package cz.dia.ismd.assistant.records.suggestion;

import jakarta.validation.Valid;

import java.util.List;

public record KnownConceptualModel(
        List<@Valid KnownClassTerm> classes,
        List<@Valid KnownAttributeTerm> attributes,
        List<@Valid KnownRelationshipTerm> relationships
) {
}
