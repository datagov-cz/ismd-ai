package cz.dia.ismd.assistant.records.classsuggestion;

import cz.dia.ismd.assistant.records.suggestion.IdReference;
import cz.dia.ismd.assistant.records.suggestion.LangString;
import cz.dia.ismd.assistant.records.suggestion.LegalAct;
import cz.dia.ismd.assistant.records.suggestion.LegalStructuralElement;
import cz.dia.ismd.assistant.records.suggestion.LocalSuggestionOccurrence;

import java.util.List;

public record ClassSuggestion(
        String id,
        String type,
        LangString name,
        LangString definition,
        LangString explanation,
        boolean isSubjectOfLaw,
        boolean isObjectOfLaw,
        List<IdReference> specializes,
        LegalAct legalAct,
        List<LocalSuggestionOccurrence> occurrences,
        List<LegalStructuralElement> references
) {
}
