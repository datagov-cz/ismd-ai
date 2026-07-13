package cz.dia.ismd.assistant.service.sparql;

import cz.dia.ismd.assistant.records.sparql.SparqlEndpointProperties;
import jakarta.annotation.PreDestroy;
import org.eclipse.rdf4j.query.QueryInterruptedException;
import org.eclipse.rdf4j.query.TupleQuery;
import org.eclipse.rdf4j.query.TupleQueryResult;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.eclipse.rdf4j.repository.sparql.SPARQLRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import javax.net.ssl.SSLException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

@Service
public class SparqlQueryExecutor {

    private final SPARQLRepository repository;
    private final URI endpointUrl;
    private final Duration timeout;
    private final int maxAttempts;
    private final Duration retryBackoff;
    private final int queryTimeoutSeconds;

    public SparqlQueryExecutor(SparqlEndpointProperties properties) {
        this.endpointUrl = properties.endpointUrl();
        this.timeout = properties.timeout();
        this.maxAttempts = properties.maxAttempts();
        this.retryBackoff = properties.retryBackoff();
        this.queryTimeoutSeconds = Math.max(1, Math.toIntExact(Math.min(Integer.MAX_VALUE, timeout.toSeconds())));
        this.repository = new SPARQLRepository(properties.endpointUrl().toString());
        this.repository.init();
    }

    public <T> T query(String operation, Function<RepositoryConnection, T> queryOperation) {
        return execute(operation, () -> {
            try (RepositoryConnection connection = repository.getConnection()) {
                return queryOperation.apply(connection);
            }
        });
    }

    public <T> T execute(String operation, Supplier<T> sparqlOperation) {
        RuntimeException lastFailure = null;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return sparqlOperation.get();
            } catch (RuntimeException exception) {
                lastFailure = exception;
                if (attempt == maxAttempts || !isRetryable(exception)) {
                    throw sparqlAccessException(operation, exception, attempt);
                }
                waitBeforeRetry(operation, attempt, exception);
            }
        }

        throw sparqlAccessException(operation, lastFailure, maxAttempts);
    }

    public TupleQueryResult evaluateTupleQuery(RepositoryConnection connection, String sparqlQuery) {
        return evaluateTupleQuery(connection, sparqlQuery, query -> {
        });
    }

    public TupleQueryResult evaluateTupleQuery(
            RepositoryConnection connection,
            String sparqlQuery,
            Consumer<TupleQuery> queryCustomizer
    ) {
        TupleQuery query = connection.prepareTupleQuery(sparqlQuery);
        query.setMaxExecutionTime(queryTimeoutSeconds);
        queryCustomizer.accept(query);
        return query.evaluate();
    }

    @PreDestroy
    public void close() {
        repository.shutDown();
    }

    private void waitBeforeRetry(String operation, int attempt, RuntimeException exception) {
        try {
            Thread.sleep(retryBackoff.toMillis());
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            throw new SparqlAccessException(
                    "SPARQL %s was interrupted before retry %d against %s after %s. Previous error: %s"
                            .formatted(operation, attempt + 1, endpointUrl, timeout, userFacingCause(exception)),
                    interruptedException
            );
        }
    }

    private SparqlAccessException sparqlAccessException(String operation, RuntimeException exception, int attemptsMade) {
        String cause = exception == null ? "unknown error" : userFacingCause(exception);
        return new SparqlAccessException(
                "Unable to complete SPARQL %s against endpoint %s after %d attempt%s. Timeout: %s. Cause: %s"
                        .formatted(operation, endpointUrl, attemptsMade, attemptsMade == 1 ? "" : "s", timeout, cause),
                exception
        );
    }

    private boolean isRetryable(Throwable exception) {
        Optional<RestClientResponseException> responseException = findCause(exception, RestClientResponseException.class);
        if (responseException.isPresent()) {
            int status = responseException.orElseThrow().getStatusCode().value();
            return status == 429 || status >= 500;
        }
        if (findCause(exception, UnknownHostException.class).isPresent()) {
            return false;
        }
        if (findCause(exception, SSLException.class).isPresent()) {
            return false;
        }
        return findCause(exception, SocketTimeoutException.class).isPresent()
                || findCause(exception, ConnectException.class).isPresent()
                || findCause(exception, QueryInterruptedException.class).isPresent()
                || findCause(exception, ResourceAccessException.class).isPresent()
                || findCause(exception, RestClientException.class).isPresent();
    }

    private String userFacingCause(Throwable exception) {
        Optional<RestClientResponseException> responseException = findCause(exception, RestClientResponseException.class);
        if (responseException.isPresent()) {
            return userFacingHttpStatus(responseException.orElseThrow().getStatusCode().value());
        }
        if (findCause(exception, UnknownHostException.class).isPresent()) {
            return "host name could not be resolved";
        }
        if (findCause(exception, ConnectException.class).isPresent()) {
            return "connection to the endpoint could not be established";
        }
        if (findCause(exception, SocketTimeoutException.class).isPresent()
                || findCause(exception, QueryInterruptedException.class).isPresent()) {
            return "endpoint did not answer within the configured timeout";
        }
        if (findCause(exception, SSLException.class).isPresent()) {
            return "TLS/SSL handshake failed";
        }

        String detail = exception.getMessage();
        if (detail == null || detail.isBlank()) {
            detail = exception.getClass().getSimpleName();
        }
        return userFacingMessage(detail);
    }

    private String userFacingHttpStatus(int status) {
        if (status == 400) {
            return "endpoint rejected the SPARQL query as invalid";
        }
        if (status == 401 || status == 403) {
            return "endpoint rejected the request; check credentials and access rights";
        }
        if (status == 404) {
            return "endpoint URL was not found";
        }
        if (status == 429) {
            return "endpoint rate limit was reached";
        }
        if (status >= 500) {
            return "endpoint returned a temporary server error";
        }
        return "endpoint returned HTTP status " + status;
    }

    private String userFacingMessage(String detail) {
        if (detail.contains("401") || detail.contains("403")) {
            return "endpoint rejected the request; check credentials and access rights";
        }
        if (detail.contains("404")) {
            return "endpoint URL was not found";
        }
        if (detail.contains("400")) {
            return "endpoint rejected the SPARQL query as invalid";
        }
        if (detail.contains("429")) {
            return "endpoint rate limit was reached";
        }
        if (detail.contains("500") || detail.contains("502") || detail.contains("503") || detail.contains("504")) {
            return "endpoint returned a temporary server error";
        }
        return detail;
    }

    private <T extends Throwable> Optional<T> findCause(Throwable exception, Class<T> expectedType) {
        Throwable current = exception;
        while (current != null) {
            if (expectedType.isInstance(current)) {
                return Optional.of(expectedType.cast(current));
            }
            current = current.getCause();
        }
        return Optional.empty();
    }
}
