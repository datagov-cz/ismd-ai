package cz.dia.ismd.assistant.model.legal;

import java.time.Instant;
import java.time.LocalDate;
import java.time.Year;

public record LegalAct(
        Long id,
        int number,
        Year year,
        LocalDate date,
        String title,
        Instant createdAt,
        Instant updatedAt
) {
    public LegalAct(int number, Year year, LocalDate date) {
        this(null, number, year, date, null, null, null);
    }

    public LegalAct withTitle(String title) {
        return new LegalAct(id, number, year, date, title, createdAt, updatedAt);
    }
}
