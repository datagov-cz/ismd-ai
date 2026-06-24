package cz.cvut.ismd.assistant.domain;

public record LocalSuggestionOccurrence(
        String id,
        String type,
        LegalStructuralElement legalActPart
) {
}
