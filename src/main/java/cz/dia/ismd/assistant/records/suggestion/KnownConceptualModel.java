package cz.dia.ismd.assistant.records.suggestion;

import cz.dia.ismd.assistant.records.classsuggestion.ClassSuggestion;
import cz.dia.ismd.assistant.records.propertysuggestion.AttributeSuggestion;
import cz.dia.ismd.assistant.records.relationshipsuggestion.RelationshipSuggestion;

import java.util.List;

public record KnownConceptualModel(
        List<ClassSuggestion> classes,
        List<AttributeSuggestion> attributes,
        List<RelationshipSuggestion> relationships
) {
}
