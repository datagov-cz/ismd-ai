package cz.dia.ismd.assistant.main;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
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
                                  "structural_element_ids": ["/eli/cz/sb/2024/1/section/1", "/eli/cz/sb/2024/1/section/2"],
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
        waitForAsyncJob();

        mockMvc.perform(get("/legal-acts/class-suggestions-jobs")
                        .queryParam("jobIds", jobId.toString())
                        .with(oidcAuthentication()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].job_id").value(jobId.toString()))
                .andExpect(jsonPath("$[0].status").value("completed"))
                .andExpect(jsonPath("$[0].new_suggestions", hasSize(2)))
                .andExpect(jsonPath("$[0].new_suggestions[0].name.en").value("Property Actor"))
                .andExpect(jsonPath("$[0].new_suggestions[0].legal_act").value("/eli/cz/sb/2024/1"));
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
