package cz.dia.ismd.assistant.records.suggestion;

public record LocalSuggestionOccurrence(
        String id,
        String type,
        LegalStructuralElement legalActPart
) {
}
