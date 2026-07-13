package cz.dia.ismd.assistant.model.llm;

import com.fasterxml.jackson.databind.JsonNode;

public record LlmCompletionResponse(
        LlmProvider provider,
        String model,
        String endpointUrl,
        String content,
        JsonNode usage
) {
}
