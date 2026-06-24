package cz.cvut.ismd.assistant.controller.dto;

import com.fasterxml.jackson.databind.JsonNode;
import cz.cvut.ismd.assistant.service.llm.LlmProvider;

public record LlmResponse(
        LlmProvider provider,
        String model,
        String endpointUrl,
        String content,
        JsonNode usage
) {
}
