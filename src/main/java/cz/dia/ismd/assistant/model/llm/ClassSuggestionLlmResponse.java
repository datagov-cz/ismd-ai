package cz.dia.ismd.assistant.model.llm;

import cz.dia.ismd.assistant.model.suggestion.classsuggestion.ClassSuggestion;

import java.util.List;
import java.util.Objects;

public record ClassSuggestionLlmResponse(List<ClassSuggestion> suggestions) {
    public ClassSuggestionLlmResponse {
        suggestions = List.copyOf(Objects.requireNonNull(suggestions, "suggestions"));
    }
}
