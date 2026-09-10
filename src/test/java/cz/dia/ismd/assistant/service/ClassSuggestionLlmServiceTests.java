package cz.dia.ismd.assistant.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import cz.dia.ismd.assistant.api.suggestion.classsuggestion.ClassSuggestionJobRequest;
import cz.dia.ismd.assistant.model.legal.LegalActText;
import cz.dia.ismd.assistant.model.llm.ClassSuggestionLlmResponse;
import cz.dia.ismd.assistant.model.llm.LlmCompletionRequest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ClassSuggestionLlmServiceTests {

    @Test
    void sendsLegalTextsToLlmWithTheirPaths() throws Exception {
        LlmClient llmClient = mock(LlmClient.class);
        ObjectMapper objectMapper = new ObjectMapper()
                .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
        ClassSuggestionLlmService service = new ClassSuggestionLlmService(llmClient, objectMapper);
        ClassSuggestionJobRequest request = new ClassSuggestionJobRequest(
                2, List.of("/eli/cz/sb/2024/1/2024-01-01/par_1"), "Osoby", null);
        LegalActText source = new LegalActText(
                10L,
                20L,
                "2024/1/2024-01-01/par_1",
                "Osobou se rozumí fyzická osoba.",
                "paragraph",
                "1"
        );
        when(llmClient.completeStructured(
                eq("test-user"), any(), eq("class_suggestions"), any(), eq(ClassSuggestionLlmResponse.class)))
                .thenReturn(new ClassSuggestionLlmResponse(List.of()));

        service.suggestClasses("test-user", request, List.of(source));

        ArgumentCaptor<LlmCompletionRequest> completionRequest = ArgumentCaptor.forClass(LlmCompletionRequest.class);
        ArgumentCaptor<JsonNode> schema = ArgumentCaptor.forClass(JsonNode.class);
        verify(llmClient).completeStructured(
                eq("test-user"), completionRequest.capture(), eq("class_suggestions"),
                schema.capture(), eq(ClassSuggestionLlmResponse.class));
        assertThat(schema.getValue().at("/properties/suggestions/maxItems").asInt()).isEqualTo(request.effectiveK());
        String promptJson = completionRequest.getValue().prompt().substring(
                completionRequest.getValue().prompt().indexOf('{'));
        var prompt = objectMapper.readTree(promptJson);
        assertThat(prompt.path("request").path("k").asInt()).isEqualTo(2);
        assertThat(prompt.path("legal_texts").get(0).path("path").asText())
                .isEqualTo("2024/1/2024-01-01/par_1");
        assertThat(prompt.path("legal_texts").get(0).path("legal_text").asText())
                .isEqualTo("Osobou se rozumí fyzická osoba.");
        assertThat(prompt.path("legal_texts").get(0).has("id")).isFalse();
        assertThat(prompt.path("legal_texts").get(0).has("legal_act_id")).isFalse();
    }
}
