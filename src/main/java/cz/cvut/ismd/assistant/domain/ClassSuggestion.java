package cz.cvut.ismd.assistant.domain;

import java.util.List;

public record ClassSuggestion(
        String id,
        String type,
        LangString name,
        LangString definition,
        LangString explanation,
        boolean isSubjectOfLaw,
        boolean isObjectOfLaw,
        List<IdReference> specializes,
        LegalAct legalAct,
        List<LocalSuggestionOccurrence> occurrences,
        List<LegalStructuralElement> references
) {
}
