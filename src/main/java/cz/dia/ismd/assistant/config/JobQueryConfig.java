package cz.dia.ismd.assistant.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(JobQueryProperties.class)
public class JobQueryConfig {
}
