package cz.dia.ismd.assistant.api.suggestion.vocabulary;

import cz.dia.ismd.assistant.exception.InvalidVocabularyRequestException;

import java.time.LocalDate;
import java.util.List;

/** Resolves the default source and validates that all elements belong to the selected version. */
final class LegalActSourceResolver {
    private LegalActSourceResolver() {}

    static List<String> resolve(int year, int number, LocalDate date, List<String> structuralElementIds) {
        if (year < 1 || year > 9999 || number < 1 || number > 32767)
            throw new InvalidVocabularyRequestException("Invalid legal act year or number");
        String root = "/eli/cz/sb/" + year + "/" + number + "/" + date;
        if (structuralElementIds == null || structuralElementIds.isEmpty())
            return List.of("https://e-sbirka.gov.cz" + root);
        for (String element : structuralElementIds) {
            int index = element == null ? -1 : element.indexOf("/eli/");
            String path = index < 0 ? "" : element.substring(index);
            if (!path.equals(root) && !path.startsWith(root + "/"))
                throw new InvalidVocabularyRequestException(
                        "structural_element_ids must belong to the legal act version in the URL");
        }
        return structuralElementIds.stream().distinct().toList();
    }
}
