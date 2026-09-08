package cz.dia.ismd.assistant;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import cz.dia.ismd.assistant.api.suggestion.relationship.RelationshipSuggestionJobRequest;
import cz.dia.ismd.assistant.model.job.JobKind;
import cz.dia.ismd.assistant.model.job.JobStatus;
import cz.dia.ismd.assistant.model.job.SuggestionJob;
import cz.dia.ismd.assistant.model.suggestion.*;
import cz.dia.ismd.assistant.model.suggestion.attribute.AttributeSuggestion;
import cz.dia.ismd.assistant.api.suggestion.attribute.PropertySuggestionJobRequest;
import cz.dia.ismd.assistant.model.suggestion.classsuggestion.ClassSuggestion;
import cz.dia.ismd.assistant.model.suggestion.relationship.RelationshipSuggestion;
import cz.dia.ismd.assistant.service.SuggestionJobRepository;
import cz.dia.ismd.assistant.service.SuggestionJobService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class VocabularySuggestionIntegrationTests extends AssistantIntegrationTest {
    private static final String POST = "/legal-acts/2024/1/2024-01-01/vocabulary-suggestions-jobs";
    private static final String GET = "/legal-acts/vocabulary-suggestions-jobs";
    private static final String SOURCE = "2024/1/2024-01-01";
    @Autowired private SuggestionJobRepository repository;
    @Autowired private SuggestionJobService jobs;
    @Autowired private ObjectMapper mapper;

    private static final String WORKING_MODEL = """
            {"classes":[{"termID":"draft-car","name":{"cs":"Vozidlo upravené uživatelem"},"type":"CLASS","specializes":[]},
                        {"termID":"draft-driver","name":{"cs":"Řidič"},"type":"CLASS","specializes":[]}],
             "attributes":[{"termID":"draft-volume","name":{"cs":"Objem válců"},"associated_class":{"id":"draft-car"}}],
             "relationships":[]}
            """;

    @Test
    void expandsUsingCallerSnapshotAndPollsOnlyNewItemsThroughSharedGet() throws Exception {
        when(propertySuggestionLlmService.suggestProperties(anyString(), any(), any())).thenAnswer(inv -> {
            var request = inv.getArgument(1, PropertySuggestionJobRequest.class);
            assertThat(request.knownConceptualModel().classes().get(0).name()).isEqualTo(LangString.cs("Vozidlo upravené uživatelem"));
            assertThat(request.knownConceptualModel().attributes()).hasSize(1);
            return List.of(new AttributeSuggestion("provider-id", new IdReference("draft-car"), LangString.cs("Výkon"), null, null, SOURCE));
        });
        UUID id = startEdit("expand", "{\"kind\":\"properties\",\"count\":1,\"selected_class_id\":\"draft-car\",\"known_conceptual_model\":" + WORKING_MODEL + "}");
        awaitTerminal(id);
        JsonNode draft = result(id).path("draft");
        assertThat(result(id).path("status").asText()).isEqualTo("completed");
        assertThat(draft.path("classes")).isEmpty(); assertThat(draft.path("relationships")).isEmpty();
        assertThat(draft.path("attributes")).hasSize(1);
        assertThat(draft.at("/attributes/0/associated_class/ref").asText()).isEqualTo("draft-car");
        assertThat(repository.findById(id).orElseThrow().vocabularyDraft().attributes().get(0).ref()).startsWith("attribute-");
        verifyNoInteractions(classSuggestionLlmService, relationshipSuggestionLlmService);
    }

    @Test
    void regeneratesExactlyOneReplacementKeepingItsRefAndDomain() throws Exception {
        when(propertySuggestionLlmService.regenerate(anyString(), any(), any(), any())).thenReturn(List.of(
                new cz.dia.ismd.assistant.service.ConceptRegenerationLlmService.Metadata(LangString.cs("Zdvihový objem"), null, null, SOURCE)));
        UUID id = startEdit("regenerate", "{\"concept_ref\":\"draft-volume\",\"known_conceptual_model\":" + WORKING_MODEL + "}");
        awaitTerminal(id);
        JsonNode draft = result(id).path("draft");
        assertThat(result(id).path("status").asText()).isEqualTo("completed");
        assertThat(draft.path("attributes")).hasSize(1);
        assertThat(draft.at("/attributes/0/ref").asText()).isEqualTo("draft-volume");
        assertThat(draft.at("/attributes/0/associated_class/ref").asText()).isEqualTo("draft-car");
        assertThat(draft.at("/attributes/0/name/cs").asText()).isEqualTo("Zdvihový objem");
        assertThat(repository.findById(id).orElseThrow().vocabularyDraft().attributes().get(0).ref()).isEqualTo("draft-volume");
        verify(propertySuggestionLlmService, never()).suggestProperties(anyString(), any(), any());
        verifyNoInteractions(classSuggestionLlmService, relationshipSuggestionLlmService);
    }

    @Test
    void editsRejectUnauthenticatedInvalidOrInconsistentRequestsBeforeCreatingJobs() throws Exception {
        mockMvc.perform(post(POST + "/expand").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post(POST + "/regenerate").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        for (String body : List.of("{}", "{\"kind\":\"properties\",\"known_conceptual_model\":" + WORKING_MODEL + "}",
                "{\"kind\":\"classes\",\"count\":11,\"known_conceptual_model\":" + WORKING_MODEL + "}")) {
            mockMvc.perform(post(POST + "/expand").with(oidcAuthentication()).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isUnprocessableEntity());
        }
        mockMvc.perform(post(POST + "/regenerate").with(oidcAuthentication()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"concept_ref\":\"missing\",\"known_conceptual_model\":" + WORKING_MODEL + "}"))
                .andExpect(status().isUnprocessableEntity());
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM suggestion_jobs", Integer.class)).isZero();
        verifyNoInteractions(classSuggestionLlmService, propertySuggestionLlmService, relationshipSuggestionLlmService);
    }

    private UUID startEdit(String operation, String body) throws Exception {
        String json = mockMvc.perform(post(POST + "/" + operation).with(oidcAuthentication())
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        return UUID.fromString(mapper.readTree(json).path("job_id").asText());
    }

    @Test
    void startsOneJobPersistsLinkedDraftAndAcceptsFeedbackForEveryTermType() throws Exception {
        configureLinkedSuggestions();
        UUID jobId = start("{\"class_count\":2,\"properties_per_class\":1,\"relationships_per_class\":1}");
        waitForAsyncJob(jobId);
        JsonNode response = result(jobId);
        assertThat(response.path("status").asText()).isEqualTo("completed");
        JsonNode draft = response.path("draft");
        assertThat(draft.path("phase").asText()).isEqualTo("DONE");
        assertThat(draft.path("classes")).hasSize(2);
        assertThat(draft.path("attributes")).hasSize(2);
        assertThat(draft.path("relationships")).hasSize(2);
        String ref = draft.path("classes").get(0).path("ref").asText();
        assertThat(ref).startsWith("class-");
        assertThat(draft.path("attributes").get(0).path("associated_class").path("ref").asText()).isEqualTo(ref);
        assertThat(draft.path("attributes").get(0).path("associated_class").has("iri")).isFalse();
        assertThat(draft.path("relationships").get(0).path("target_class").path("ref").asText())
                .isEqualTo(draft.path("classes").get(1).path("ref").asText());
        assertThat(draft.path("classes").get(0).has("iri")).isFalse();

        SuggestionJob restored = repository.findById(jobId).orElseThrow();
        assertThat(restored.kind()).isEqualTo(JobKind.VOCABULARY);
        assertThat(restored.vocabularyDraft().classes().get(0).ref()).isEqualTo(ref);
        assertThat(result(jobId)).isEqualTo(response); // Polling and repository round trips preserve refs.
        verify(legalActSPARQLService, times(1)).retrieveLegalActTexts(List.of("https://e-sbirka.gov.cz/eli/cz/sb/" + SOURCE));
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM suggestion_jobs WHERE job_id = ?", Integer.class, jobId))
                .isEqualTo(1);
        String feedback = mapper.writeValueAsString(List.of(java.util.Map.of("jobID", jobId,
                "suggestionID", List.of(ref, draft.path("attributes").get(0).path("ref").asText(),
                        draft.path("relationships").get(0).path("ref").asText()))));
        mockMvc.perform(post("/accept-suggestion").with(oidcAuthentication())
                        .contentType(MediaType.APPLICATION_JSON).content(feedback))
                .andExpect(status().isNoContent());
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM feedback_records WHERE job_id = ?", Integer.class, jobId))
                .isEqualTo(3);
    }

    @Test
    void failedJobKeepsValidatedClassesAndPropertiesInDatabase() throws Exception {
        configureLinkedSuggestions();
        doThrow(new IllegalStateException("Provider unavailable"))
                .when(relationshipSuggestionLlmService).suggestRelationships(anyString(), any(), any());
        UUID id = start("{\"class_count\":2,\"properties_per_class\":1,\"relationships_per_class\":1}");
        awaitTerminal(id);
        SuggestionJob persisted = repository.findById(id).orElseThrow();
        assertThat(persisted.status()).isEqualTo(JobStatus.FAILED);
        assertThat(persisted.vocabularyDraft().classes()).hasSize(2);
        assertThat(persisted.vocabularyDraft().attributes()).hasSize(2);
        assertThat(persisted.vocabularyDraft().relationships()).isEmpty();
        assertThat(result(id).path("status").asText()).isEqualTo("failed");
        repository.failInterruptedJobs();
        assertThat(repository.findById(id).orElseThrow().vocabularyDraft()).isEqualTo(persisted.vocabularyDraft());
    }

    @Test
    void interruptedVocabularyJobRetainsItsSnapshotAfterStartupRecovery() {
        var interrupted = new SuggestionJob(UUID.randomUUID(), JobKind.VOCABULARY, null);
        var draft = new cz.dia.ismd.assistant.model.suggestion.vocabulary.VocabularyDraft(
                cz.dia.ismd.assistant.model.suggestion.vocabulary.VocabularyDraft.Phase.PROPERTIES,
                List.of(new cz.dia.ismd.assistant.model.suggestion.vocabulary.VocabularyDraft.DraftClass(
                        "class-stable", LangString.cs("Vozidlo"), null, null, TermType.CLASS, List.of(), SOURCE)),
                List.of(), List.of());
        interrupted.updateVocabularyDraft(draft);
        repository.insert(interrupted);
        repository.failInterruptedJobs();
        var restored = repository.findById(interrupted.jobId()).orElseThrow();
        assertThat(restored.status()).isEqualTo(JobStatus.FAILED);
        assertThat(restored.vocabularyDraft()).isEqualTo(draft);
    }

    @Test
    void validatesCountsElementsKnownIdsAndLegalActVersionBeforeStarting() throws Exception {
        for (String body : List.of("{\"class_count\":0}", "{\"properties_per_class\":11}",
                "{\"relationships_per_class\":-1}", "{\"structural_element_ids\":[\"§1\"]}",
                "{\"structural_element_ids\":[null]}",
                "{\"structural_element_ids\":[\"/eli/cz/sb/2025/1/2025-01-01\"]}",
                "{\"known_conceptual_model\":{\"classes\":[{\"termID\":\"same\"},{\"termID\":\"same\"}]}}")) {
            mockMvc.perform(post(POST).with(oidcAuthentication()).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isUnprocessableEntity());
        }
        verifyNoInteractions(classSuggestionLlmService, propertySuggestionLlmService, relationshipSuggestionLlmService);
    }

    @Test
    void requiresAuthenticationForStartAndPolling() throws Exception {
        mockMvc.perform(post(POST).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get(GET).queryParam("jobIds", UUID.randomUUID().toString()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsOtherJobKindsAndUnknownIds() throws Exception {
        SuggestionJob classJob = new SuggestionJob(UUID.randomUUID(), JobKind.CLASS, null);
        repository.insert(classJob);
        for (UUID id : List.of(classJob.jobId(), UUID.randomUUID())) {
            mockMvc.perform(get(GET).with(oidcAuthentication()).queryParam("jobIds", id.toString()))
                    .andExpect(status().isNotFound());
        }
    }

    @Test
    void schemaUpgradePreservesLegacyJobsAndAllowsVocabularyJobs() throws Exception {
        String schema = "migration_" + UUID.randomUUID().toString().replace("-", "");
        var resource = new org.springframework.core.io.ClassPathResource("schema.sql");
        String script;
        try (var stream = resource.getInputStream()) {
            script = new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
        String legacyTable = script.substring(0, script.indexOf(");") + 2).replace(", 'VOCABULARY'", "");
        try (var connection = jdbcTemplate.getDataSource().getConnection(); var statement = connection.createStatement()) {
            statement.execute("CREATE SCHEMA " + schema);
            try {
                statement.execute("SET search_path TO " + schema);
                statement.execute(legacyTable);
                statement.execute("INSERT INTO suggestion_jobs(job_id, job_kind, status, created_at) VALUES "
                        + "('00000000-0000-0000-0000-000000000001', 'CLASS', 'COMPLETED', now())");
                org.springframework.jdbc.datasource.init.ScriptUtils.executeSqlScript(connection, resource);
                org.springframework.jdbc.datasource.init.ScriptUtils.executeSqlScript(connection, resource);
                statement.execute("INSERT INTO suggestion_jobs(job_id, job_kind, status, created_at) VALUES "
                        + "('00000000-0000-0000-0000-000000000002', 'VOCABULARY', 'IN_PROGRESS', now())");
                try (var rows = statement.executeQuery("SELECT count(*) FROM suggestion_jobs")) {
                    rows.next();
                    assertThat(rows.getInt(1)).isEqualTo(2);
                }
            } finally {
                statement.execute("SET search_path TO public");
                statement.execute("DROP SCHEMA " + schema + " CASCADE");
            }
        }
    }

    @Test
    void openApiDescribesBothOperationsAndReferenceFields() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/legal-acts/{year}/{number}/{date}/vocabulary-suggestions-jobs'].post.responses['202']").exists())
                .andExpect(jsonPath("$.paths['/legal-acts/vocabulary-suggestions-jobs'].get").exists())
                .andExpect(jsonPath("$.components.schemas.ConceptReference.properties.ref").exists())
                .andExpect(jsonPath("$.components.schemas.ConceptReference.properties.iri").exists());
    }

    private void configureLinkedSuggestions() {
        when(classSuggestionLlmService.suggestClasses(anyString(), any(), any())).thenReturn(List.of(
                new ClassSuggestion("a", LangString.cs("Vozidlo"), null, null, TermType.CLASS, List.of(), SOURCE),
                new ClassSuggestion("b", LangString.cs("Osoba"), null, null, TermType.CLASS, List.of(), SOURCE)));
        when(propertySuggestionLlmService.suggestProperties(anyString(), any(), any())).thenAnswer(invocation -> {
            PropertySuggestionJobRequest request = invocation.getArgument(1);
            return List.of(new AttributeSuggestion("p", new IdReference(request.selectedClassId()),
                    LangString.cs("Název"), null, null, SOURCE));
        });
        when(relationshipSuggestionLlmService.suggestRelationships(anyString(), any(), any())).thenAnswer(invocation -> {
            RelationshipSuggestionJobRequest request = invocation.getArgument(1);
            return List.of(new RelationshipSuggestion("r", new IdReference(request.selectedClassId()),
                    new IdReference(request.knownConceptualModel().classes().get(1).termID()),
                    LangString.cs("Má vlastníka"), null, null, SOURCE));
        });
    }

    private UUID start(String body) throws Exception {
        var response = mockMvc.perform(post(POST).with(oidcAuthentication()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isAccepted()).andExpect(jsonPath("$.status").value("in_progress")).andReturn();
        return UUID.fromString(Json.read(response, "job_id"));
    }

    private JsonNode result(UUID id) throws Exception {
        var response = mockMvc.perform(get(GET).with(oidcAuthentication()).queryParam("jobIds", id.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1))).andReturn();
        return mapper.readTree(response.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8)).get(0);
    }

    private void awaitTerminal(UUID id) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            if (jobs.get(id).status() != JobStatus.IN_PROGRESS) return;
            Thread.sleep(10);
        }
        throw new AssertionError("Job did not finish");
    }
}
