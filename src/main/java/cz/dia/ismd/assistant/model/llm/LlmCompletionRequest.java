package cz.dia.ismd.assistant.model.llm;

public record LlmCompletionRequest(
        String systemPrompt,
        String prompt,
        Integer maxTokens,
        Double temperature
) {
}
