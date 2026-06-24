package cz.dia.ismd.assistant.records.llm;

public record LlmCompletionRequest(
        String systemPrompt,
        String prompt,
        Integer maxTokens,
        Double temperature
) {
}
