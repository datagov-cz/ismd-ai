package cz.dia.ismd.assistant.records.propertysuggestion;

import cz.dia.ismd.assistant.records.suggestion.LangString;
import cz.dia.ismd.assistant.records.suggestion.LegalAct;
import cz.dia.ismd.assistant.records.suggestion.LegalStructuralElement;
import cz.dia.ismd.assistant.records.suggestion.LocalSuggestionOccurrence;

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
