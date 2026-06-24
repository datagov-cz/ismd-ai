package cz.cvut.ismd.assistant.service.llm;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class LlmClient {

    private static final String DEFAULT_SYSTEM_PROMPT = "You are a concise assistant.";
    private static final String ANTHROPIC_VERSION = "2023-06-01";

    private final RestClient restClient;
    private final LlmProperties properties;

    public LlmClient(RestClient llmRestClient, LlmProperties properties) {
        this.restClient = llmRestClient;
        this.properties = properties;
    }

    public LlmCompletionResponse complete(LlmCompletionRequest request) {
        if (!properties.enabled()) {
            throw new LlmException("LLM integration is disabled. Set APP_LLM_ENABLED=true to enable external LLM calls.");
        }
        validateConfiguration();

        URI endpoint = endpoint();
        JsonNode response = switch (properties.provider()) {
            case OPENAI, OPENAI_COMPATIBLE, MISTRAL -> postOpenAiCompatible(endpoint, request, true);
            case AZURE_OPENAI -> postOpenAiCompatible(endpoint, request, false);
            case ANTHROPIC -> postAnthropic(endpoint, request);
            case GOOGLE -> postGoogle(endpoint, request);
            case COHERE -> postCohere(endpoint, request);
            case OLLAMA -> postOllama(endpoint, request);
        };
        return new LlmCompletionResponse(
                properties.provider(),
                properties.model(),
                endpoint.toString(),
                extractContent(response),
                extractUsage(response)
        );
    }

    private JsonNode postOpenAiCompatible(URI endpoint, LlmCompletionRequest request, boolean includeModel) {
        Map<String, Object> body = new LinkedHashMap<>();
        if (includeModel) {
            body.put("model", properties.model());
        }
        body.put("messages", chatMessages(request));
        body.put("max_tokens", maxTokens(request));
        body.put("temperature", temperature(request));

        return post(endpoint, body, headers -> {
            headers.setBearerAuth(properties.apiKey());
        });
    }

    private JsonNode postAnthropic(URI endpoint, LlmCompletionRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", properties.model());
        body.put("max_tokens", maxTokens(request));
        body.put("temperature", temperature(request));
        body.put("system", systemPrompt(request));
        body.put("messages", List.of(Map.of(
                "role", "user",
                "content", prompt(request)
        )));

        return post(endpoint, body, headers -> {
            headers.set("x-api-key", properties.apiKey());
            headers.set("anthropic-version", ANTHROPIC_VERSION);
        });
    }

    private JsonNode postGoogle(URI endpoint, LlmCompletionRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("systemInstruction", Map.of("parts", List.of(Map.of("text", systemPrompt(request)))));
        body.put("contents", List.of(Map.of(
                "role", "user",
                "parts", List.of(Map.of("text", prompt(request)))
        )));
        body.put("generationConfig", Map.of(
                "maxOutputTokens", maxTokens(request),
                "temperature", temperature(request)
        ));

        return post(endpoint, body, headers -> headers.set("x-goog-api-key", properties.apiKey()));
    }

    private JsonNode postCohere(URI endpoint, LlmCompletionRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", properties.model());
        body.put("messages", List.of(
                Map.of("role", "system", "content", systemPrompt(request)),
                Map.of("role", "user", "content", prompt(request))
        ));
        body.put("max_tokens", maxTokens(request));
        body.put("temperature", temperature(request));

        return post(endpoint, body, headers -> headers.setBearerAuth(properties.apiKey()));
    }

    private JsonNode postOllama(URI endpoint, LlmCompletionRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", properties.model());
        body.put("messages", chatMessages(request));
        body.put("stream", false);
        body.put("options", Map.of(
                "num_predict", maxTokens(request),
                "temperature", temperature(request)
        ));

        return post(endpoint, body, headers -> {
        });
    }

    private JsonNode post(URI endpoint, Map<String, Object> body, HeaderCustomizer headerCustomizer) {
        try {
            return restClient.post()
                    .uri(endpoint)
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(headerCustomizer::customize)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientException exception) {
            throw new LlmException("LLM provider request failed: " + exception.getMessage(), exception);
        }
    }

    private String extractContent(JsonNode response) {
        if (response == null) {
            throw new LlmException("LLM provider returned an empty response");
        }
        JsonNode content = switch (properties.provider()) {
            case OPENAI, OPENAI_COMPATIBLE, AZURE_OPENAI, MISTRAL -> response.at("/choices/0/message/content");
            case ANTHROPIC -> response.at("/content/0/text");
            case GOOGLE -> response.at("/candidates/0/content/parts/0/text");
            case COHERE -> response.at("/message/content/0/text");
            case OLLAMA -> response.at("/message/content");
        };
        if (content.isMissingNode() || !content.isTextual()) {
            throw new LlmException("LLM provider response did not contain generated text");
        }
        return content.asText();
    }

    private JsonNode extractUsage(JsonNode response) {
        return switch (properties.provider()) {
            case OPENAI, OPENAI_COMPATIBLE, AZURE_OPENAI, MISTRAL -> response.get("usage");
            case ANTHROPIC -> response.get("usage");
            case GOOGLE -> response.get("usageMetadata");
            case COHERE -> response.at("/usage/tokens");
            case OLLAMA -> response;
        };
    }

    private List<Map<String, String>> chatMessages(LlmCompletionRequest request) {
        return List.of(
                Map.of("role", "system", "content", systemPrompt(request)),
                Map.of("role", "user", "content", prompt(request))
        );
    }

    private URI endpoint() {
        String endpoint = properties.effectiveEndpointUrl().toString().replace("{model}", properties.model());
        return URI.create(endpoint);
    }

    private String prompt(LlmCompletionRequest request) {
        if (request.prompt() == null || request.prompt().isBlank()) {
            throw new LlmException("Prompt must not be blank");
        }
        return request.prompt();
    }

    private String systemPrompt(LlmCompletionRequest request) {
        return request.systemPrompt() == null || request.systemPrompt().isBlank()
                ? DEFAULT_SYSTEM_PROMPT
                : request.systemPrompt();
    }

    private int maxTokens(LlmCompletionRequest request) {
        return request.maxTokens() == null ? properties.maxTokens() : request.maxTokens();
    }

    private double temperature(LlmCompletionRequest request) {
        return request.temperature() == null ? properties.temperature() : request.temperature();
    }

    private void validateConfiguration() {
        if (properties.provider() != LlmProvider.OLLAMA
                && (properties.apiKey() == null || properties.apiKey().isBlank())) {
            throw new LlmException("LLM API key is not configured for provider " + properties.provider());
        }
    }

    @FunctionalInterface
    private interface HeaderCustomizer {
        void customize(HttpHeaders headers);
    }
}
