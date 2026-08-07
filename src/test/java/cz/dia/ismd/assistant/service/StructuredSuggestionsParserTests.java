package cz.dia.ismd.assistant.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import cz.dia.ismd.assistant.model.suggestion.classsuggestion.ClassSuggestion;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StructuredSuggestionsParserTests {

    @Test
    void publishesOnlyFullyClosedSuggestionObjectsAndDoesNotRepeatThem() {
        ObjectMapper objectMapper = new ObjectMapper()
                .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
        List<ClassSuggestion> published = new ArrayList<>();
        StructuredSuggestionsParser<ClassSuggestion> parser =
                new StructuredSuggestionsParser<>(objectMapper, ClassSuggestion.class, published::add);

        parser.accept("{\"suggestions\":[{\"suggestion_id\":\"one\",\"name\":{\"cs\":\"První\"},");
        assertThat(published).isEmpty();

        parser.accept("\"definition\":null,\"explanation\":null,\"type\":\"CLASS\","
                + "\"specializes\":[],\"legal_act\":\"/eli/1\"},"
                + "{\"suggestion_id\":\"two\",\"name\":{\"cs\":\"Druhá\"},");
        assertThat(published).extracting(ClassSuggestion::suggestionID).containsExactly("one");

        parser.accept("\"definition\":null,\"explanation\":null,\"type\":\"CLASS\","
                + "\"specializes\":[],\"legal_act\":\"/eli/2\"}]} ");
        assertThat(published).extracting(ClassSuggestion::suggestionID).containsExactly("one", "two");
    }
}
