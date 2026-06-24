package cz.dia.ismd.assistant.main;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LegalActPathValidationIntegrationTests extends AssistantIntegrationTest {

    @Test
    void acceptsYearMonthDayLegalActDateFormat() throws Exception {
        mockMvc.perform(post("/legal-acts/2026/1/2026-01-01/class-suggestions-top-k-extraction-jobs")
                        .with(oidcAuthentication())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"k": 1}
                                """))
                .andExpect(status().isAccepted());

        mockMvc.perform(post("/legal-acts/2026/1/2026-01-01/property-suggestions-top-k-extraction-jobs")
                        .with(oidcAuthentication())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "k": 1,
                                  "selected_class_id": "class_001"
                                }
                                """))
                .andExpect(status().isAccepted());

        mockMvc.perform(post("/legal-acts/2026/1/2026-01-01/relationship-suggestions-top-k-extraction-jobs")
                        .with(oidcAuthentication())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "k": 1,
                                  "selected_class_id": "class_001"
                                }
                                """))
                .andExpect(status().isAccepted());
    }

    @Test
    void rejectsNonPaddedLegalActDateFormat() throws Exception {
        mockMvc.perform(post("/legal-acts/2026/1/2026-1-1/class-suggestions-top-k-extraction-jobs")
                        .with(oidcAuthentication())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"k": 1}
                                """))
                .andExpect(status().isBadRequest());
    }
}
