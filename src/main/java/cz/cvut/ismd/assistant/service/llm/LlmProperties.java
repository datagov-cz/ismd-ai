package cz.cvut.ismd.assistant.service.llm;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.time.Duration;

@ConfigurationProperties(prefix = "app.llm")
public record LlmProperties(
        boolean enabled,
        LlmProvider provider,
        URI endpointUrl,
        String model,
        String apiKey,
        Integer maxTokens,
        Double temperature,
        Duration timeout
) {
    public LlmProperties {
        provider = provider == null ? LlmProvider.OPENAI : provider;
        model = model == null || model.isBlank() ? "gpt-4o-mini" : model;
        maxTokens = maxTokens == null ? 1024 : maxTokens;
        temperature = temperature == null ? 0.2 : temperature;
        timeout = timeout == null ? Duration.ofSeconds(60) : timeout;
    }

    public URI effectiveEndpointUrl() {
        if (endpointUrl != null && !endpointUrl.toString().isBlank()) {
            return endpointUrl;
        }
        return URI.create(switch (provider) {
            case OPENAI, OPENAI_COMPATIBLE -> "https://api.openai.com/v1/chat/completions";
            case AZURE_OPENAI -> "https://example.openai.azure.com/openai/deployments/{model}/chat/completions?api-version=2024-02-15-preview";
            case ANTHROPIC -> "https://api.anthropic.com/v1/messages";
            case GOOGLE -> "https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent";
            case MISTRAL -> "https://api.mistral.ai/v1/chat/completions";
            case COHERE -> "https://api.cohere.com/v2/chat";
            case OLLAMA -> "http://localhost:11434/api/chat";
        });
    }
}
