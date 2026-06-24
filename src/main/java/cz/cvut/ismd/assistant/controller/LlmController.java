package cz.cvut.ismd.assistant.controller;

import cz.cvut.ismd.assistant.controller.dto.LlmRequest;
import cz.cvut.ismd.assistant.controller.dto.LlmResponse;
import cz.cvut.ismd.assistant.service.llm.LlmClient;
import cz.cvut.ismd.assistant.service.llm.LlmCompletionRequest;
import cz.cvut.ismd.assistant.service.llm.LlmCompletionResponse;
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
