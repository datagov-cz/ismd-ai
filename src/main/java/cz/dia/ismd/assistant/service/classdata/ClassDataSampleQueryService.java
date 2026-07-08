package cz.dia.ismd.assistant.service.classdata;

import cz.dia.ismd.assistant.data.classdata.SparqlResourceSample;
import cz.dia.ismd.assistant.service.sparql.SparqlQueryExecutor;
import org.eclipse.rdf4j.model.Value;
import org.eclipse.rdf4j.query.BindingSet;
import org.eclipse.rdf4j.query.TupleQueryResult;
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

    private final SparqlQueryExecutor sparqlQueryExecutor;

    public ClassDataSampleQueryService(SparqlQueryExecutor sparqlQueryExecutor) {
        this.sparqlQueryExecutor = sparqlQueryExecutor;
    }

    public Optional<SparqlResourceSample> retrieveSampleResource() {
        return sparqlQueryExecutor.query("sample resource query", connection -> {
            try (TupleQueryResult result = sparqlQueryExecutor.evaluateTupleQuery(connection, SAMPLE_RESOURCE_QUERY)) {
                if (!result.hasNext()) {
                    return Optional.empty();
                }

                BindingSet bindingSet = result.next();
                return Optional.of(new SparqlResourceSample(
                        stringValue(bindingSet.getValue("resource")),
                        stringValue(bindingSet.getValue("type"))
                ));
            }
        });
    }

    public String sampleResourceQuery() {
        return SAMPLE_RESOURCE_QUERY;
    }

    private String stringValue(Value value) {
        return value == null ? null : value.stringValue();
    }
}
