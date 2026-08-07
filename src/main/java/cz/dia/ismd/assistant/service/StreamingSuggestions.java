package cz.dia.ismd.assistant.service;

import java.util.List;

public record StreamingSuggestions<T>(List<T> suggestions) {
    public StreamingSuggestions {
        suggestions = List.copyOf(suggestions);
    }
}
