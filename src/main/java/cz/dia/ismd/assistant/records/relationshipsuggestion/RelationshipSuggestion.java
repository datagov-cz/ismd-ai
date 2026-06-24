package cz.dia.ismd.assistant.records.relationshipsuggestion;

import cz.dia.ismd.assistant.records.suggestion.IdReference;
import cz.dia.ismd.assistant.records.suggestion.LangString;
import cz.dia.ismd.assistant.records.suggestion.LegalAct;
import cz.dia.ismd.assistant.records.suggestion.LegalStructuralElement;
import cz.dia.ismd.assistant.records.suggestion.LocalSuggestionOccurrence;

import java.util.List;

public record RelationshipSuggestion(
        String id,
        String type,
        String sourceClass,
        String targetClass,
        LangString name,
        LangString definition,
        LangString explanation,
        LegalAct legalAct,
        List<LocalSuggestionOccurrence> occurrences,
        List<LegalStructuralElement> references
) {
}
