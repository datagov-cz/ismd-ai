package cz.dia.ismd.assistant.api.suggestion.classsuggestion;

import cz.dia.ismd.assistant.model.suggestion.KnownConceptualModel;
import cz.dia.ismd.assistant.model.suggestion.ValidationPatterns;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;

import java.util.List;

public record ClassSuggestionJobRequest(
        @Schema(description = "Maximum number of class suggestions to generate. Defaults to 5 when omitted.")
        @Min(1) @Max(10) Integer k,
        @Schema(description = "ELI identifiers of legal structural elements whose text should be used as source material.")
        List<@Pattern(regexp = ValidationPatterns.ELI_URI) String> structuralElementIds,
        @Schema(description = "Plain-language user context used to guide class suggestion generation (optional, currently unused).")
        String contextText,
        @Schema(description = "Known vocabulary terms that the generated class suggestions should take into account.")
        @Valid KnownConceptualModel knownConceptualModel
) {
    public int effectiveK() {
        return k == null ? 5 : k;
    }
}
