package cz.dia.ismd.assistant.records.propertysuggestion;

import cz.dia.ismd.assistant.records.suggestion.*;
import cz.dia.ismd.assistant.validation.ValidationPatterns;
import jakarta.validation.constraints.Pattern;

import java.util.List;

public record AttributeSuggestion(
        String suggestionID,
        IdReference associatedClass,
        LangString name,
        LangString definition,
        LangString explanation,
        @Pattern(regexp = ValidationPatterns.ELI_URI) String legalAct
//        List<LocalSuggestionOccurrence> occurrences,
//        List<LegalStructuralElement> references
) {
}
