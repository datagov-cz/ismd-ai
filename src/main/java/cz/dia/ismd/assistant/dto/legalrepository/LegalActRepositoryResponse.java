package cz.dia.ismd.assistant.dto.legalrepository;

import cz.dia.ismd.assistant.validation.ValidationPatterns;
import jakarta.validation.constraints.Pattern;

import java.time.Instant;
import java.util.List;

public record LegalActRepositoryResponse(
        Instant savedAt,
        String query,
        int count,
        List<@Pattern(regexp = ValidationPatterns.ELI_URI) String> legalActUris
) {
}
