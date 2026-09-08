package cz.dia.ismd.assistant.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import cz.dia.ismd.assistant.api.suggestion.relationship.RelationshipSuggestionJobRequest;
import cz.dia.ismd.assistant.model.legal.LegalActText;
import cz.dia.ismd.assistant.model.llm.LlmCompletionRequest;
import cz.dia.ismd.assistant.model.llm.RelationshipSuggestionLlmResponse;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RelationshipSuggestionLlmServiceTests {

    @Test
    void sendsRequestAndLegalTextsUsingRelationshipStructuredOutput() throws Exception {
        LlmClient llmClient = mock(LlmClient.class);
        ObjectMapper objectMapper = new ObjectMapper()
                .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
        RelationshipSuggestionLlmService service = new RelationshipSuggestionLlmService(llmClient, objectMapper);
        RelationshipSuggestionJobRequest request = new RelationshipSuggestionJobRequest(
                2, "class_001", List.of("/eli/cz/sb/2024/1/par_1"), "Vlastnictví", null);
        LegalActText source = new LegalActText(
                10L, 20L, "2024/1/par_1", "Osoba vlastní věc.", "paragraph", "1");
        when(llmClient.completeStructured(
                eq("test-user"), any(), eq("relationship_suggestions"), any(),
                eq(RelationshipSuggestionLlmResponse.class)))
                .thenReturn(new RelationshipSuggestionLlmResponse(List.of()));

        service.suggestRelationships("test-user", request, List.of(source));

        ArgumentCaptor<LlmCompletionRequest> completionRequest = ArgumentCaptor.forClass(LlmCompletionRequest.class);
        ArgumentCaptor<JsonNode> schema = ArgumentCaptor.forClass(JsonNode.class);
        verify(llmClient).completeStructured(
                eq("test-user"), completionRequest.capture(), eq("relationship_suggestions"),
                schema.capture(), eq(RelationshipSuggestionLlmResponse.class));
        assertThat(schema.getValue().at("/properties/suggestions/maxItems").asInt()).isEqualTo(request.effectiveK());
        assertThat(schema.getValue().at("/properties/suggestions/items/properties/source_class/properties/id/enum")).isEqualTo(objectMapper.createArrayNode().add(request.selectedClassId()));
        String promptJson = completionRequest.getValue().prompt().substring(
                completionRequest.getValue().prompt().indexOf('{'));
        var prompt = objectMapper.readTree(promptJson);
        assertThat(prompt.path("request").path("selected_class_id").asText()).isEqualTo("class_001");
        assertThat(prompt.path("legal_texts").get(0).path("path").asText()).isEqualTo("2024/1/par_1");
    }
}
