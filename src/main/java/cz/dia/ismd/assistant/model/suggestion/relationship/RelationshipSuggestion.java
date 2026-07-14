package cz.dia.ismd.assistant.model.suggestion.relationship;

import cz.dia.ismd.assistant.model.suggestion.IdReference;
import cz.dia.ismd.assistant.model.suggestion.LangString;
import cz.dia.ismd.assistant.model.suggestion.ValidationPatterns;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;

public record RelationshipSuggestion(
        @Schema(description = "Stable identifier of the generated relationship suggestion.")
        String suggestionID,
        @Schema(description = "Source class of the suggested relationship.")
        IdReference sourceClass,
        @Schema(description = "Target class of the suggested relationship.")
        IdReference targetClass,
        @Schema(description = "Localized name of the suggested relationship.")
        LangString name,
        @Schema(description = "Localized definition (definice) of the suggested relationship.")
        LangString definition,
        @Schema(description = "Localized explanation (popis) of the suggested relationship.")
        LangString explanation,
        @Schema(description = "Source of the suggested relationship as an ELI URI.")
        @Pattern(regexp = ValidationPatterns.ELI_URI) String legalAct
) {
}
