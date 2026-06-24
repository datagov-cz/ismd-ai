package cz.cvut.ismd.assistant.service;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(SparqlEndpointProperties.class)
public class SparqlClientConfig {

    @Bean
    RestClient sparqlRestClient(RestClient.Builder builder, SparqlEndpointProperties properties) {
        return builder
                .baseUrl(properties.endpointUrl().toString())
                .build();
    }
}
