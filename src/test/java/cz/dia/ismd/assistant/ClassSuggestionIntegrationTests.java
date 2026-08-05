package cz.dia.ismd.assistant;

import cz.dia.ismd.assistant.api.suggestion.classsuggestion.ClassSuggestionJobRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ClassSuggestionIntegrationTests extends AssistantIntegrationTest {

    @Test
    void startsAndCompletesLegalClassSuggestionJob() throws Exception {
        MvcResult start = mockMvc.perform(post("/legal-acts/2024/1/2024-01-01/class-suggestions-top-k-extraction-jobs")
                        .with(oidcAuthentication())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "k": 2,
                                  "structural_element_ids": ["https://e-sbirka.gov.cz/eli/cz/sb/2026/60/2026-05-27/dokument/norma/cast_1/hlava_1/par_3", "https://e-sbirka.gov.cz/eli/cz/sb/2026/60/2026-05-27/dokument/norma/cast_1/hlava_1/par_4"],
                                  "context_text": "Property rights",
                                  "known_conceptual_model": {
                                    "classes": [
                                      {
                                        "termID": "known-class-001",
                                        "name": {"cs": "český string", "en": "english string", "fr": "francois"},
                                        "definition": {"en": "Known definition"},
                                        "explanation": {"en": "Known explanation"},
                                        "type": "CLASS",
                                        "specializes": [],
                                        "legal_act": "/eli/cz/sb/2024/1"
                                      }
                                    ],
                                    "attributes": [],
                                    "relationships": []
                                  }
                                }
                                """))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("in_progress"))
                .andReturn();

        UUID jobId = UUID.fromString(Json.read(start, "job_id"));
        waitForAsyncJob(jobId);
        verify(classSuggestionLlmService).suggestClasses(
                eq("test-user"), any(ClassSuggestionJobRequest.class), any());

        mockMvc.perform(get("/legal-acts/class-suggestions-jobs")
                        .queryParam("jobIds", jobId.toString())
                        .with(oidcAuthentication()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].job_id").value(jobId.toString()))
                .andExpect(jsonPath("$[0].status").value("completed"))
                .andExpect(jsonPath("$[0].new_suggestions", hasSize(2)))
                .andExpect(jsonPath("$[0].new_suggestions[0].name.cs").value("Property Actor"))
                .andExpect(jsonPath("$[0].new_suggestions[0].legal_act").value("/eli/cz/sb/2024/1"));

        mockMvc.perform(get("/legal-acts/class-suggestions-jobs")
                        .queryParam("jobIds", jobId.toString())
                        .with(oidcAuthentication()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("completed"))
                .andExpect(jsonPath("$[0].new_suggestions", hasSize(2)))
                .andExpect(jsonPath("$[0].new_suggestions[0].name.cs").value("Property Actor"));
    }

    @Test
    void validatesStructuralElementIdsUseEliUriFormat() throws Exception {
        mockMvc.perform(post("/legal-acts/2024/1/2024-01-01/class-suggestions-top-k-extraction-jobs")
                        .with(oidcAuthentication())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "k": 1,
                                  "structural_element_ids": ["§1"]
                                }
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").exists());
    }
}
