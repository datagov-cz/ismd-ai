package cz.dia.ismd.assistant.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(TokenUsageProperties.class)
public class TokenUsageConfig {
}
