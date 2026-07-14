package cz.dia.ismd.assistant.config;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.tokens")
public record TokenUsageProperties(
        @Min(0) int maxAllowedPerDay
) {
}
