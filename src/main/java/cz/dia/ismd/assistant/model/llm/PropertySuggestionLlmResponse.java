package cz.dia.ismd.assistant.model.llm;

import cz.dia.ismd.assistant.model.suggestion.attribute.AttributeSuggestion;

import java.util.List;
import java.util.Objects;

public record PropertySuggestionLlmResponse(List<AttributeSuggestion> suggestions) {
    public PropertySuggestionLlmResponse {
        suggestions = List.copyOf(Objects.requireNonNull(suggestions, "suggestions"));
    }
}
