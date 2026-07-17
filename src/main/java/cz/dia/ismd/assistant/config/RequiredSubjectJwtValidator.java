package cz.dia.ismd.assistant.config;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.util.StringUtils;

final class RequiredSubjectJwtValidator implements OAuth2TokenValidator<Jwt> {

    private static final OAuth2Error MISSING_SUBJECT = new OAuth2Error(
            "invalid_token",
            "The JWT subject (sub) claim is required.",
            null
    );

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        if (!StringUtils.hasText(jwt.getSubject())) {
            return OAuth2TokenValidatorResult.failure(MISSING_SUBJECT);
        }
        return OAuth2TokenValidatorResult.success();
    }
}
