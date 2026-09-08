package cz.dia.ismd.assistant.service;

import com.fasterxml.jackson.databind.*;
import cz.dia.ismd.assistant.api.suggestion.vocabulary.VocabularyRegenerationJobRequest;
import cz.dia.ismd.assistant.model.legal.LegalActText;
import cz.dia.ismd.assistant.model.llm.LlmCompletionRequest;
import cz.dia.ismd.assistant.model.suggestion.*;
import cz.dia.ismd.assistant.model.suggestion.attribute.KnownAttributeTerm;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class ConceptRegenerationLlmServiceTests {
    @Test
    void sendsActualTargetAndSourcesAndRequestsOnlyMutableMetadata() throws Exception {
        var mapper = new ObjectMapper().setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
        var client = mock(LlmClient.class);
        var target = new KnownAttributeTerm("attribute-volume", new IdReference("motorcycle"), LangString.cs("Objem"), null, null, null);
        var model = new KnownConceptualModel(List.of(), List.of(target), List.of());
        var request = new VocabularyRegenerationJobRequest("attribute-volume", List.of("/eli/cz/sb/2024/1/2024-01-01"), "Upřesni název", model);
        when(client.completeStructured(anyString(), any(), eq("concept_regeneration"), any(), eq(ConceptRegenerationLlmService.Response.class)))
                .thenReturn(new ConceptRegenerationLlmService.Response(List.of()));
        ConceptRegenerationLlmService.regenerate(client, mapper, "user", ConceptRegenerationLlmService.Kind.ATTRIBUTE, target, request,
                List.of(new LegalActText(1L, 1, "2024/1/2024-01-01", "Motocykl o objemu válců do 50 cm3.", "par", "1")));
        var completion = ArgumentCaptor.forClass(LlmCompletionRequest.class);
        var schema = ArgumentCaptor.forClass(JsonNode.class);
        verify(client).completeStructured(eq("user"), completion.capture(), eq("concept_regeneration"), schema.capture(), eq(ConceptRegenerationLlmService.Response.class));
        var prompt = mapper.readTree(completion.getValue().prompt());
        assertThat(prompt.at("/request/target_concept/termID").asText()).isEqualTo("attribute-volume");
        assertThat(prompt.at("/request/target_concept/associated_class/id").asText()).isEqualTo("motorcycle");
        assertThat(prompt.at("/request/context_text").asText()).isEqualTo("Upřesni název");
        assertThat(prompt.at("/legal_texts/0/legal_text").asText()).contains("50 cm3");
        var props = schema.getValue().at("/properties/suggestions/items/properties");
        assertThat(props.size()).isEqualTo(4);
        assertThat(props.has("name") && props.has("definition") && props.has("explanation") && props.has("legal_act")).isTrue();
        assertThat(schema.getValue().at("/properties/suggestions/maxItems").asInt()).isEqualTo(1);
        var sourcePattern = java.util.regex.Pattern.compile(props.at("/legal_act/pattern").asText());
        assertThat(sourcePattern.matcher("<var>d)</var> řidič je účastník provozu").matches()).isFalse();
        assertThat(sourcePattern.matcher("2000/361/2024-01-01/dokument/norma/par_2/pism_d").matches()).isTrue();
        assertThat(sourcePattern.matcher("https://e-sbirka.gov.cz/eli/cz/sb/2000/361/2024-01-01/par_6/odst_5:2").matches()).isTrue();
    }
}
