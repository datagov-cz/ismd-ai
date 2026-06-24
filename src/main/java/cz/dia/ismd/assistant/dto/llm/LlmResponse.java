package cz.dia.ismd.assistant.dto.llm;

import com.fasterxml.jackson.databind.JsonNode;
import cz.dia.ismd.assistant.service.llm.LlmProvider;

public record LlmResponse(
        LlmProvider provider,
        String model,
        String endpointUrl,
        String content,
        JsonNode usage
) {
}
