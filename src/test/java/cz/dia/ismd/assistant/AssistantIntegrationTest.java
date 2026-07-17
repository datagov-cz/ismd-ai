package cz.dia.ismd.assistant;

import cz.dia.ismd.assistant.api.suggestion.classsuggestion.ClassSuggestionJobRequest;
import cz.dia.ismd.assistant.model.job.JobStatus;
import cz.dia.ismd.assistant.model.legal.LegalActText;
import cz.dia.ismd.assistant.model.suggestion.DocumentContext;
import cz.dia.ismd.assistant.service.ClassSuggestionLlmService;
import cz.dia.ismd.assistant.service.LegalActSPARQLService;
import cz.dia.ismd.assistant.service.SuggestionGenerator;
import cz.dia.ismd.assistant.service.SuggestionJobService;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

@SpringBootTest(
        classes = SemanticModelingAssistantApplication.class,
        properties = {
                "spring.security.oauth2.resourceserver.jwt.issuer-uri=https://issuer.example.test",
                "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=https://issuer.example.test/.well-known/jwks.json"
        }
)
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
abstract class AssistantIntegrationTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @Autowired
    private SuggestionJobService suggestionJobService;

    @MockBean
    protected ClassSuggestionLlmService classSuggestionLlmService;

    @MockBean
    protected LegalActSPARQLService legalActSPARQLService;

    @BeforeEach
    void configureClassSuggestionLlmService() {
        SuggestionGenerator generator = new SuggestionGenerator();
        when(legalActSPARQLService.retrieveLegalActTexts(any()))
                .thenAnswer(invocation -> ((List<String>) invocation.getArgument(0)).stream()
                        .map(identifier -> new LegalActText(
                                1L, 1L, identifier.substring(identifier.indexOf("/eli/cz/sb/") + 11),
                                "Legal text", "paragraph", "1"))
                        .toList());
        when(classSuggestionLlmService.suggestClasses(
                anyString(), any(ClassSuggestionJobRequest.class), any()))
                .thenAnswer(invocation -> generator.classSuggestions(
                        DocumentContext.legal(2024, 1, java.time.LocalDate.of(2024, 1, 1)),
                        invocation.getArgument(1)
                ));
    }

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    protected static RequestPostProcessor oidcAuthentication() {
        return jwt().jwt(token -> token
                .issuer("https://issuer.example.test")
                .subject("test-user")
                .claim("scope", "openid profile")
        );
    }

    protected void waitForAsyncJob(UUID... jobIds) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            boolean allCompleted = true;
            for (UUID jobId : jobIds) {
                JobStatus status = suggestionJobService.get(jobId).status();
                if (status == JobStatus.FAILED) {
                    throw new AssertionError("Suggestion job failed: " + jobId);
                }
                if (status != JobStatus.COMPLETED) {
                    allCompleted = false;
                }
            }
            if (allCompleted) {
                return;
            }
            Thread.sleep(10);
        }
        throw new AssertionError("Timed out waiting for suggestion jobs to complete");
    }

    protected static final class Json {
        private Json() {
        }

        static String read(MvcResult result, String fieldName) throws Exception {
            String body = result.getResponse().getContentAsString();
            com.fasterxml.jackson.databind.JsonNode json = new com.fasterxml.jackson.databind.ObjectMapper().readTree(body);
            return json.get(fieldName).asText();
        }
    }
}
