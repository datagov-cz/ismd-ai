package cz.cvut.ismd.assistant.controller.dto;

import java.time.Instant;
import java.util.List;

public record LegalActRepositoryResponse(
        Instant savedAt,
        String query,
        int count,
        List<String> legalActIris
) {
}
