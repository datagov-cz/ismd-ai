package cz.dia.ismd.assistant.service.sparql;

import cz.dia.ismd.assistant.records.sparql.SparqlEndpointProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(SparqlEndpointProperties.class)
public class SparqlClientConfig {

    @Bean
    RestClient sparqlRestClient(RestClient.Builder builder, SparqlEndpointProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.timeout());
        requestFactory.setReadTimeout(properties.timeout());
        return builder
                .requestFactory(requestFactory)
                .baseUrl(properties.endpointUrl().toString())
                .build();
    }
}
