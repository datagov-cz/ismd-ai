package cz.dia.ismd.assistant.records.relationshipsuggestion;

import cz.dia.ismd.assistant.records.suggestion.IdReference;
import cz.dia.ismd.assistant.records.suggestion.LangString;
import cz.dia.ismd.assistant.records.suggestion.LegalStructuralElement;
import cz.dia.ismd.assistant.records.suggestion.LocalSuggestionOccurrence;
import cz.dia.ismd.assistant.validation.ValidationPatterns;
import jakarta.validation.constraints.Pattern;

import java.util.List;

public record RelationshipSuggestion(
        String suggestionID,
        IdReference sourceClass,
        IdReference targetClass,
        LangString name,
        LangString definition,
        LangString explanation,
        @Pattern(regexp = ValidationPatterns.ELI_URI) String legalAct
//        List<LocalSuggestionOccurrence> occurrences,
//        List<LegalStructuralElement> references
) {
}
