package cz.dia.ismd.assistant.model.llm;

import cz.dia.ismd.assistant.model.suggestion.relationship.RelationshipSuggestion;

import java.util.List;
import java.util.Objects;

public record RelationshipSuggestionLlmResponse(List<RelationshipSuggestion> suggestions) {
    public RelationshipSuggestionLlmResponse {
        suggestions = List.copyOf(Objects.requireNonNull(suggestions, "suggestions"));
    }
}
