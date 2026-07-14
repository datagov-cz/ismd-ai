package cz.dia.ismd.assistant.model.legal;

public record LegalActText(
        Long id,
        long legalActId,
        String path,
        String legalText,
        String legalHierarchy,
        String legalOrder
) {
}
