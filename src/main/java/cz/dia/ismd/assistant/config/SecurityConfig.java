package cz.dia.ismd.assistant.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    private final ApiEnvironment apiEnvironment;

    public SecurityConfig(ApiEnvironment apiEnvironment) {
        this.apiEnvironment = apiEnvironment;
    }

    @Bean
    OAuth2TokenValidator<Jwt> requiredSubjectJwtValidator() {
        return new RequiredSubjectJwtValidator();
    }

    @Bean
    JwtDecoder jwtDecoder(
            @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuerUri,
            @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}") String jwkSetUri,
            OAuth2TokenValidator<Jwt> requiredSubjectJwtValidator
    ) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(issuerUri),
                requiredSubjectJwtValidator
        ));
        return decoder;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> {
                    authorize.requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll();
                    if (apiEnvironment.isDevelopment()) {
                        authorize.anyRequest().permitAll();
                    } else {
                        authorize.anyRequest().authenticated();
                    }
                });

        if (!apiEnvironment.isDevelopment()) {
            http.oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()));
        }

        return http.build();
    }
}
