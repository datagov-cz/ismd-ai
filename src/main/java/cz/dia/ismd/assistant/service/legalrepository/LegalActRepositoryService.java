package cz.dia.ismd.assistant.service.legalrepository;

import com.fasterxml.jackson.databind.JsonNode;
import cz.dia.ismd.assistant.records.legalrepository.LegalActRepositoryRecord;
import cz.dia.ismd.assistant.service.sparql.SparqlQueryExecutor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class LegalActRepositoryService {

    private static final MediaType SPARQL_RESULTS_JSON = MediaType.valueOf("application/sparql-results+json");
    private static final String LEGAL_ACT_QUERY = """
            select ?akt where {?akt a <https://slovník.gov.cz/datový/sbírka/pojem/právní-akt>}
            """;

    private final RestClient sparqlRestClient;
    private final SparqlQueryExecutor sparqlQueryExecutor;
    private final AtomicReference<LegalActRepositoryRecord> currentRecord = new AtomicReference<>(
            new LegalActRepositoryRecord(null, LEGAL_ACT_QUERY, List.of())
    );

    public LegalActRepositoryService(RestClient sparqlRestClient, SparqlQueryExecutor sparqlQueryExecutor) {
        this.sparqlRestClient = sparqlRestClient;
        this.sparqlQueryExecutor = sparqlQueryExecutor;
    }

    public LegalActRepositoryRecord importLegalActs() {
        LegalActRepositoryRecord record = new LegalActRepositoryRecord(
                Instant.now(),
                LEGAL_ACT_QUERY,
                extractAktValues(fetchJson(LEGAL_ACT_QUERY))
        );
        currentRecord.set(record);
        return record;
    }

    public LegalActRepositoryRecord currentRecord() {
        return currentRecord.get();
    }

    public JsonNode fetchJson(String sparqlQuery) {
        return sparqlQueryExecutor.execute("legal act repository query", () -> {
            MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
            formData.add("query", sparqlQuery);

            return sparqlRestClient.post()
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .accept(SPARQL_RESULTS_JSON, MediaType.APPLICATION_JSON)
                    .body(formData)
                    .retrieve()
                    .body(JsonNode.class);
        });
    }

    private List<String> extractAktValues(JsonNode response) {
        JsonNode bindings = response == null ? null : response.at("/results/bindings");
        if (bindings == null || !bindings.isArray()) {
            return List.of();
        }

        List<String> values = new ArrayList<>();
        for (JsonNode binding : bindings) {
            JsonNode value = binding.at("/akt/value");
            if (value.isTextual() && !value.asText().isBlank()) {
                values.add(value.asText());
            }
        }
        return values;
    }
}
