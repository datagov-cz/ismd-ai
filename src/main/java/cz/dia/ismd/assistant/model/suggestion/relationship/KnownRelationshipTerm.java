package cz.dia.ismd.assistant.model.suggestion.relationship;

import com.fasterxml.jackson.annotation.JsonProperty;
import cz.dia.ismd.assistant.model.suggestion.IdReference;
import cz.dia.ismd.assistant.model.suggestion.LangString;
import cz.dia.ismd.assistant.model.suggestion.ValidationPatterns;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;

public record KnownRelationshipTerm(
        @Schema(description = "Identifier of the known relationship term.")
        @JsonProperty("termID") String termID,
        @Schema(description = "Source class of the known relationship.")
        IdReference sourceClass,
        @Schema(description = "Target class of the known relationship.")
        IdReference targetClass,
        @Schema(description = "Localized display name of the known relationship.")
        LangString name,
        @Schema(description = "Localized definition (definice) of the known relationship.")
        LangString definition,
        @Schema(description = "Localized explanation (popis) or note for the known relationship.")
        LangString explanation,
        @Schema(description = "ELI URI of the legal act associated with the known relationship.")
        @Pattern(regexp = ValidationPatterns.ELI_URI) String legalAct
) {
}
