package cz.dia.ismd.assistant.config;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import static org.assertj.core.api.Assertions.assertThat;

class RequiredSubjectJwtValidatorTests {

    private final RequiredSubjectJwtValidator validator = new RequiredSubjectJwtValidator();

    @Test
    void acceptsTokenWithNonBlankSubject() {
        Jwt jwt = jwtBuilder().subject("user-123").build();

        assertThat(validator.validate(jwt).hasErrors()).isFalse();
    }

    @Test
    void rejectsTokenWithoutSubject() {
        OAuth2TokenValidatorResult result = validator.validate(jwtBuilder().build());

        assertThat(result.hasErrors()).isTrue();
        assertThat(result.getErrors()).singleElement().satisfies(error -> {
            assertThat(error.getErrorCode()).isEqualTo("invalid_token");
            assertThat(error.getDescription()).contains("subject (sub)");
        });
    }

    @Test
    void rejectsTokenWithBlankSubject() {
        assertThat(validator.validate(jwtBuilder().subject("  ").build()).hasErrors()).isTrue();
    }

    private Jwt.Builder jwtBuilder() {
        return Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .claim("iss", "https://issuer.example.test");
    }
}
