package cz.dia.ismd.assistant.controller.llm;

import cz.dia.ismd.assistant.dto.llm.LlmRequest;
import cz.dia.ismd.assistant.dto.llm.LlmResponse;
import cz.dia.ismd.assistant.service.llm.LlmClient;
import cz.dia.ismd.assistant.records.llm.LlmCompletionRequest;
import cz.dia.ismd.assistant.records.llm.LlmCompletionResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class LlmController {

    private final LlmClient llmClient;

    public LlmController(LlmClient llmClient) {
        this.llmClient = llmClient;
    }

    @PostMapping("/llm")
    public LlmResponse complete(@Valid @RequestBody LlmRequest request) {
        LlmCompletionResponse response = llmClient.complete(new LlmCompletionRequest(
                request.systemPrompt(),
                request.prompt(),
                request.maxTokens(),
                request.temperature()
        ));
        return new LlmResponse(
                response.provider(),
                response.model(),
                response.endpointUrl(),
                response.content(),
                response.usage()
        );
    }
}
