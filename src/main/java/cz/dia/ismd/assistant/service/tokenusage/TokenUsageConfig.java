package cz.dia.ismd.assistant.service.tokenusage;

import cz.dia.ismd.assistant.records.tokenusage.TokenUsageProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(TokenUsageProperties.class)
public class TokenUsageConfig {
}
