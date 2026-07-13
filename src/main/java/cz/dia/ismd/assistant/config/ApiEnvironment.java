package cz.dia.ismd.assistant.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;

@Component
public class ApiEnvironment {

    private static final Set<String> DEVELOPMENT_NAMES = Set.of("dev", "development");

    private final String configuredEnvironment;
    private final Environment springEnvironment;

    public ApiEnvironment(
            @Value("${app.environment:production}") String configuredEnvironment,
            Environment springEnvironment
    ) {
        this.configuredEnvironment = configuredEnvironment;
        this.springEnvironment = springEnvironment;
    }

    public boolean isDevelopment() {
        return isDevelopmentName(configuredEnvironment)
                || Arrays.stream(springEnvironment.getActiveProfiles()).anyMatch(this::isDevelopmentName);
    }

    private boolean isDevelopmentName(String value) {
        return value != null && DEVELOPMENT_NAMES.contains(value.toLowerCase(Locale.ROOT));
    }
}
