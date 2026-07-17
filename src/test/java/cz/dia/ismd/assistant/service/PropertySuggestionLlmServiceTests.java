package cz.dia.ismd.assistant.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import cz.dia.ismd.assistant.api.suggestion.attribute.PropertySuggestionJobRequest;
import cz.dia.ismd.assistant.model.legal.LegalActText;
import cz.dia.ismd.assistant.model.llm.LlmCompletionRequest;
import cz.dia.ismd.assistant.model.llm.PropertySuggestionLlmResponse;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PropertySuggestionLlmServiceTests {

    @Test
    void sendsRequestAndLegalTextsUsingPropertyStructuredOutput() throws Exception {
        LlmClient llmClient = mock(LlmClient.class);
        ObjectMapper objectMapper = new ObjectMapper()
                .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
        PropertySuggestionLlmService service = new PropertySuggestionLlmService(llmClient, objectMapper);
        PropertySuggestionJobRequest request = new PropertySuggestionJobRequest(
                2, "class_001", List.of("/eli/cz/sb/2024/1/par_1"), "Osoby", null);
        LegalActText source = new LegalActText(
                10L, 20L, "2024/1/par_1", "Osoba má jméno.", "paragraph", "1");
        when(llmClient.completeStructured(
                eq("test-user"), any(), eq("property_suggestions"), any(),
                eq(PropertySuggestionLlmResponse.class)))
                .thenReturn(new PropertySuggestionLlmResponse(List.of()));

        service.suggestProperties("test-user", request, List.of(source));

        ArgumentCaptor<LlmCompletionRequest> completionRequest = ArgumentCaptor.forClass(LlmCompletionRequest.class);
        verify(llmClient).completeStructured(
                eq("test-user"), completionRequest.capture(), eq("property_suggestions"),
                any(), eq(PropertySuggestionLlmResponse.class));
        String promptJson = completionRequest.getValue().prompt().substring(
                completionRequest.getValue().prompt().indexOf('{'));
        var prompt = objectMapper.readTree(promptJson);
        assertThat(prompt.path("request").path("selected_class_id").asText()).isEqualTo("class_001");
        assertThat(prompt.path("legal_texts").get(0).path("legal_text").asText())
                .isEqualTo("Osoba má jméno.");
    }
}
