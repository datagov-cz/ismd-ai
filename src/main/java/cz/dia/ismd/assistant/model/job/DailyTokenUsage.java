package cz.dia.ismd.assistant.model.job;

import java.time.LocalDate;

public record DailyTokenUsage(LocalDate date, int usedTokens) {
}
