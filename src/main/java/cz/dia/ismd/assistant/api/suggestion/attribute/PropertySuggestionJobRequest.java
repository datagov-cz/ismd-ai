package cz.dia.ismd.assistant.api.suggestion.attribute;

import com.fasterxml.jackson.annotation.JsonIgnore;
import cz.dia.ismd.assistant.model.suggestion.KnownConceptualModel;
import cz.dia.ismd.assistant.model.suggestion.ValidationPatterns;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
        @NotNull @Valid KnownConceptualModel knownConceptualModel
) {
    public int effectiveK() {
        return k == null ? 5 : k;
    }

    @JsonIgnore
    @AssertTrue(message = "knownConceptualModel must be non-empty and contain selectedClassId as a class termID")
    public boolean isSelectedClassPresentInKnownConceptualModel() {
        return selectedClassId != null
                && knownConceptualModel != null
                && knownConceptualModel.classes() != null
                && !knownConceptualModel.classes().isEmpty()
                && knownConceptualModel.classes().stream()
                .anyMatch(term -> term != null && selectedClassId.equals(term.termID()));
    }
}
