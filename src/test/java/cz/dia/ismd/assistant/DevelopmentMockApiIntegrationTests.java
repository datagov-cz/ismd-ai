package cz.dia.ismd.assistant;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@TestPropertySource(properties = "app.environment=development")
class DevelopmentMockApiIntegrationTests extends AssistantIntegrationTest {

    @Test
    void returnsLinkedVocabularyMockWithoutAuthentication() throws Exception {
        mockMvc.perform(post("/legal-acts/2024/1/2024-01-01/vocabulary-suggestions-jobs")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.job_id").value("00000000-0000-0000-0000-000000000401"));
        mockMvc.perform(get("/legal-acts/vocabulary-suggestions-jobs")
                        .queryParam("jobIds", "00000000-0000-0000-0000-000000000401"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("completed"))
                .andExpect(jsonPath("$[0].draft.classes", hasSize(2)))
                .andExpect(jsonPath("$[0].draft.attributes[0].associated_class.ref").value("mock-class-vehicle"))
                .andExpect(jsonPath("$[0].draft.relationships[0].target_class.ref").value("mock-class-person"));
    }

    @Test
    void allowsRequestsWithoutOidcAuthenticationInDevelopment() throws Exception {
        mockMvc.perform(post("/legal-acts/2024/1/2024-01-01/class-suggestions-top-k-extraction-jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "k": 1,
                                  "context_text": "This input is ignored in development"
                                }
                                """))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.job_id").value("00000000-0000-0000-0000-000000000101"))
                .andExpect(jsonPath("$.status").value("in_progress"));
    }

    @Test
    void returnsPredeterminedClassSuggestionResponses() throws Exception {
        mockMvc.perform(post("/legal-acts/2024/1/2024-01-01/class-suggestions-top-k-extraction-jobs")
                        .with(oidcAuthentication())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "k": 1,
                                  "context_text": "This input is ignored in development"
                                }
                                """))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.job_id").value("00000000-0000-0000-0000-000000000101"))
                .andExpect(jsonPath("$.status").value("in_progress"));

        mockMvc.perform(get("/legal-acts/class-suggestions-jobs")
                        .queryParam("jobIds", "11111111-1111-1111-1111-111111111111")
                        .with(oidcAuthentication()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].job_id").value("00000000-0000-0000-0000-000000000101"))
                .andExpect(jsonPath("$[0].status").value("completed"))
                .andExpect(jsonPath("$[0].new_suggestions[0].suggestion_id").value("mock-class-suggestion-001"));
    }

    @Test
    void returnsPredeterminedPropertySuggestionResponses() throws Exception {
        mockMvc.perform(post("/legal-acts/2024/1/2024-01-01/property-suggestions-top-k-extraction-jobs")
                        .with(oidcAuthentication())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "k": 2,
                                  "selected_class_id": "request-class-that-is-ignored",
                                  "known_conceptual_model": {
                                    "classes": [{"termID": "request-class-that-is-ignored"}],
                                    "attributes": [],
                                    "relationships": []
                                  }
                                }
                                """))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.job_id").value("00000000-0000-0000-0000-000000000201"))
                .andExpect(jsonPath("$.selected_class_id").doesNotExist())
                .andExpect(jsonPath("$.status").value("in_progress"));

        mockMvc.perform(get("/legal-acts/property-suggestions-jobs")
                        .queryParam("jobIds", "11111111-1111-1111-1111-111111111111")
                        .with(oidcAuthentication()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].job_id").value("00000000-0000-0000-0000-000000000201"))
                .andExpect(jsonPath("$[0].selected_class_id").value("mock-class-001"))
                .andExpect(jsonPath("$[0].new_attribute_suggestions[0].suggestion_id").value("mock-attribute-suggestion-001"));
    }

    @Test
    void returnsPredeterminedRelationshipSuggestionResponses() throws Exception {
        mockMvc.perform(post("/legal-acts/2024/1/2024-01-01/relationship-suggestions-top-k-extraction-jobs")
                        .with(oidcAuthentication())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "k": 2,
                                  "selected_class_id": "request-class-that-is-ignored",
                                  "known_conceptual_model": {
                                    "classes": [{"termID": "request-class-that-is-ignored"}],
                                    "attributes": [],
                                    "relationships": []
                                  }
                                }
                                """))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.job_id").value("00000000-0000-0000-0000-000000000301"))
                .andExpect(jsonPath("$.selected_class_id").doesNotExist())
                .andExpect(jsonPath("$.status").value("in_progress"));

        mockMvc.perform(get("/legal-acts/relationship-suggestions-jobs")
                        .queryParam("jobIds", "11111111-1111-1111-1111-111111111111")
                        .with(oidcAuthentication()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].job_id").value("00000000-0000-0000-0000-000000000301"))
                .andExpect(jsonPath("$[0].selected_class_id").value("mock-class-001"))
                .andExpect(jsonPath("$[0].new_relationship_suggestions[0].suggestion_id").value("mock-relationship-suggestion-001"));
    }

    @Test
    void returnsPredeterminedFeedbackResponses() throws Exception {
        String body = """
                [{
                  "jobID": "11111111-1111-1111-1111-111111111111",
                  "suggestionID": ["request-suggestion-that-is-ignored"]
                }]
                """;

        mockMvc.perform(post("/accept-suggestion")
                        .with(oidcAuthentication())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("accepted"));

        mockMvc.perform(post("/like-suggestion")
                        .with(oidcAuthentication())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("liked"));

        mockMvc.perform(post("/dislike-suggestion")
                        .with(oidcAuthentication())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("disliked"));
    }

    @Test
    void stillRejectsInvalidRequestsInDevelopment() throws Exception {
        mockMvc.perform(post("/legal-acts/2024/1/2024-01-01/property-suggestions-top-k-extraction-jobs")
                        .with(oidcAuthentication())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "k": 2
                                }
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").exists());
    }
}
