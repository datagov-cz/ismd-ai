package cz.dia.ismd.assistant.data.legalact;

public record LegalActText(
        Long id,
        long legalActId,
        String legalText,
        String officialId,
        String officialNumber,
        Long successorId
) {
}
