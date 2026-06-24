package cz.dia.ismd.assistant.dto.propertysuggestion;

import cz.dia.ismd.assistant.records.suggestion.KnownConceptualModel;
import cz.dia.ismd.assistant.validation.ValidationPatterns;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.util.List;

public record PropertySuggestionJobRequest(
        @Min(1) @Max(10) Integer k,
        @NotBlank String selectedClassId,
        List<@Pattern(regexp = ValidationPatterns.ELI_URI) String> structuralElementIds,
        String contextText,
        @Valid KnownConceptualModel knownConceptualModel
) {
    public int effectiveK() {
        return k == null ? 5 : k;
    }
}
