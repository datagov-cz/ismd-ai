package cz.dia.ismd.assistant.dto.legalrepository;

import cz.dia.ismd.assistant.validation.ValidationPatterns;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;

import java.time.Instant;
import java.util.List;

public record LegalActRepositoryResponse(
        @Schema(description = "Timestamp when the legal act repository result was saved.")
        Instant savedAt,
        @Schema(description = "Repository query that produced the legal act result.")
        String query,
        @Schema(description = "Number of legal act URIs returned by the query.")
        int count,
        @Schema(description = "ELI URIs of legal acts returned by the repository query.")
        List<@Pattern(regexp = ValidationPatterns.ELI_URI) String> legalActUris
) {
}
