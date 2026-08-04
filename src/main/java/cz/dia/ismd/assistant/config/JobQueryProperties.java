package cz.dia.ismd.assistant.config;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.job-query")
public record JobQueryProperties(
        @Min(1) Integer maxJobIds
) {

    public JobQueryProperties {
        maxJobIds = maxJobIds == null ? 100 : maxJobIds;
    }
}
