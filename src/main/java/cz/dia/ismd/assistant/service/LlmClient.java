package cz.dia.ismd.assistant.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.MissingNode;
import cz.dia.ismd.assistant.model.llm.LlmProvider;
import cz.dia.ismd.assistant.exception.LlmException;
import cz.dia.ismd.assistant.model.llm.LlmCompletionRequest;
import cz.dia.ismd.assistant.model.llm.LlmCompletionResponse;
import cz.dia.ismd.assistant.config.LlmProperties;
import cz.dia.ismd.assistant.config.LlmProperties.ReasoningEffort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.URI;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class LlmClient {

    private static final String DEFAULT_SYSTEM_PROMPT = "You are a concise assistant.";
    private static final String ANTHROPIC_VERSION = "2023-06-01";
    private static final Set<ReasoningEffort> AZURE_REASONING_EFFORTS = EnumSet.of(
            ReasoningEffort.NONE, ReasoningEffort.MINIMAL, ReasoningEffort.LOW,
            ReasoningEffort.MEDIUM, ReasoningEffort.HIGH, ReasoningEffort.XHIGH);
    private static final Set<ReasoningEffort> ANTHROPIC_REASONING_EFFORTS = EnumSet.of(
            ReasoningEffort.LOW, ReasoningEffort.MEDIUM, ReasoningEffort.HIGH,
            ReasoningEffort.XHIGH, ReasoningEffort.MAX);
    private static final Set<ReasoningEffort> GOOGLE_REASONING_EFFORTS = EnumSet.of(
            ReasoningEffort.MINIMAL, ReasoningEffort.LOW, ReasoningEffort.MEDIUM, ReasoningEffort.HIGH);
    private static final Set<ReasoningEffort> MISTRAL_REASONING_EFFORTS = EnumSet.of(
            ReasoningEffort.NONE, ReasoningEffort.MINIMAL, ReasoningEffort.LOW,
            ReasoningEffort.MEDIUM, ReasoningEffort.HIGH, ReasoningEffort.XHIGH);
    private static final Set<ReasoningEffort> OLLAMA_REASONING_EFFORTS = EnumSet.of(
            ReasoningEffort.NONE, ReasoningEffort.LOW, ReasoningEffort.MEDIUM,
            ReasoningEffort.HIGH, ReasoningEffort.MAX);

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
            case OPENAI, AZURE_OPENAI -> postResponse(endpoint, request, null);
            case OPENAI_COMPATIBLE, MISTRAL -> postOpenAiCompatible(endpoint, request);
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
            case OPENAI, AZURE_OPENAI -> postResponse(
                    endpoint, request, responseTextFormat(schemaName, schema));
            case OPENAI_COMPATIBLE, MISTRAL -> postOpenAiCompatible(
                    endpoint, request, openAiResponseFormat(schemaName, schema));
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

    private Map<String, Object> responseTextFormat(String schemaName, JsonNode schema) {
        return Map.of(
                "type", "json_schema",
                "name", schemaName,
                "strict", true,
                "schema", schema
        );
    }

    private JsonNode postResponse(
            URI endpoint,
            LlmCompletionRequest request,
            Map<String, Object> textFormat
    ) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", properties.model());
        body.put("instructions", systemPrompt(request));
        body.put("input", prompt(request));
        body.put("max_output_tokens", maxTokens(request));
        addTemperature(body, request);
        addResponseTuning(body, textFormat);
        body.put("store", properties.logInteractions());

        return post(endpoint, body, headers -> {
            if (properties.provider() == LlmProvider.AZURE_OPENAI) {
                headers.set("api-key", properties.apiKey());
            } else {
                headers.setBearerAuth(properties.apiKey());
            }
        });
    }

    private JsonNode postOpenAiCompatible(URI endpoint, LlmCompletionRequest request) {
        return postOpenAiCompatible(endpoint, request, null);
    }

    private JsonNode postOpenAiCompatible(
            URI endpoint,
            LlmCompletionRequest request,
            Map<String, Object> responseFormat
    ) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", properties.model());
        body.put("messages", chatMessages(request));
        body.put("max_tokens", maxTokens(request));
        addTemperature(body, request);
        addChatCompletionsTuning(body);
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
        addTemperature(body, request);
        body.put("system", systemPrompt(request));
        body.put("messages", List.of(Map.of(
                "role", "user",
                "content", prompt(request)
        )));
        addAnthropicTuning(body, new LinkedHashMap<>());

        return post(endpoint, body, headers -> {
            headers.set("x-api-key", properties.apiKey());
            headers.set("anthropic-version", ANTHROPIC_VERSION);
        });
    }

    private JsonNode postAnthropicStructured(URI endpoint, LlmCompletionRequest request, JsonNode schema) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", properties.model());
        body.put("max_tokens", maxTokens(request));
        addTemperature(body, request);
        body.put("system", systemPrompt(request));
        body.put("messages", List.of(Map.of("role", "user", "content", prompt(request))));
        Map<String, Object> outputConfig = new LinkedHashMap<>();
        outputConfig.put("format", Map.of("type", "json_schema", "schema", schema));
        addAnthropicTuning(body, outputConfig);

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
        addInteractionLogging(body);
        Map<String, Object> generationConfig = new LinkedHashMap<>();
        generationConfig.put("maxOutputTokens", maxTokens(request));
        addTemperature(generationConfig, request);
        addGoogleTuning(generationConfig);
        body.put("generationConfig", generationConfig);

        return post(endpoint, body, headers -> headers.set("x-goog-api-key", properties.apiKey()));
    }

    private JsonNode postGoogleStructured(URI endpoint, LlmCompletionRequest request, JsonNode schema) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("systemInstruction", Map.of("parts", List.of(Map.of("text", systemPrompt(request)))));
        body.put("contents", List.of(Map.of(
                "role", "user",
                "parts", List.of(Map.of("text", prompt(request)))
        )));
        addInteractionLogging(body);
        Map<String, Object> generationConfig = new LinkedHashMap<>();
        generationConfig.put("maxOutputTokens", maxTokens(request));
        addTemperature(generationConfig, request);
        generationConfig.put("responseMimeType", "application/json");
        generationConfig.put("responseJsonSchema", schema);
        addGoogleTuning(generationConfig);
        body.put("generationConfig", generationConfig);

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
        addTemperature(body, request);

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
        addTemperature(body, request);
        body.put("response_format", Map.of("type", "json_object", "schema", schema));

        return post(endpoint, body, headers -> headers.setBearerAuth(properties.apiKey()));
    }

    private JsonNode postOllama(URI endpoint, LlmCompletionRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", properties.model());
        body.put("messages", chatMessages(request));
        body.put("stream", false);
        Map<String, Object> options = new LinkedHashMap<>();
        options.put("num_predict", maxTokens(request));
        addTemperature(options, request);
        body.put("options", options);
        addOllamaTuning(body);

        return post(endpoint, body, headers -> {
        });
    }

    private JsonNode postOllamaStructured(URI endpoint, LlmCompletionRequest request, JsonNode schema) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", properties.model());
        body.put("messages", chatMessages(request));
        body.put("stream", false);
        body.put("format", schema);
        Map<String, Object> options = new LinkedHashMap<>();
        options.put("num_predict", maxTokens(request));
        addTemperature(options, request);
        body.put("options", options);
        addOllamaTuning(body);

        return post(endpoint, body, headers -> {
        });
    }

    private void addInteractionLogging(Map<String, Object> body) {
        if (!properties.logInteractions()) {
            return;
        }
        switch (properties.provider()) {
            case OPENAI, AZURE_OPENAI, GOOGLE -> body.put("store", true);
            case OPENAI_COMPATIBLE, ANTHROPIC, MISTRAL, COHERE, OLLAMA -> {
                // These providers do not expose a compatible per-request interaction logging flag.
            }
        }
    }

    private void addResponseTuning(Map<String, Object> body, Map<String, Object> textFormat) {
        ReasoningEffort effort = properties.reasoningEffort();
        if (effort != null) {
            if (properties.provider() == LlmProvider.AZURE_OPENAI) {
                requireSupportedReasoningEffort(effort, AZURE_REASONING_EFFORTS);
            }
            body.put("reasoning", Map.of("effort", effort.apiValue()));
        }

        Map<String, Object> text = new LinkedHashMap<>();
        if (textFormat != null) {
            text.put("format", textFormat);
        }
        if (properties.textVerbosity() != null) {
            text.put("verbosity", properties.textVerbosity().apiValue());
        }
        if (!text.isEmpty()) {
            body.put("text", text);
        }
    }

    private void addChatCompletionsTuning(Map<String, Object> body) {
        ReasoningEffort effort = properties.reasoningEffort();
        switch (properties.provider()) {
            case OPENAI_COMPATIBLE -> putReasoningEffort(body, "reasoning_effort", effort, null);
            case MISTRAL -> putReasoningEffort(
                    body, "reasoning_effort", effort, MISTRAL_REASONING_EFFORTS);
            case OPENAI, AZURE_OPENAI, ANTHROPIC, GOOGLE, COHERE, OLLAMA -> {
                // These providers are handled by their native request builders.
            }
        }

        if (properties.textVerbosity() != null) {
            switch (properties.provider()) {
                case OPENAI_COMPATIBLE ->
                        body.put("verbosity", properties.textVerbosity().apiValue());
                case OPENAI, AZURE_OPENAI, ANTHROPIC, GOOGLE, MISTRAL, COHERE, OLLAMA -> {
                    // No separate text verbosity request field is available.
                }
            }
        }
    }

    private void addAnthropicTuning(Map<String, Object> body, Map<String, Object> outputConfig) {
        putReasoningEffort(
                outputConfig, "effort", properties.reasoningEffort(), ANTHROPIC_REASONING_EFFORTS);
        if (!outputConfig.isEmpty()) {
            body.put("output_config", outputConfig);
        }
    }

    private void addGoogleTuning(Map<String, Object> generationConfig) {
        ReasoningEffort effort = properties.reasoningEffort();
        if (effort == null) {
            return;
        }
        requireSupportedReasoningEffort(effort, GOOGLE_REASONING_EFFORTS);
        generationConfig.put("thinkingConfig", Map.of("thinkingLevel", effort.apiValue()));
    }

    private void addOllamaTuning(Map<String, Object> body) {
        ReasoningEffort effort = properties.reasoningEffort();
        if (effort == null) {
            return;
        }
        requireSupportedReasoningEffort(effort, OLLAMA_REASONING_EFFORTS);
        body.put("think", effort == ReasoningEffort.NONE ? false : effort.apiValue());
    }

    private void putReasoningEffort(
            Map<String, Object> target,
            String field,
            ReasoningEffort effort,
            Set<ReasoningEffort> supported
    ) {
        if (effort == null) {
            return;
        }
        if (supported != null) {
            requireSupportedReasoningEffort(effort, supported);
        }
        target.put(field, effort.apiValue());
    }

    private void requireSupportedReasoningEffort(
            ReasoningEffort effort,
            Set<ReasoningEffort> supported
    ) {
        if (!supported.contains(effort)) {
            throw new LlmException("Reasoning effort " + effort.apiValue()
                    + " is not supported by provider " + properties.provider()
                    + "; supported values are "
                    + supported.stream().map(ReasoningEffort::apiValue).toList());
        }
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
            case OPENAI, AZURE_OPENAI -> responseOutputText(response);
            case OPENAI_COMPATIBLE -> response.at("/choices/0/message/content");
            case MISTRAL -> mistralTextContent(response.at("/choices/0/message/content"));
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

    private JsonNode mistralTextContent(JsonNode content) {
        if (!content.isArray()) {
            return content;
        }
        for (JsonNode chunk : content) {
            if ("text".equals(chunk.path("type").asText()) && chunk.path("text").isTextual()) {
                return chunk.path("text");
            }
        }
        return content;
    }

    private JsonNode responseOutputText(JsonNode response) {
        for (JsonNode item : response.path("output")) {
            if (!"message".equals(item.path("type").asText())) {
                continue;
            }
            for (JsonNode part : item.path("content")) {
                if ("output_text".equals(part.path("type").asText()) && part.path("text").isTextual()) {
                    return part.path("text");
                }
            }
        }
        return MissingNode.getInstance();
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
            case OPENAI, AZURE_OPENAI -> ensureResponseCompleted(response);
            case OPENAI_COMPATIBLE, MISTRAL -> {
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

    private void ensureResponseCompleted(JsonNode response) {
        for (JsonNode item : response.path("output")) {
            for (JsonNode part : item.path("content")) {
                if ("refusal".equals(part.path("type").asText()) && part.path("refusal").isTextual()) {
                    throw new LlmException("LLM provider refused the structured request: "
                            + part.path("refusal").asText());
                }
            }
        }
        JsonNode status = response.path("status");
        if (status.isTextual() && !"completed".equalsIgnoreCase(status.asText())) {
            JsonNode reason = response.at("/incomplete_details/reason");
            throw new LlmException("LLM provider did not complete the structured response: "
                    + (reason.isTextual() ? reason.asText() : status.asText()));
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
            case OPENAI, AZURE_OPENAI -> response.at("/usage/output_tokens");
            case OPENAI_COMPATIBLE, MISTRAL -> response.at("/usage/completion_tokens");
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
        String configuredPrompt = request.systemPrompt() == null || request.systemPrompt().isBlank()
                ? DEFAULT_SYSTEM_PROMPT
                : request.systemPrompt();
        return configuredPrompt + "\n\n" + LlmRequestSupport.CLOSED_WORLD_INSTRUCTION;
    }

    private int maxTokens(LlmCompletionRequest request) {
        return request.maxTokens() == null ? properties.maxTokens() : request.maxTokens();
    }

    private void addTemperature(Map<String, Object> target, LlmCompletionRequest request) {
        Double temperature = temperature(request);
        if (temperature != null) {
            target.put("temperature", temperature);
        }
    }

    private Double temperature(LlmCompletionRequest request) {
        return request.temperature() == null ? properties.temperature() : request.temperature();
    }

    private void validateConfiguration() {
        if (properties.provider() != LlmProvider.OLLAMA
                && (properties.apiKey() == null || properties.apiKey().isBlank())) {
            throw new LlmException("LLM API key is not configured for provider " + properties.provider());
        }
        String model = properties.model().toLowerCase(Locale.ROOT);
        if (model.contains("search-preview") || model.contains("deep-research")) {
            throw new LlmException("Model " + properties.model()
                    + " has intrinsic external-retrieval capabilities and cannot be used for closed-world requests");
        }
    }

    @FunctionalInterface
    private interface HeaderCustomizer {
        void customize(HttpHeaders headers);
    }
}
