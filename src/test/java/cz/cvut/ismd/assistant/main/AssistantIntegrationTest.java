package cz.dia.ismd.assistant.main;

import cz.dia.ismd.assistant.main.SemanticModelingAssistantApplication;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

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
abstract class AssistantIntegrationTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

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

    protected void waitForAsyncJob() throws InterruptedException {
        Thread.sleep(150);
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
