package cz.dia.ismd.assistant.model.legal;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.Year;

/** One interpretation of ELI identifiers for request validation and legal text retrieval. */
public record LegalActEli(String path, Year year, int number, LocalDate date) {
    private static final String PREFIX = "/eli/cz/sb/";

    public static LegalActEli parse(String identifier) {
        int marker = identifier == null ? -1 : identifier.indexOf("/eli/");
        if (marker < 0 || !identifier.startsWith(PREFIX, marker)
                || identifier.indexOf("/eli/", marker + 1) >= 0) {
            throw new IllegalArgumentException("ELI identifier must contain exactly one " + PREFIX);
        }
        return parsePath(identifier.substring(marker + PREFIX.length()));
    }

    public static LegalActEli parsePath(String path) {
        if (path == null || path.isBlank() || !path.matches("[A-Za-z0-9_./:-]+") || path.contains("/eli/")) {
            throw new IllegalArgumentException("Invalid or ambiguous ELI path: " + path);
        }
        String[] segments = path.split("/", -1);
        if (segments.length < 3 || !segments[0].matches("[0-9]{4}") || !segments[1].matches("[0-9]+")) {
            throw new IllegalArgumentException("ELI path must start with {year}/{number}/{date}: " + path);
        }
        try {
            return new LegalActEli(path, Year.parse(segments[0]), Integer.parseInt(segments[1]),
                    LocalDate.parse(segments[2]));
        } catch (IllegalArgumentException | DateTimeException exception) {
            throw new IllegalArgumentException("Unable to extract legal act year, number or date: " + path, exception);
        }
    }

    public String legalActPath() {
        return year + "/" + number + "/" + date;
    }

    public LegalAct toLegalAct() {
        return new LegalAct(number, year, date);
    }
}
