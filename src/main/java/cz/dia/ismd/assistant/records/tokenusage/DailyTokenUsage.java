package cz.dia.ismd.assistant.records.tokenusage;

import java.time.LocalDate;

public record DailyTokenUsage(LocalDate date, int usedTokens) {
}
