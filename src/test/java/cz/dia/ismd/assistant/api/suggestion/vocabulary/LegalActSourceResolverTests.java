package cz.dia.ismd.assistant.api.suggestion.vocabulary;

import cz.dia.ismd.assistant.exception.InvalidVocabularyRequestException;
import cz.dia.ismd.assistant.model.legal.LegalActEli;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

class LegalActSourceResolverTests {
    private static final LocalDate DATE = LocalDate.of(2024, 1, 1);

    @ParameterizedTest
    @ValueSource(strings = {"", "https://e-sbirka.gov.cz", "https://opendata.eselpoint.gov.cz/esel-esb"})
    void validatesTheSameParsedVersionAndPathUsedForRetrieval(String prefix) {
        String path = "2024/1/2024-01-01/dokument/norma/par_2/odst_1:2";
        String identifier = prefix + "/eli/cz/sb/" + path;
        var resolved = LegalActSourceResolver.resolve(2024, 1, DATE, List.of(identifier, identifier));
        assertThat(resolved).containsExactly(identifier);
        var parsed = LegalActEli.parse(resolved.get(0));
        assertThat(parsed.path()).isEqualTo(path);
        assertThat(parsed.year().getValue()).isEqualTo(2024);
        assertThat(parsed.number()).isEqualTo(1);
        assertThat(parsed.date()).isEqualTo(DATE);
    }

    @Test
    void defaultWholeActUsesTheSameParser() {
        var resolved = LegalActSourceResolver.resolve(2024, 1, DATE, null);
        var parsed = LegalActEli.parse(resolved.get(0));
        assertThat(parsed.path()).isEqualTo(parsed.legalActPath()).isEqualTo("2024/1/2024-01-01");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/eli/cz/sb/2025/1/2024-01-01/par_2",
            "/eli/cz/sb/2024/2/2024-01-01/par_2",
            "/eli/cz/sb/2024/1/2024-01-02/par_2",
            "/eli/cz/sb/2024/1/2024-02-30/par_2",
            "/eli/cz/sb/2024/1/2024-01-01/eli/cz/sb/2025/2/2025-01-01/par_2",
            "/eli/cz/sb/2024/1/2024-01-01/eli/cz/sb/2024/1/2024-01-01/par_2"
    })
    void rejectsWrongVersionAndAmbiguousPathsEvenWhenBeanValidationAcceptsThem(String identifier) {
        var request = new VocabularySuggestionJobRequest(1, 0, 0, List.of(identifier), null, null);
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            assertThat(factory.getValidator().validate(request)).isEmpty();
        }
        assertThatThrownBy(() -> request.forLegalAct(2024, 1, DATE))
                .isInstanceOf(InvalidVocabularyRequestException.class);
    }
}
