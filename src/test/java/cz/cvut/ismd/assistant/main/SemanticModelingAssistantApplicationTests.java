package cz.dia.ismd.assistant.main;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=https://issuer.example.test",
        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=https://issuer.example.test/.well-known/jwks.json",
        "spring.datasource.url=jdbc:sqlite:target/feedback-test.sqlite"
})
@AutoConfigureMockMvc
class SemanticModelingAssistantApplicationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void rejectsRequestsWithoutOidcAuthentication() throws Exception {
        mockMvc.perform(post("/legal-acts/2024/1/2024-01-01/class-suggestions-top-k-extraction-jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"k": 1}
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void startsAndCompletesLegalClassSuggestionJob() throws Exception {
        MvcResult start = mockMvc.perform(post("/legal-acts/2024/1/2024-01-01/class-suggestions-top-k-extraction-jobs")
                        .with(oidcAuthentication())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "k": 2,
                                  "structural_element_ids": ["§1", "§2"],
                                  "context_text": "Property rights"
                                }
                                """))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("in_progress"))
                .andReturn();

        UUID jobId = UUID.fromString(Json.read(start, "job_id"));
        waitForAsyncJob();

        mockMvc.perform(get("/legal-acts/2024/1/2024-01-01/class-suggestions-jobs/{jobId}", jobId)
                        .with(oidcAuthentication()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.job_id").value(jobId.toString()))
                .andExpect(jsonPath("$.status").value("completed"))
                .andExpect(jsonPath("$.new_suggestions", hasSize(2)))
                .andExpect(jsonPath("$.new_suggestions[0].name.@value").value("Property Actor"))
                .andExpect(jsonPath("$.new_suggestions[0].legal_act.official_number").value("1/2024"));
    }

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

        mockMvc.perform(get("/legal-acts/2026/1/2026-1-1/class-suggestions-jobs/{jobId}", UUID.randomUUID())
                        .with(oidcAuthentication()))
                .andExpect(status().isBadRequest());
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
    void startsAndCompletesLegalRelationshipSuggestionJob() throws Exception {
        MvcResult start = mockMvc.perform(post("/legal-acts/2024/1/2024-01-01/relationship-suggestions-top-k-extraction-jobs")
                        .with(oidcAuthentication())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "k": 2,
                                  "selected_class_id": "class_001",
                                  "structural_element_ids": ["§1", "§2"],
                                  "context_text": "Property rights"
                                }
                                """))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.selected_class_id").value("class_001"))
                .andExpect(jsonPath("$.status").value("in_progress"))
                .andReturn();

        UUID jobId = UUID.fromString(Json.read(start, "job_id"));
        waitForAsyncJob();

        mockMvc.perform(get("/legal-acts/2024/1/2024-01-01/relationship-suggestions-jobs/{jobId}", jobId)
                        .with(oidcAuthentication()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.job_id").value(jobId.toString()))
                .andExpect(jsonPath("$.selected_class_id").value("class_001"))
                .andExpect(jsonPath("$.status").value("completed"))
                .andExpect(jsonPath("$.new_relationship_suggestions", hasSize(2)))
                .andExpect(jsonPath("$.new_relationship_suggestions[0].legal_act.official_number").value("1/2024"));
    }

    @Test
    void recordsFeedbackForExistingJob() throws Exception {
        MvcResult start = mockMvc.perform(post("/legal-acts/2024/1/2024-01-01/class-suggestions-top-k-extraction-jobs")
                        .with(oidcAuthentication())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"k": 1}
                                """))
                .andExpect(status().isAccepted())
                .andReturn();

        UUID jobId = UUID.fromString(Json.read(start, "job_id"));

        mockMvc.perform(post("/like-suggestion")
                        .with(oidcAuthentication())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "job_id": "%s",
                                  "suggestion_id": "class_001"
                                }
                                """.formatted(jobId)))
                .andExpect(status().isNoContent());

        Integer records = jdbcTemplate.queryForObject("""
                        SELECT COUNT(*)
                        FROM feedback_records
                        WHERE job_id = ? AND suggestion_id = ? AND feedback_type = ?
                        """,
                Integer.class,
                jobId.toString(),
                "class_001",
                "LIKED");
        org.assertj.core.api.Assertions.assertThat(records).isEqualTo(1);
    }

    @Test
    void recordsConcurrentFeedbackWrites() throws Exception {
        MvcResult start = mockMvc.perform(post("/legal-acts/2024/1/2024-01-01/class-suggestions-top-k-extraction-jobs")
                        .with(oidcAuthentication())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"k": 1}
                                """))
                .andExpect(status().isAccepted())
                .andReturn();

        UUID jobId = UUID.fromString(Json.read(start, "job_id"));
        int writeCount = 20;
        CountDownLatch startGate = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(8);

        try {
            List<Future<Void>> writes = IntStream.range(0, writeCount)
                    .mapToObj(index -> executor.submit((Callable<Void>) () -> {
                        startGate.await();
                        mockMvc.perform(post("/like-suggestion")
                                        .with(oidcAuthentication())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("""
                                                {
                                                  "job_id": "%s",
                                                  "suggestion_id": "class_%03d"
                                                }
                                                """.formatted(jobId, index)))
                                .andExpect(status().isNoContent());
                        return null;
                    }))
                    .toList();

            startGate.countDown();
            for (Future<Void> write : writes) {
                write.get(5, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
        }

        Integer records = jdbcTemplate.queryForObject("""
                        SELECT COUNT(*)
                        FROM feedback_records
                        WHERE job_id = ?
                        """,
                Integer.class,
                jobId.toString());
        org.assertj.core.api.Assertions.assertThat(records).isEqualTo(writeCount);
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor oidcAuthentication() {
        return jwt().jwt(token -> token
                .issuer("https://issuer.example.test")
                .subject("test-user")
                .claim("scope", "openid profile")
        );
    }

    private void waitForAsyncJob() throws InterruptedException {
        Thread.sleep(150);
    }

    private static final class Json {
        private Json() {
        }

        static String read(MvcResult result, String fieldName) throws Exception {
            String body = result.getResponse().getContentAsString();
            com.fasterxml.jackson.databind.JsonNode json = new com.fasterxml.jackson.databind.ObjectMapper().readTree(body);
            return json.get(fieldName).asText();
        }
    }
}
