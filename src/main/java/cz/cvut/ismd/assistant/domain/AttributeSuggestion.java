package cz.cvut.ismd.assistant.domain;

import java.util.List;

public record AttributeSuggestion(
        String id,
        String type,
        LangString name,
        LangString definition,
        LangString explanation,
        LegalAct legalAct,
        List<LocalSuggestionOccurrence> occurrences,
        List<LegalStructuralElement> references
) {
}
