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

class SuggestionJobQueryIntegrationTests extends AssistantIntegrationTest {

    @Test
    void returnsSuggestionJobsForMultipleJobIds() throws Exception {
        MvcResult firstClassStart = mockMvc.perform(post("/legal-acts/2024/1/2024-01-01/class-suggestions-top-k-extraction-jobs")
                        .with(oidcAuthentication())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "k": 1,
                                  "structural_element_ids": [
        "https://e-sbirka.gov.cz/eli/cz/sb/2026/60/2026-05-27/dokument/norma/cast_1/hlava_1/par_3"
      ]
                                }
                                """))
                .andExpect(status().isAccepted())
                .andReturn();
        MvcResult secondClassStart = mockMvc.perform(post("/legal-acts/2024/1/2024-01-01/class-suggestions-top-k-extraction-jobs")
                        .with(oidcAuthentication())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "k": 1,
                                  "structural_element_ids": [
        "https://e-sbirka.gov.cz/eli/cz/sb/2026/60/2026-05-27/dokument/norma/cast_1/hlava_1/par_3"
      ]
                                }
                                """))
                .andExpect(status().isAccepted())
                .andReturn();
        MvcResult propertyStart = mockMvc.perform(post("/legal-acts/2024/1/2024-01-01/property-suggestions-top-k-extraction-jobs")
                        .with(oidcAuthentication())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "k": 1,
                                  "selected_class_id": "class_001",
                                  "structural_element_ids": [
                                    "https://e-sbirka.gov.cz/eli/cz/sb/2026/60/2026-05-27/dokument/norma/cast_1/hlava_1/par_3"
                                  ],
                                  "known_conceptual_model": {
                                    "classes": [{"termID": "class_001"}],
                                    "attributes": [],
                                    "relationships": []
                                  }
                                }
                                """))
                .andExpect(status().isAccepted())
                .andReturn();
        MvcResult relationshipStart = mockMvc.perform(post("/legal-acts/2024/1/2024-01-01/relationship-suggestions-top-k-extraction-jobs")
                        .with(oidcAuthentication())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "k": 1,
                                  "selected_class_id": "class_001",
                                  "structural_element_ids": [
                                    "https://e-sbirka.gov.cz/eli/cz/sb/2026/60/2026-05-27/dokument/norma/cast_1/hlava_1/par_3"
                                  ],
                                  "known_conceptual_model": {
                                    "classes": [{"termID": "class_001"}],
                                    "attributes": [],
                                    "relationships": []
                                  }
                                }
                                """))
                .andExpect(status().isAccepted())
                .andReturn();

        UUID firstClassJobId = UUID.fromString(Json.read(firstClassStart, "job_id"));
        UUID secondClassJobId = UUID.fromString(Json.read(secondClassStart, "job_id"));
        UUID propertyJobId = UUID.fromString(Json.read(propertyStart, "job_id"));
        UUID relationshipJobId = UUID.fromString(Json.read(relationshipStart, "job_id"));
        waitForAsyncJob(firstClassJobId, secondClassJobId, propertyJobId, relationshipJobId);

        mockMvc.perform(get("/legal-acts/class-suggestions-jobs")
                        .queryParam("jobIds", firstClassJobId.toString(), secondClassJobId.toString())
                        .with(oidcAuthentication()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].job_id").value(firstClassJobId.toString()))
                .andExpect(jsonPath("$[1].job_id").value(secondClassJobId.toString()))
                .andExpect(jsonPath("$[0].new_suggestions", hasSize(1)))
                .andExpect(jsonPath("$[1].new_suggestions", hasSize(1)));

        mockMvc.perform(get("/legal-acts/property-suggestions-jobs")
                        .queryParam("jobIds", propertyJobId.toString())
                        .with(oidcAuthentication()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].job_id").value(propertyJobId.toString()))
                .andExpect(jsonPath("$[0].selected_class_id").value("class_001"))
                .andExpect(jsonPath("$[0].new_attribute_suggestions", hasSize(1)));

        mockMvc.perform(get("/legal-acts/relationship-suggestions-jobs")
                        .queryParam("jobIds", relationshipJobId.toString())
                        .with(oidcAuthentication()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].job_id").value(relationshipJobId.toString()))
                .andExpect(jsonPath("$[0].selected_class_id").value("class_001"))
                .andExpect(jsonPath("$[0].new_relationship_suggestions", hasSize(1)));
    }
}
