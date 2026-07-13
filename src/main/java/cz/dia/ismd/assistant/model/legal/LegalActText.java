package cz.dia.ismd.assistant.model.legal;

public record LegalActText(
        Long id,
        long legalActId,
        String legalText,
        String officialId,
        String officialNumber,
        Long successorId
) {
}
