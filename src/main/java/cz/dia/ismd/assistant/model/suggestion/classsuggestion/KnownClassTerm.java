package cz.dia.ismd.assistant.model.suggestion.classsuggestion;

import com.fasterxml.jackson.annotation.JsonProperty;
import cz.dia.ismd.assistant.model.suggestion.TermType;
import cz.dia.ismd.assistant.model.suggestion.ValidationPatterns;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;

import java.util.List;

public record KnownClassTerm(
        @Schema(description = "Identifier of the known class term.")
        @JsonProperty("termID") String termID,
        @Schema(description = "Localized display name of the known class.")
        LangString name,
        @Schema(description = "Localized definition (definice) of the known class.")
        LangString definition,
        @Schema(description = "Localized explanation (popis) or note for the known class.")
        LangString explanation,
        @Schema(description = "Type of conceptual model term.")
        TermType type,
        @Schema(description = "Classes that the known class specializes.")
        List<IdReference> specializes,
        @Schema(description = "ELI URI of the legal act associated with the known class.")
        @Pattern(regexp = ValidationPatterns.ELI_URI) String legalAct
) {
}
