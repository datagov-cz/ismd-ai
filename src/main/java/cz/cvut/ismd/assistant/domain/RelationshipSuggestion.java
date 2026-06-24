package cz.cvut.ismd.assistant.domain;

import java.util.List;

public record RelationshipSuggestion(
        String id,
        String type,
        LangString name,
        LangString definition,
        LangString explanation,
        boolean isRelationship,
        List<IdReference> mediatesClass,
        LegalAct legalAct,
        List<LocalSuggestionOccurrence> occurrences,
        List<LegalStructuralElement> references
) {
}
