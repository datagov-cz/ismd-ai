package cz.dia.ismd.assistant.records.suggestion;

import cz.dia.ismd.assistant.records.classsuggestion.ClassSuggestion;
import cz.dia.ismd.assistant.records.propertysuggestion.AttributeSuggestion;
import cz.dia.ismd.assistant.records.relationshipsuggestion.RelationshipSuggestion;
import jakarta.validation.Valid;

import java.util.List;

public record KnownConceptualModel(
        List<@Valid ClassSuggestion> classes,
        List<@Valid AttributeSuggestion> attributes,
        List<@Valid RelationshipSuggestion> relationships
) {
}
