package cz.dia.ismd.assistant.dto.propertysuggestion;

import cz.dia.ismd.assistant.records.suggestion.KnownConceptualModel;
import cz.dia.ismd.assistant.validation.ValidationPatterns;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.util.List;

public record PropertySuggestionJobRequest(
        @Schema(description = "Maximum number of attribute suggestions to generate. Defaults to 5 when omitted.")
        @Min(1) @Max(10) Integer k,
        @Schema(description = "Identifier of the class for which attribute suggestions should be generated. Must correspond to an identifier within the knownConceptualModel terms.")
        @NotBlank String selectedClassId,
        @Schema(description = "ELI identifiers of legal structural elements whose text should be used as source material.")
        List<@Pattern(regexp = ValidationPatterns.ELI_URI) String> structuralElementIds,
        @Schema(description = "Plain-language user context used to guide property suggestion generation (optional, currently unused).")
        String contextText,
        @Schema(description = "Known conceptual model terms that the generated attribute suggestions should take into account.")
        @Valid KnownConceptualModel knownConceptualModel
) {
    public int effectiveK() {
        return k == null ? 5 : k;
    }
}
