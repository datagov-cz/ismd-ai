package cz.dia.ismd.assistant.service.classdata;

import cz.dia.ismd.assistant.data.classdata.SparqlResourceSample;
import cz.dia.ismd.assistant.records.sparql.SparqlEndpointProperties;
import jakarta.annotation.PreDestroy;
import org.eclipse.rdf4j.model.Value;
import org.eclipse.rdf4j.query.BindingSet;
import org.eclipse.rdf4j.query.TupleQueryResult;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.eclipse.rdf4j.repository.sparql.SPARQLRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class ClassDataSampleQueryService {

    private static final String SAMPLE_RESOURCE_QUERY = """

            SELECT ?resource ?type WHERE {
              ?resource a ?type .
            }
            LIMIT 1
            """;

    private final SPARQLRepository repository;

    public ClassDataSampleQueryService(SparqlEndpointProperties properties) {
        this.repository = new SPARQLRepository(properties.endpointUrl().toString());
        this.repository.init();
    }

    public Optional<SparqlResourceSample> retrieveSampleResource() {
        try (RepositoryConnection connection = repository.getConnection();
             TupleQueryResult result = connection.prepareTupleQuery(SAMPLE_RESOURCE_QUERY).evaluate()) {
            if (!result.hasNext()) {
                return Optional.empty();
            }

            BindingSet bindingSet = result.next();
            return Optional.of(new SparqlResourceSample(
                    stringValue(bindingSet.getValue("resource")),
                    stringValue(bindingSet.getValue("type"))
            ));
        }
    }

    public String sampleResourceQuery() {
        return SAMPLE_RESOURCE_QUERY;
    }

    @PreDestroy
    public void close() {
        repository.shutDown();
    }

    private String stringValue(Value value) {
        return value == null ? null : value.stringValue();
    }
}
