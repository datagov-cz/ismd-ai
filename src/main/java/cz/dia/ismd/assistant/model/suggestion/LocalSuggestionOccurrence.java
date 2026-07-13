package cz.dia.ismd.assistant.model.suggestion;

public record LocalSuggestionOccurrence(
        String id,
        String type,
        LegalStructuralElement legalActPart
) {
}
