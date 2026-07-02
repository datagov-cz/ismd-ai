package cz.dia.ismd.assistant.records.classsuggestion;

import cz.dia.ismd.assistant.domain.TermType;
import cz.dia.ismd.assistant.records.suggestion.IdReference;
import cz.dia.ismd.assistant.records.suggestion.LangString;
import cz.dia.ismd.assistant.validation.ValidationPatterns;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;

import java.util.List;

public record ClassSuggestion(
        @Schema(description = "Stable identifier of the generated class suggestion.")
        String suggestionID,
        @Schema(description = "Localized display name of the suggested class.")
        LangString name,
        @Schema(description = "Localized definition (definice) of the suggested class.")
        LangString definition,
        @Schema(description = "Localized explanation (popis) of why the class was suggested.")
        LangString explanation,
        @Schema(description = "Type of conceptual model term represented by the suggestion.")
        TermType type,
        @Schema(description = "Existing or suggested classes that this class specializes.")
        List<IdReference> specializes,
        @Schema(description = "ELI URI of the legal act that supports the suggestion.")
        @Pattern(regexp = ValidationPatterns.ELI_URI) String legalAct
) {
}
