package cz.dia.ismd.assistant.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.node.ObjectNode;
import cz.dia.ismd.assistant.config.LlmProperties;
import cz.dia.ismd.assistant.config.LlmProperties.ReasoningEffort;
import cz.dia.ismd.assistant.config.LlmProperties.TextVerbosity;
import cz.dia.ismd.assistant.exception.LlmException;
import cz.dia.ismd.assistant.exception.TokenLimitReachedException;
import cz.dia.ismd.assistant.model.llm.ClassSuggestionLlmResponse;
import cz.dia.ismd.assistant.model.llm.LlmCompletionRequest;
import cz.dia.ismd.assistant.model.llm.LlmCompletionResponse;
import cz.dia.ismd.assistant.model.llm.LlmProvider;
import cz.dia.ismd.assistant.model.suggestion.classsuggestion.ClassSuggestion;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class LlmClientStructuredOutputTests {

    private static final String STRUCTURED_CONTENT = """
            {
              "suggestions": [{
                "suggestion_id": "class_001",
                "name": {"cs": "Osoba"},
                "definition": {"cs": "Fyzická osoba."},
                "explanation": {"cs": "Pojem nalezený v právním textu."},
                "type": "CLASS",
                "specializes": [],
                "legal_act": "/eli/cz/sb/2024/1"
              }]
            }
            """;

    @Test
    void streamsEachCompleteSuggestionBeforeTheTerminalEvent() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper()
                .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        URI endpoint = URI.create("https://llm.test/responses");
        LlmProperties properties = new LlmProperties(
                true, LlmProvider.OPENAI, endpoint, "test-model", "test-key",
                512, null, null, null, Duration.ofSeconds(5), false);
        TokenUsageService tokenUsageService = mock(TokenUsageService.class);
        LlmClient client = new LlmClient(builder.build(), properties, objectMapper, tokenUsageService);
        JsonNode schema = objectMapper.readTree("""
                {"type":"object","properties":{"suggestions":{"type":"array"}},"required":["suggestions"]}
                """);
        String first = STRUCTURED_CONTENT.substring(0, STRUCTURED_CONTENT.indexOf("}]") + 1);
        String second = STRUCTURED_CONTENT.substring(STRUCTURED_CONTENT.indexOf("}]") + 1);
        String events = "data: " + objectMapper.writeValueAsString(objectMapper.createObjectNode()
                .put("type", "response.output_text.delta").put("delta", first)) + "\n\n"
                + "data: " + objectMapper.writeValueAsString(objectMapper.createObjectNode()
                .put("type", "response.output_text.delta").put("delta", second)) + "\n\n"
                + "data: {\"type\":\"response.completed\",\"response\":{\"usage\":{\"output_tokens\":17}}}\n\n";
        server.expect(requestTo(endpoint))
                .andExpect(jsonPath("$.stream").value(true))
                .andRespond(withSuccess(events, MediaType.TEXT_EVENT_STREAM));
        List<ClassSuggestion> published = new ArrayList<>();

        ClassSuggestionLlmResponse response = client.completeStructuredStreaming(
                "test-user", new LlmCompletionRequest("Return JSON.", "Suggest classes.", null, null),
                "class_suggestions", schema, ClassSuggestionLlmResponse.class,
                cz.dia.ismd.assistant.model.suggestion.classsuggestion.ClassSuggestion.class, published::add);

        assertThat(published).hasSize(1);
        assertThat(response.suggestions()).containsExactlyElementsOf(published);
        verify(tokenUsageService).addOutputTokens("test-user", 17);
        server.verify();
    }

    @Test
    void reportsOpenAiIncompleteStreamReasonAndStillBillsReportedUsage() throws Exception {
        String events = "data: {\"type\":\"response.incomplete\",\"response\":{"
                + "\"incomplete_details\":{\"reason\":\"max_output_tokens\"},"
                + "\"usage\":{\"output_tokens\":41}}}\n\n";

        assertRejectedStructuredStream(LlmProvider.OPENAI, events, 41, "max_output_tokens");
    }

    @Test
    void rejectsCohereMaxTokensStreamAndStillBillsReportedUsage() throws Exception {
        String events = "data: {\"type\":\"content-delta\",\"delta\":{\"message\":{\"content\":{\"text\":"
                + new ObjectMapper().writeValueAsString(STRUCTURED_CONTENT)
                + "}}}}\n\n"
                + "data: {\"type\":\"message-end\",\"delta\":{\"finish_reason\":\"MAX_TOKENS\","
                + "\"usage\":{\"tokens\":{\"output_tokens\":23}}}}\n\n";

        assertRejectedStructuredStream(LlmProvider.COHERE, events, 23, "MAX_TOKENS");
    }

    @Test
    void acceptsCohereCompleteStream() throws Exception {
        String events = "data: {\"type\":\"content-delta\",\"delta\":{\"message\":{\"content\":{\"text\":"
                + new ObjectMapper().writeValueAsString(STRUCTURED_CONTENT)
                + "}}}}\n\n"
                + "data: {\"type\":\"message-end\",\"delta\":{\"finish_reason\":\"COMPLETE\","
                + "\"usage\":{\"tokens\":{\"output_tokens\":19}}}}\n\n";

        assertAcceptedStructuredStream(LlmProvider.COHERE, events, 19);
    }

    @ParameterizedTest
    @CsvSource({"length, 29", "unload, 30"})
    void rejectsOllamaNonStopStreamAndStillBillsReportedUsage(
            String doneReason,
            int outputTokens
    ) throws Exception {
        String events = "{\"message\":{\"content\":"
                + new ObjectMapper().writeValueAsString(STRUCTURED_CONTENT)
                + "},\"done\":false}\n"
                + "{\"message\":{\"content\":\"\"},\"done\":true,"
                + "\"done_reason\":\"" + doneReason + "\",\"eval_count\":" + outputTokens + "}\n";

        assertRejectedStructuredStream(LlmProvider.OLLAMA, events, outputTokens, doneReason);
    }

    @Test
    void acceptsOllamaStopStream() throws Exception {
        String events = "{\"message\":{\"content\":"
                + new ObjectMapper().writeValueAsString(STRUCTURED_CONTENT)
                + "},\"done\":false}\n"
                + "{\"message\":{\"content\":\"\"},\"done\":true,"
                + "\"done_reason\":\"stop\",\"eval_count\":31}\n";

        assertAcceptedStructuredStream(LlmProvider.OLLAMA, events, 31);
    }

    @ParameterizedTest
    @EnumSource(LlmProvider.class)
    void sendsProviderSpecificSchemaAndReturnsTypedSuggestions(LlmProvider provider) throws Exception {
        ObjectMapper objectMapper = new ObjectMapper()
                .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        URI endpoint = URI.create("https://llm.test/completions");
        LlmProperties properties = new LlmProperties(
                true,
                provider,
                endpoint,
                "test-model",
                "test-key",
                512,
                0.1,
                ReasoningEffort.MEDIUM,
                TextVerbosity.LOW,
                Duration.ofSeconds(5),
                true
        );
        TokenUsageService tokenUsageService = mock(TokenUsageService.class);
        LlmClient client = new LlmClient(builder.build(), properties, objectMapper, tokenUsageService);
        JsonNode schema = objectMapper.readTree("""
                {
                  "type": "object",
                  "properties": {"suggestions": {"type": "array"}},
                  "required": ["suggestions"]
                }
                """);

        server.expect(requestTo(endpoint))
                .andExpect(jsonPath(schemaPath(provider)).exists())
                .andExpect(maxTokensExpectation(provider))
                .andExpect(jsonPath(temperaturePath(provider)).value(0.1))
                .andExpect(interactionLoggingExpectation(provider))
                .andExpect(reasoningEffortExpectation(provider))
                .andExpect(textVerbosityExpectation(provider))
                .andExpect(externalAccessDisabledExpectation(provider))
                .andRespond(withSuccess(providerResponse(provider, objectMapper), MediaType.APPLICATION_JSON));

        ClassSuggestionLlmResponse response = client.completeStructured(
                "test-user",
                new LlmCompletionRequest("Return JSON.", "Suggest classes.", null, null),
                "class_suggestions",
                schema,
                ClassSuggestionLlmResponse.class
        );

        assertThat(response.suggestions()).hasSize(1);
        assertThat(response.suggestions().get(0).suggestionID()).isEqualTo("class_001");
        assertThat(response.suggestions().get(0).name().values()).containsEntry("cs", "Osoba");
        verify(tokenUsageService).ensureRequestAllowed("test-user");
        verify(tokenUsageService).addOutputTokens("test-user", 17);
        server.verify();
    }

    @ParameterizedTest
    @EnumSource(LlmProvider.class)
    void omitsTemperatureWhenNeitherRequestNorConfigurationProvidesIt(LlmProvider provider) throws Exception {
        ObjectMapper objectMapper = new ObjectMapper()
                .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        URI endpoint = URI.create("https://llm.test/completions");
        LlmProperties properties = new LlmProperties(
                true,
                provider,
                endpoint,
                "test-model",
                "test-key",
                512,
                null,
                null,
                null,
                Duration.ofSeconds(5),
                false
        );
        LlmClient client = new LlmClient(
                builder.build(), properties, objectMapper, mock(TokenUsageService.class));
        JsonNode schema = objectMapper.readTree("""
                {
                  "type": "object",
                  "properties": {"suggestions": {"type": "array"}},
                  "required": ["suggestions"]
                }
                """);

        server.expect(requestTo(endpoint))
                .andExpect(jsonPath(temperaturePath(provider)).doesNotExist())
                .andExpect(interactionLoggingDisabledExpectation(provider))
                .andRespond(withSuccess(providerResponse(provider, objectMapper), MediaType.APPLICATION_JSON));

        client.completeStructured(
                "test-user",
                new LlmCompletionRequest("Return JSON.", "Suggest classes.", null, null),
                "class_suggestions",
                schema,
                ClassSuggestionLlmResponse.class
        );

        server.verify();
    }

    @Test
    void rejectsLimitedUserBeforeSendingProviderRequest() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        LlmProperties properties = new LlmProperties(
                true,
                LlmProvider.OPENAI,
                URI.create("https://llm.test/completions"),
                "test-model",
                "test-key",
                512,
                0.1,
                null,
                null,
                Duration.ofSeconds(5),
                false
        );
        TokenUsageService tokenUsageService = mock(TokenUsageService.class);
        doThrow(new TokenLimitReachedException("limited-user", 100))
                .when(tokenUsageService).ensureRequestAllowed("limited-user");
        LlmClient client = new LlmClient(builder.build(), properties, objectMapper, tokenUsageService);

        assertThatThrownBy(() -> client.complete(
                "limited-user",
                new LlmCompletionRequest("System", "Prompt", null, null)
        )).isInstanceOf(TokenLimitReachedException.class);

        server.verify();
        verify(tokenUsageService).ensureRequestAllowed("limited-user");
    }

    @Test
    void readsTextAndUsageFromOpenAiResponse() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        URI endpoint = URI.create("https://llm.test/responses");
        LlmProperties properties = new LlmProperties(
                true, LlmProvider.OPENAI, endpoint, "test-model", "test-key",
                512, null, null, null, Duration.ofSeconds(5), false);
        TokenUsageService tokenUsageService = mock(TokenUsageService.class);
        LlmClient client = new LlmClient(builder.build(), properties, objectMapper, tokenUsageService);

        ObjectNode response = objectMapper.createObjectNode();
        response.put("status", "completed");
        response.set("output", objectMapper.createArrayNode().add(objectMapper.createObjectNode()
                .put("type", "reasoning")).add(objectMapper.createObjectNode()
                .put("type", "message")
                .set("content", objectMapper.createArrayNode().add(objectMapper.createObjectNode()
                        .put("type", "output_text")
                        .put("text", "Generated text")))));
        response.set("usage", objectMapper.createObjectNode()
                .put("input_tokens", 11)
                .put("output_tokens", 7));

        server.expect(requestTo(endpoint))
                .andExpect(jsonPath("$.instructions", containsString("Do not use the internet")))
                .andExpect(jsonPath("$.input").value("Prompt"))
                .andExpect(jsonPath("$.max_output_tokens").value(512))
                .andExpect(jsonPath("$.store").value(false))
                .andExpect(jsonPath("$.messages").doesNotExist())
                .andRespond(withSuccess(objectMapper.writeValueAsString(response), MediaType.APPLICATION_JSON));

        LlmCompletionResponse completion = client.complete(
                "test-user", new LlmCompletionRequest("System", "Prompt", null, null));

        assertThat(completion.content()).isEqualTo("Generated text");
        assertThat(completion.usage().path("output_tokens").asInt()).isEqualTo(7);
        verify(tokenUsageService).addOutputTokens("test-user", 7);
        server.verify();
    }

    @ParameterizedTest
    @CsvSource({
            "OPENAI, gpt-4o-search-preview",
            "GOOGLE, deep-research-preview-04-2026"
    })
    void rejectsModelsWithIntrinsicExternalRetrieval(LlmProvider provider, String model) {
        ObjectMapper objectMapper = new ObjectMapper();
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        LlmProperties properties = new LlmProperties(
                true,
                provider,
                URI.create("https://llm.test/completions"),
                model,
                "test-key",
                512,
                null,
                null,
                null,
                Duration.ofSeconds(5),
                false
        );
        LlmClient client = new LlmClient(
                builder.build(), properties, objectMapper, mock(TokenUsageService.class));

        assertThatThrownBy(() -> client.complete(
                "test-user", new LlmCompletionRequest("System", "Prompt", null, null)))
                .isInstanceOf(LlmException.class)
                .hasMessageContaining("intrinsic external-retrieval capabilities");
        server.verify();
    }

    private void assertRejectedStructuredStream(
            LlmProvider provider,
            String events,
            int expectedOutputTokens,
            String expectedFailureReason
    ) throws Exception {
        ObjectMapper objectMapper = new ObjectMapper()
                .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        URI endpoint = URI.create("https://llm.test/stream");
        LlmProperties properties = new LlmProperties(
                true, provider, endpoint, "test-model", "test-key",
                512, null, null, null, Duration.ofSeconds(5), false);
        TokenUsageService tokenUsageService = mock(TokenUsageService.class);
        LlmClient client = new LlmClient(builder.build(), properties, objectMapper, tokenUsageService);
        JsonNode schema = objectMapper.readTree("""
                {"type":"object","properties":{"suggestions":{"type":"array"}},"required":["suggestions"]}
                """);
        server.expect(requestTo(endpoint))
                .andRespond(withSuccess(events, MediaType.TEXT_EVENT_STREAM));

        assertThatThrownBy(() -> client.completeStructuredStreaming(
                "test-user", new LlmCompletionRequest("Return JSON.", "Suggest classes.", null, null),
                "class_suggestions", schema, ClassSuggestionLlmResponse.class,
                ClassSuggestion.class, ignored -> { }))
                .isInstanceOf(LlmException.class)
                .hasMessageContaining("before completing")
                .hasMessageContaining("provider=" + provider)
                .hasMessageContaining("reason=" + expectedFailureReason);

        verify(tokenUsageService).addOutputTokens("test-user", expectedOutputTokens);
        server.verify();
    }

    private void assertAcceptedStructuredStream(
            LlmProvider provider,
            String events,
            int expectedOutputTokens
    ) throws Exception {
        ObjectMapper objectMapper = new ObjectMapper()
                .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        URI endpoint = URI.create("https://llm.test/stream");
        LlmProperties properties = new LlmProperties(
                true, provider, endpoint, "test-model", "test-key",
                512, null, null, null, Duration.ofSeconds(5), false);
        TokenUsageService tokenUsageService = mock(TokenUsageService.class);
        LlmClient client = new LlmClient(builder.build(), properties, objectMapper, tokenUsageService);
        JsonNode schema = objectMapper.readTree("""
                {"type":"object","properties":{"suggestions":{"type":"array"}},"required":["suggestions"]}
                """);
        server.expect(requestTo(endpoint))
                .andRespond(withSuccess(events, MediaType.TEXT_EVENT_STREAM));

        ClassSuggestionLlmResponse response = client.completeStructuredStreaming(
                "test-user", new LlmCompletionRequest("Return JSON.", "Suggest classes.", null, null),
                "class_suggestions", schema, ClassSuggestionLlmResponse.class,
                ClassSuggestion.class, ignored -> { });

        assertThat(response.suggestions()).hasSize(1);
        verify(tokenUsageService).addOutputTokens("test-user", expectedOutputTokens);
        server.verify();
    }

    private String schemaPath(LlmProvider provider) {
        return switch (provider) {
            case OPENAI, AZURE_OPENAI -> "$.text.format.schema";
            case OPENAI_COMPATIBLE, MISTRAL ->
                    "$.response_format.json_schema.schema";
            case ANTHROPIC -> "$.output_config.format.schema";
            case GOOGLE -> "$.generationConfig.responseJsonSchema";
            case COHERE -> "$.response_format.schema";
            case OLLAMA -> "$.format";
        };
    }

    private org.springframework.test.web.client.RequestMatcher interactionLoggingExpectation(LlmProvider provider) {
        return switch (provider) {
            case OPENAI, AZURE_OPENAI, GOOGLE -> jsonPath("$.store").value(true);
            case OPENAI_COMPATIBLE, ANTHROPIC, MISTRAL, COHERE, OLLAMA -> jsonPath("$.store").doesNotExist();
        };
    }

    private org.springframework.test.web.client.RequestMatcher interactionLoggingDisabledExpectation(
            LlmProvider provider
    ) {
        return switch (provider) {
            case OPENAI, AZURE_OPENAI -> jsonPath("$.store").value(false);
            case OPENAI_COMPATIBLE, ANTHROPIC, GOOGLE, MISTRAL, COHERE, OLLAMA ->
                    jsonPath("$.store").doesNotExist();
        };
    }

    private org.springframework.test.web.client.RequestMatcher maxTokensExpectation(LlmProvider provider) {
        return switch (provider) {
            case OPENAI, AZURE_OPENAI -> request -> {
                jsonPath("$.max_output_tokens").value(512).match(request);
                jsonPath("$.max_completion_tokens").doesNotExist().match(request);
                jsonPath("$.max_tokens").doesNotExist().match(request);
            };
            case OPENAI_COMPATIBLE, MISTRAL, ANTHROPIC, COHERE ->
                    jsonPath("$.max_tokens").value(512);
            case GOOGLE -> jsonPath("$.generationConfig.maxOutputTokens").value(512);
            case OLLAMA -> jsonPath("$.options.num_predict").value(512);
        };
    }

    private String temperaturePath(LlmProvider provider) {
        return switch (provider) {
            case GOOGLE -> "$.generationConfig.temperature";
            case OLLAMA -> "$.options.temperature";
            case OPENAI, OPENAI_COMPATIBLE, AZURE_OPENAI, ANTHROPIC, MISTRAL, COHERE -> "$.temperature";
        };
    }

    private org.springframework.test.web.client.RequestMatcher reasoningEffortExpectation(LlmProvider provider) {
        return switch (provider) {
            case OPENAI, AZURE_OPENAI -> jsonPath("$.reasoning.effort").value("medium");
            case OPENAI_COMPATIBLE, MISTRAL ->
                    jsonPath("$.reasoning_effort").value("medium");
            case ANTHROPIC -> jsonPath("$.output_config.effort").value("medium");
            case GOOGLE -> jsonPath("$.generationConfig.thinkingConfig.thinkingLevel").value("medium");
            case OLLAMA -> jsonPath("$.think").value("medium");
            case COHERE -> jsonPath("$.reasoning_effort").doesNotExist();
        };
    }

    private org.springframework.test.web.client.RequestMatcher textVerbosityExpectation(LlmProvider provider) {
        return switch (provider) {
            case OPENAI, AZURE_OPENAI -> jsonPath("$.text.verbosity").value("low");
            case OPENAI_COMPATIBLE -> jsonPath("$.verbosity").value("low");
            case ANTHROPIC, GOOGLE, MISTRAL, COHERE, OLLAMA -> jsonPath("$.verbosity").doesNotExist();
        };
    }

    private org.springframework.test.web.client.RequestMatcher externalAccessDisabledExpectation(
            LlmProvider provider
    ) {
        return request -> {
            switch (provider) {
                case OPENAI, AZURE_OPENAI -> {
                    jsonPath("$.tool_choice").doesNotExist().match(request);
                    jsonPath("$.tools").doesNotExist().match(request);
                    jsonPath("$.instructions", containsString("Work only with information explicitly included"))
                            .match(request);
                    jsonPath("$.input").value("Suggest classes.").match(request);
                    jsonPath("$.messages").doesNotExist().match(request);
                }
                case OPENAI_COMPATIBLE, MISTRAL -> {
                    jsonPath("$.tool_choice").doesNotExist().match(request);
                    jsonPath("$.tools").doesNotExist().match(request);
                    jsonPath("$.messages[0].content", containsString("Work only with information explicitly included"))
                            .match(request);
                }
                case ANTHROPIC -> {
                    jsonPath("$.tools").doesNotExist().match(request);
                    jsonPath("$.system", containsString("Do not use the internet"))
                            .match(request);
                }
                case GOOGLE -> {
                    jsonPath("$.tools").doesNotExist().match(request);
                    jsonPath("$.systemInstruction.parts[0].text", containsString("Do not use the internet"))
                            .match(request);
                }
                case COHERE -> {
                    jsonPath("$.tool_choice").doesNotExist().match(request);
                    jsonPath("$.tools").doesNotExist().match(request);
                    jsonPath("$.messages[0].content", containsString("Do not use the internet"))
                            .match(request);
                }
                case OLLAMA -> {
                    jsonPath("$.tools").doesNotExist().match(request);
                    jsonPath("$.messages[0].content", containsString("Do not use the internet"))
                            .match(request);
                }
            }
        };
    }

    private String providerResponse(LlmProvider provider, ObjectMapper objectMapper) throws Exception {
        ObjectNode response = objectMapper.createObjectNode();
        switch (provider) {
            case OPENAI, AZURE_OPENAI -> {
                response.put("status", "completed");
                response.set("output", objectMapper.createArrayNode().add(objectMapper.createObjectNode()
                        .put("type", "message")
                        .set("content", objectMapper.createArrayNode().add(objectMapper.createObjectNode()
                                .put("type", "output_text")
                                .put("text", STRUCTURED_CONTENT)))));
                response.set("usage", objectMapper.createObjectNode().put("output_tokens", 17));
            }
            case OPENAI_COMPATIBLE -> {
                response.set("choices", objectMapper.createArrayNode().add(objectMapper.createObjectNode()
                        .put("finish_reason", "stop")
                        .set("message", objectMapper.createObjectNode().put("content", STRUCTURED_CONTENT))));
                response.set("usage", objectMapper.createObjectNode().put("completion_tokens", 17));
            }
            case MISTRAL -> {
                response.set("choices", objectMapper.createArrayNode().add(objectMapper.createObjectNode()
                        .put("finish_reason", "stop")
                        .set("message", objectMapper.createObjectNode().set("content",
                                objectMapper.createArrayNode()
                                        .add(objectMapper.createObjectNode()
                                                .put("type", "thinking")
                                                .set("thinking", objectMapper.createArrayNode()))
                                        .add(objectMapper.createObjectNode()
                                                .put("type", "text")
                                                .put("text", STRUCTURED_CONTENT))))));
                response.set("usage", objectMapper.createObjectNode().put("completion_tokens", 17));
            }
            case ANTHROPIC -> {
                response.put("stop_reason", "end_turn");
                response.set("content", objectMapper.createArrayNode().add(
                        objectMapper.createObjectNode().put("text", STRUCTURED_CONTENT)));
                response.set("usage", objectMapper.createObjectNode().put("output_tokens", 17));
            }
            case GOOGLE -> {
                response.set("candidates", objectMapper.createArrayNode().add(objectMapper.createObjectNode()
                        .put("finishReason", "STOP")
                        .set("content", objectMapper.createObjectNode().set("parts",
                                objectMapper.createArrayNode().add(
                                        objectMapper.createObjectNode().put("text", STRUCTURED_CONTENT))))));
                response.set("usageMetadata", objectMapper.createObjectNode().put("candidatesTokenCount", 17));
            }
            case COHERE -> {
                response.put("finish_reason", "COMPLETE");
                response.set("message", objectMapper.createObjectNode().set("content",
                        objectMapper.createArrayNode().add(
                                objectMapper.createObjectNode().put("text", STRUCTURED_CONTENT))));
                response.set("usage", objectMapper.createObjectNode().set("tokens",
                        objectMapper.createObjectNode().put("output_tokens", 17)));
            }
            case OLLAMA -> {
                response.put("done", true);
                response.put("done_reason", "stop");
                response.put("eval_count", 17);
                response.set("message", objectMapper.createObjectNode().put("content", STRUCTURED_CONTENT));
            }
        }
        return objectMapper.writeValueAsString(response);
    }
}
