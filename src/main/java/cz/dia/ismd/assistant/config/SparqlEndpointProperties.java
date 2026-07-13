package cz.dia.ismd.assistant.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.time.Duration;

@ConfigurationProperties(prefix = "app.sparql")
public record SparqlEndpointProperties(
        URI endpointUrl,
        Duration timeout,
        int maxAttempts,
        Duration retryBackoff
) {

    public SparqlEndpointProperties {
        timeout = timeout == null ? Duration.ofSeconds(10) : timeout;
        timeout = timeout.isNegative() || timeout.isZero() ? Duration.ofSeconds(10) : timeout;
        maxAttempts = maxAttempts <= 0 ? 3 : maxAttempts;
        retryBackoff = retryBackoff == null ? Duration.ofMillis(250) : retryBackoff;
        retryBackoff = retryBackoff.isNegative() ? Duration.ofMillis(250) : retryBackoff;
    }
}
