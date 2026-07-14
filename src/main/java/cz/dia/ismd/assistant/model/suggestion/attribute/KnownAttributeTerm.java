package cz.dia.ismd.assistant.model.suggestion.attribute;

import com.fasterxml.jackson.annotation.JsonProperty;
import cz.dia.ismd.assistant.model.suggestion.IdReference;
import cz.dia.ismd.assistant.model.suggestion.LangString;
import cz.dia.ismd.assistant.model.suggestion.ValidationPatterns;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;

public record KnownAttributeTerm(
        @Schema(description = "Identifier of the known attribute term.")
        @JsonProperty("termID") String termID,
        @Schema(description = "Class that the known attribute belongs to.")
        IdReference associatedClass,
        @Schema(description = "Localized display name of the known attribute.")
        LangString name,
        @Schema(description = "Localized definition (definice) of the known attribute.")
        LangString definition,
        @Schema(description = "Localized explanation (popis) or note for the known attribute.")
        LangString explanation,
        @Schema(description = "ELI URI of the legal act associated with the known attribute.")
        @Pattern(regexp = ValidationPatterns.ELI_URI) String legalAct
) {
}
