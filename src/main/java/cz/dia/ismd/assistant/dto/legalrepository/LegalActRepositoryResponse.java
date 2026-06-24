package cz.dia.ismd.assistant.dto.legalrepository;

import java.time.Instant;
import java.util.List;

public record LegalActRepositoryResponse(
        Instant savedAt,
        String query,
        int count,
        List<String> legalActIris
) {
}
