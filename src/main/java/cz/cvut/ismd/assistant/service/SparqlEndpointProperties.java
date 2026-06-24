package cz.cvut.ismd.assistant.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;

@ConfigurationProperties(prefix = "app.sparql")
public record SparqlEndpointProperties(URI endpointUrl) {
}
