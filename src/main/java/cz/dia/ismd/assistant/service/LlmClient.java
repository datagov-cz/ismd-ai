package cz.dia.ismd.assistant.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import cz.dia.ismd.assistant.model.llm.LlmProvider;
import cz.dia.ismd.assistant.exception.LlmException;
import cz.dia.ismd.assistant.model.llm.LlmCompletionRequest;
import cz.dia.ismd.assistant.model.llm.LlmCompletionResponse;
import cz.dia.ismd.assistant.config.LlmProperties;
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
    private final ObjectMapper objectMapper;
    private final TokenUsageService tokenUsageService;

    public LlmClient(
            RestClient llmRestClient,
            LlmProperties properties,
            ObjectMapper objectMapper,
            TokenUsageService tokenUsageService
    ) {
        this.restClient = llmRestClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.tokenUsageService = tokenUsageService;
    }

    public LlmCompletionResponse complete(String userId, LlmCompletionRequest request) {
        if (!properties.enabled()) {
            throw new LlmException("LLM integration is disabled. Set APP_LLM_ENABLED=true to enable external LLM calls.");
        }
        validateConfiguration();

        URI endpoint = endpoint();
        tokenUsageService.ensureRequestAllowed(userId);
        JsonNode response = switch (properties.provider()) {
            case OPENAI, OPENAI_COMPATIBLE, MISTRAL -> postOpenAiCompatible(endpoint, request, true);
            case AZURE_OPENAI -> postOpenAiCompatible(endpoint, request, false);
            case ANTHROPIC -> postAnthropic(endpoint, request);
            case GOOGLE -> postGoogle(endpoint, request);
            case COHERE -> postCohere(endpoint, request);
            case OLLAMA -> postOllama(endpoint, request);
        };
        tokenUsageService.addOutputTokens(userId, extractOutputTokenCount(response));
        return new LlmCompletionResponse(
                properties.provider(),
                properties.model(),
                endpoint.toString(),
                extractContent(response),
                extractUsage(response)
        );
    }

    public <T> T completeStructured(
            String userId,
            LlmCompletionRequest request,
            String schemaName,
            JsonNode schema,
            Class<T> responseType
    ) {
        if (!properties.enabled()) {
            throw new LlmException("LLM integration is disabled. Set APP_LLM_ENABLED=true to enable external LLM calls.");
        }
        validateConfiguration();

        URI endpoint = endpoint();
        tokenUsageService.ensureRequestAllowed(userId);
        JsonNode response = switch (properties.provider()) {
            case OPENAI, OPENAI_COMPATIBLE, MISTRAL -> postOpenAiCompatible(
                    endpoint, request, true, openAiResponseFormat(schemaName, schema));
            case AZURE_OPENAI -> postOpenAiCompatible(
                    endpoint, request, false, openAiResponseFormat(schemaName, schema));
            case ANTHROPIC -> postAnthropicStructured(endpoint, request, schema);
            case GOOGLE -> postGoogleStructured(endpoint, request, schema);
            case COHERE -> postCohereStructured(endpoint, request, schema);
            case OLLAMA -> postOllamaStructured(endpoint, request, schema);
        };
        tokenUsageService.addOutputTokens(userId, extractOutputTokenCount(response));
        String content = extractStructuredContent(response);
        try {
            return objectMapper.readValue(content, responseType);
        } catch (JsonProcessingException exception) {
            throw new LlmException("LLM provider returned a response that could not be deserialized as "
                    + responseType.getSimpleName(), exception);
        }
    }

    private Map<String, Object> openAiResponseFormat(String schemaName, JsonNode schema) {
        return Map.of(
                "type", "json_schema",
                "json_schema", Map.of(
                        "name", schemaName,
                        "strict", true,
                        "schema", schema
                )
        );
    }

    private JsonNode postOpenAiCompatible(URI endpoint, LlmCompletionRequest request, boolean includeModel) {
        return postOpenAiCompatible(endpoint, request, includeModel, null);
    }

    private JsonNode postOpenAiCompatible(
            URI endpoint,
            LlmCompletionRequest request,
            boolean includeModel,
            Map<String, Object> responseFormat
    ) {
        Map<String, Object> body = new LinkedHashMap<>();
        if (includeModel) {
            body.put("model", properties.model());
        }
        body.put("messages", chatMessages(request));
        body.put("max_tokens", maxTokens(request));
        body.put("temperature", temperature(request));
        if (responseFormat != null) {
            body.put("response_format", responseFormat);
        }

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

    private JsonNode postAnthropicStructured(URI endpoint, LlmCompletionRequest request, JsonNode schema) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", properties.model());
        body.put("max_tokens", maxTokens(request));
        body.put("temperature", temperature(request));
        body.put("system", systemPrompt(request));
        body.put("messages", List.of(Map.of("role", "user", "content", prompt(request))));
        body.put("output_config", Map.of("format", Map.of("type", "json_schema", "schema", schema)));

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

    private JsonNode postGoogleStructured(URI endpoint, LlmCompletionRequest request, JsonNode schema) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("systemInstruction", Map.of("parts", List.of(Map.of("text", systemPrompt(request)))));
        body.put("contents", List.of(Map.of(
                "role", "user",
                "parts", List.of(Map.of("text", prompt(request)))
        )));
        body.put("generationConfig", Map.of(
                "maxOutputTokens", maxTokens(request),
                "temperature", temperature(request),
                "responseMimeType", "application/json",
                "responseJsonSchema", schema
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

    private JsonNode postCohereStructured(URI endpoint, LlmCompletionRequest request, JsonNode schema) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", properties.model());
        body.put("messages", List.of(
                Map.of("role", "system", "content", systemPrompt(request)),
                Map.of("role", "user", "content", prompt(request))
        ));
        body.put("max_tokens", maxTokens(request));
        body.put("temperature", temperature(request));
        body.put("response_format", Map.of("type", "json_object", "schema", schema));

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

    private JsonNode postOllamaStructured(URI endpoint, LlmCompletionRequest request, JsonNode schema) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", properties.model());
        body.put("messages", chatMessages(request));
        body.put("stream", false);
        body.put("format", schema);
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

    private String extractStructuredContent(JsonNode response) {
        if (response == null) {
            throw new LlmException("LLM provider returned an empty response");
        }
        ensureStructuredResponseCompleted(response);
        return extractContent(response);
    }

    private void ensureStructuredResponseCompleted(JsonNode response) {
        switch (properties.provider()) {
            case OPENAI, OPENAI_COMPATIBLE, AZURE_OPENAI, MISTRAL -> {
                JsonNode refusal = response.at("/choices/0/message/refusal");
                if (refusal.isTextual() && !refusal.asText().isBlank()) {
                    throw new LlmException("LLM provider refused the structured request: " + refusal.asText());
                }
                requireFinishReason(response.at("/choices/0/finish_reason"), "stop");
            }
            case ANTHROPIC -> rejectFinishReason(response.path("stop_reason"), "max_tokens");
            case GOOGLE -> requireFinishReason(response.at("/candidates/0/finishReason"), "STOP");
            case COHERE -> requireFinishReason(response.path("finish_reason"), "COMPLETE");
            case OLLAMA -> {
                if (response.has("done") && !response.path("done").asBoolean()) {
                    throw new LlmException("LLM provider did not complete the structured response");
                }
                rejectFinishReason(response.path("done_reason"), "length");
            }
        }
    }

    private void requireFinishReason(JsonNode finishReason, String expected) {
        if (finishReason.isTextual() && !expected.equalsIgnoreCase(finishReason.asText())) {
            throw new LlmException("LLM provider did not complete the structured response: " + finishReason.asText());
        }
    }

    private void rejectFinishReason(JsonNode finishReason, String rejected) {
        if (finishReason.isTextual() && rejected.equalsIgnoreCase(finishReason.asText())) {
            throw new LlmException("LLM provider did not complete the structured response: " + finishReason.asText());
        }
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

    private int extractOutputTokenCount(JsonNode response) {
        if (response == null) {
            return 0;
        }

        JsonNode outputTokens = switch (properties.provider()) {
            case OPENAI, OPENAI_COMPATIBLE, AZURE_OPENAI, MISTRAL -> response.at("/usage/completion_tokens");
            case ANTHROPIC -> response.at("/usage/output_tokens");
            case GOOGLE -> response.at("/usageMetadata/candidatesTokenCount");
            case COHERE -> firstPresent(
                    response.at("/usage/tokens/output_tokens"),
                    response.at("/usage/billed_units/output_tokens")
            );
            case OLLAMA -> response.path("eval_count");
        };

        if (outputTokens.isMissingNode() || outputTokens.isNull()) {
            return 0;
        }
        if (!outputTokens.canConvertToInt() || outputTokens.asInt() < 0) {
            throw new LlmException("LLM provider returned an invalid output token count");
        }
        return outputTokens.asInt();
    }

    private JsonNode firstPresent(JsonNode first, JsonNode second) {
        return first.isMissingNode() || first.isNull() ? second : first;
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
