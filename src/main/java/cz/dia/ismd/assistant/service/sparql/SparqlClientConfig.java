package cz.dia.ismd.assistant.service.sparql;

import cz.dia.ismd.assistant.records.sparql.SparqlEndpointProperties;
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
