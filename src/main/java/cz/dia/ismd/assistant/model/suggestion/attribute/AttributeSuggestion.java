package cz.dia.ismd.assistant.model.suggestion.attribute;

import cz.dia.ismd.assistant.model.suggestion.IdReference;
import cz.dia.ismd.assistant.model.suggestion.LangString;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;

public record AttributeSuggestion(
        @Schema(description = "Stable identifier of the generated attribute suggestion.")
        String suggestionID,
        @Schema(description = "Class that the suggested attribute belongs to.")
        IdReference associatedClass,
        @Schema(description = "Localized name of the suggested attribute.")
        LangString name,
        @Schema(description = "Localized definition (definice) of the suggested attribute.")
        LangString definition,
        @Schema(description = "Localized explanation (popis) of the suggested attribute.")
        LangString explanation,
        @Schema(description = "Source of the suggested relationship as an ELI URI.")
        @Pattern(regexp = ValidationPatterns.ELI_URI) String legalAct
) {
}
