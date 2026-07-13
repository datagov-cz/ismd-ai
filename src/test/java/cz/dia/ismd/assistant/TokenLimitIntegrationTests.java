package cz.dia.ismd.assistant;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@TestPropertySource(properties = "app.tokens.max-allowed-per-day=2")
class TokenLimitIntegrationTests extends AssistantIntegrationTest {

    @Test
    void rejectsJobWhenDailyTokenLimitWouldBeExceeded() throws Exception {
        mockMvc.perform(post("/legal-acts/2024/1/2024-01-01/class-suggestions-top-k-extraction-jobs")
                        .with(jwt().jwt(token -> token
                                .issuer("https://issuer.example.test")
                                .subject("daily-limit-user")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"k": 2}
                                """))
                .andExpect(status().isAccepted());

        mockMvc.perform(post("/legal-acts/2024/1/2024-01-01/class-suggestions-top-k-extraction-jobs")
                        .with(jwt().jwt(token -> token
                                .issuer("https://issuer.example.test")
                                .subject("daily-limit-user")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"k": 1}
                                """))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("daily token limit")));
    }
}
