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

class PropertySuggestionIntegrationTests extends AssistantIntegrationTest {

    @Test
    void startsAndCompletesLegalPropertySuggestionJob() throws Exception {
        MvcResult start = mockMvc.perform(post("/legal-acts/2024/1/2024-01-01/property-suggestions-top-k-extraction-jobs")
                        .with(oidcAuthentication())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "k": 1,
                                  "selected_class_id": "class_001",
                                  "structural_element_ids": ["/eli/cz/sb/2024/1/2024-01-01/par_1"],
                                  "known_conceptual_model": {
                                    "classes": [{"termID": "class_001", "name": {"cs": "Osoba"}}],
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

        mockMvc.perform(get("/legal-acts/property-suggestions-jobs")
                        .queryParam("jobIds", jobId.toString())
                        .with(oidcAuthentication()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].job_id").value(jobId.toString()))
                .andExpect(jsonPath("$[0].selected_class_id").value("class_001"))
                .andExpect(jsonPath("$[0].new_attribute_suggestions", hasSize(1)));

        mockMvc.perform(get("/legal-acts/property-suggestions-jobs")
                        .queryParam("jobIds", jobId.toString())
                        .with(oidcAuthentication()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("completed"))
                .andExpect(jsonPath("$[0].new_attribute_suggestions", hasSize(1)));
    }

    @Test
    void validatesSelectedClassForPropertyJobs() throws Exception {
        mockMvc.perform(post("/legal-acts/2024/1/2024-01-01/property-suggestions-top-k-extraction-jobs")
                        .with(oidcAuthentication())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"k": 1}
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").exists());
    }

    @Test
    void rejectsPropertyJobWhenSelectedClassIsNotInKnownConceptualModel() throws Exception {
        mockMvc.perform(post("/legal-acts/2024/1/2024-01-01/property-suggestions-top-k-extraction-jobs")
                        .with(oidcAuthentication())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "selected_class_id": "missing",
                                  "known_conceptual_model": {
                                    "classes": [{"termID": "class_001"}],
                                    "attributes": [],
                                    "relationships": []
                                  }
                                }
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("termID")));
    }
}
