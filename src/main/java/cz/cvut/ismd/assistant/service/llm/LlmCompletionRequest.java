package cz.cvut.ismd.assistant.service.llm;

public record LlmCompletionRequest(
        String systemPrompt,
        String prompt,
        Integer maxTokens,
        Double temperature
) {
}
