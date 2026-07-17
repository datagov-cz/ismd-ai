package cz.dia.ismd.assistant.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class LlmPropertiesBindingTests {

    @Test
    void bindsLowercaseReasoningEffortAndTextVerbosity() {
        MapConfigurationPropertySource source = new MapConfigurationPropertySource(Map.of(
                "app.llm.reasoning-effort", "xhigh",
                "app.llm.text-verbosity", "low"
        ));

        LlmProperties properties = new Binder(source)
                .bind("app.llm", Bindable.of(LlmProperties.class))
                .get();

        assertThat(properties.reasoningEffort()).isEqualTo(LlmProperties.ReasoningEffort.XHIGH);
        assertThat(properties.textVerbosity()).isEqualTo(LlmProperties.TextVerbosity.LOW);
        assertThat(properties.temperature()).isNull();
    }

    @Test
    void usesResponsesApiEndpointsForOpenAiProviders() {
        LlmProperties openAi = bindProvider("OPENAI");
        LlmProperties azureOpenAi = bindProvider("AZURE_OPENAI");
        LlmProperties compatible = bindProvider("OPENAI_COMPATIBLE");

        assertThat(openAi.effectiveEndpointUrl().toString())
                .isEqualTo("https://api.openai.com/v1/responses");
        assertThat(azureOpenAi.effectiveEndpointUrl().toString())
                .isEqualTo("https://example.openai.azure.com/openai/v1/responses");
        assertThat(compatible.effectiveEndpointUrl().toString())
                .isEqualTo("https://api.openai.com/v1/chat/completions");
    }

    private LlmProperties bindProvider(String provider) {
        return new Binder(new MapConfigurationPropertySource(Map.of("app.llm.provider", provider)))
                .bind("app.llm", Bindable.of(LlmProperties.class))
                .get();
    }
}
