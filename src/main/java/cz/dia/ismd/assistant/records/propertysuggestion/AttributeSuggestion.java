package cz.dia.ismd.assistant.records.propertysuggestion;

import cz.dia.ismd.assistant.records.suggestion.*;

import java.util.List;

public record AttributeSuggestion(
        String suggestionID,
        IdReference associatedClass,
        LangString name,
        LangString definition,
        LangString explanation,
        LegalAct legalAct,
        List<LocalSuggestionOccurrence> occurrences,
        List<LegalStructuralElement> references
) {
}
