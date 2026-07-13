package cz.dia.ismd.assistant;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import cz.dia.ismd.assistant.api.suggestion.classsuggestion.ClassSuggestionJobRequest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class KnownConceptualModelDeserializationTests {

    private final ObjectMapper objectMapper = new ObjectMapper()
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);

    @Test
    void acceptsTermIdWithAnyStringValueInKnownConceptualModel() throws Exception {
        ClassSuggestionJobRequest request = objectMapper.readValue("""
                {
                  "known_conceptual_model": {
                    "classes": [
                      {
                        "termID": "external id: /not/an/eli uri #1",
                        "name": {"en": "Known class"},
                        "definition": {"en": "Known definition"},
                        "explanation": {"en": "Known explanation"},
                        "type": "CLASS",
                        "specializes": [],
                        "legal_act": "/eli/cz/sb/2024/1"
                      }
                    ],
                    "attributes": [],
                    "relationships": []
                  }
                }
                """, ClassSuggestionJobRequest.class);

        assertThat(request.knownConceptualModel().classes().get(0).termID())
                .isEqualTo("external id: /not/an/eli uri #1");
    }
}
