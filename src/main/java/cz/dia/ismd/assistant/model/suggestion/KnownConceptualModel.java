package cz.dia.ismd.assistant.model.suggestion;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;

import java.util.List;

public record KnownConceptualModel(
        @Schema(description = "Known class terms already present in the conceptual model.")
        List<@Valid KnownClassTerm> classes,
        @Schema(description = "Known attribute terms already present in the conceptual model.")
        List<@Valid KnownAttributeTerm> attributes,
        @Schema(description = "Known relationship terms already present in the conceptual model.")
        List<@Valid KnownRelationshipTerm> relationships
) {
}
