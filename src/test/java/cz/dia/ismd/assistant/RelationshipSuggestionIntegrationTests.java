package cz.dia.ismd.assistant;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RelationshipSuggestionIntegrationTests extends AssistantIntegrationTest {

    @Test
    void startsAndCompletesLegalRelationshipSuggestionJob() throws Exception {
        MvcResult start = mockMvc.perform(post("/legal-acts/2024/1/2024-01-01/relationship-suggestions-top-k-extraction-jobs")
                        .with(oidcAuthentication())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "k": 2,
                                  "selected_class_id": "class_001",
                                  "structural_element_ids": ["/eli/cz/sb/2024/1/section/1", "/eli/cz/sb/2024/1/section/2"],
                                  "context_text": "Property rights",
                                  "known_conceptual_model": {
                                    "classes": [
                                      {"termID": "class_001", "name": {"cs": "Osoba"}},
                                      {"termID": "class_002", "name": {"cs": "Věc"}}
                                    ],
                                    "attributes": [],
                                    "relationships": []
                                  }
                                }
                                """))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.selected_class_id").doesNotExist())
                .andExpect(jsonPath("$.status").value("in_progress"))
                .andReturn();

        UUID jobId = UUID.fromString(Json.read(start, "job_id"));
        waitForAsyncJob(jobId);

        mockMvc.perform(get("/legal-acts/relationship-suggestions-jobs")
                        .queryParam("jobIds", jobId.toString())
                        .with(oidcAuthentication()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].job_id").value(jobId.toString()))
                .andExpect(jsonPath("$[0].selected_class_id").value("class_001"))
                .andExpect(jsonPath("$[0].status").value("completed"))
                .andExpect(jsonPath("$[0].new_relationship_suggestions", hasSize(2)))
                .andExpect(jsonPath("$[0].new_relationship_suggestions[0].legal_act").value("/eli/cz/sb/2024/1"));
    }

    @Test
    void rejectsRelationshipJobWithEmptyKnownConceptualModel() throws Exception {
        mockMvc.perform(post("/legal-acts/2024/1/2024-01-01/relationship-suggestions-top-k-extraction-jobs")
                        .with(oidcAuthentication())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "selected_class_id": "class_001",
                                  "known_conceptual_model": {
                                    "classes": [],
                                    "attributes": [],
                                    "relationships": []
                                  }
                                }
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("non-empty")));
    }
}
