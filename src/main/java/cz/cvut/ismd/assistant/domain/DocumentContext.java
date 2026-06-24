package cz.cvut.ismd.assistant.domain;

import java.time.LocalDate;
import java.util.Optional;

public record DocumentContext(
        Optional<Integer> year,
        Optional<Integer> number,
        Optional<LocalDate> date,
        Optional<String> text
) {
    public static DocumentContext legal(int year, int number, LocalDate date) {
        return new DocumentContext(Optional.of(year), Optional.of(number), Optional.of(date), Optional.empty());
    }

    public static DocumentContext nonLegal(String text) {
        return new DocumentContext(Optional.empty(), Optional.empty(), Optional.empty(), Optional.of(text));
    }
}
