package cz.dia.ismd.assistant;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@TestPropertySource(properties = "app.tokens.max-allowed-per-day=2")
class TokenLimitIntegrationTests extends AssistantIntegrationTest {

    @Test
    void usesConfiguredTimezoneForTokenUsageDates() {
        String timezone = jdbcTemplate.queryForObject("SHOW TIME ZONE", String.class);

        assertThat(timezone).isEqualTo("Europe/Prague");
    }

    @Test
    void rejectsJobWhenDailyTokenLimitHasBeenReached() throws Exception {
        jdbcTemplate.update("""
                INSERT INTO token_usage(user_id, usage_date, tokens_spent)
                VALUES (?, CURRENT_DATE, ?)
                """, "daily-limit-user", 2);

        mockMvc.perform(post("/legal-acts/2024/1/2024-01-01/class-suggestions-top-k-extraction-jobs")
                        .with(jwt().jwt(token -> token
                                .issuer("https://issuer.example.test")
                                .subject("daily-limit-user")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"k": 1}
                                """))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("token limit")));
    }
}
