package cz.dia.ismd.assistant.records.legalrepository;

import java.time.Instant;
import java.util.List;

public record LegalActRepositoryRecord(
        Instant savedAt,
        String query,
        List<String> legalActIris
) {
    public LegalActRepositoryRecord {
        legalActIris = legalActIris == null ? List.of() : List.copyOf(legalActIris);
    }
}
