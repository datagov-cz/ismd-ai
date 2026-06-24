package cz.cvut.ismd.assistant.service;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.tokens")
public record TokenUsageProperties(
        @Min(1) int maxAllowedPerDay
) {
}
