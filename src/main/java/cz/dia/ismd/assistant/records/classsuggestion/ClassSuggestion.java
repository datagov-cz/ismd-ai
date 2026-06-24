package cz.dia.ismd.assistant.records.classsuggestion;

import cz.dia.ismd.assistant.domain.TermType;
import cz.dia.ismd.assistant.records.suggestion.IdReference;
import cz.dia.ismd.assistant.records.suggestion.LangString;
import cz.dia.ismd.assistant.records.suggestion.LegalStructuralElement;
import cz.dia.ismd.assistant.records.suggestion.LocalSuggestionOccurrence;
import cz.dia.ismd.assistant.validation.ValidationPatterns;
import jakarta.validation.constraints.Pattern;

import java.util.List;

public record ClassSuggestion(
        String suggestionID,
        LangString name,
        LangString definition,
        LangString explanation,
        TermType type,
        List<IdReference> specializes,
        @Pattern(regexp = ValidationPatterns.ELI_URI) String legalAct
//        List<LocalSuggestionOccurrence> occurrences,
//        List<LegalStructuralElement> references
) {
}
