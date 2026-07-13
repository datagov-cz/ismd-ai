package cz.dia.ismd.assistant.api.suggestion.relationship;

import cz.dia.ismd.assistant.model.suggestion.KnownConceptualModel;
import cz.dia.ismd.assistant.model.suggestion.ValidationPatterns;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.util.List;

public record RelationshipSuggestionJobRequest(
        @Schema(description = "Maximum number of relationship suggestions to generate. Defaults to 5 when omitted.")
        @Min(1) @Max(10) Integer k,
        @Schema(description = "Identifier of the source class for which relationship suggestions should be generated. Must correspond to an identifier within the knownConceptualModel terms.")
        @NotBlank String selectedClassId,
        @Schema(description = "ELI identifiers of legal structural elements whose text should be used as source material.")
        List<@Pattern(regexp = ValidationPatterns.ELI_URI) String> structuralElementIds,
        @Schema(description = "Plain-language user context used to guide relationship suggestion generation (optional, currently unused).")
        String contextText,
        @Schema(description = "Known conceptual model terms that the generated relationship suggestions should take into account.")
        @Valid KnownConceptualModel knownConceptualModel
) {
    public int effectiveK() {
        return k == null ? 5 : k;
    }
}
