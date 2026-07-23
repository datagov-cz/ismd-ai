package cz.dia.ismd.assistant.service;

import java.util.List;

record StreamingSuggestions<T>(List<T> suggestions) {
    StreamingSuggestions {
        suggestions = List.copyOf(suggestions);
    }
}
