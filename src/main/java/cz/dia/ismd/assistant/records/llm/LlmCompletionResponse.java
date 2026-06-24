package cz.dia.ismd.assistant.records.llm;

import com.fasterxml.jackson.databind.JsonNode;
import cz.dia.ismd.assistant.service.llm.LlmProvider;

public record LlmCompletionResponse(
        LlmProvider provider,
        String model,
        String endpointUrl,
        String content,
        JsonNode usage
) {
}
