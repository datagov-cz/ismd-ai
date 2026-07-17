package cz.dia.ismd.assistant.model.suggestion;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class ValidationPatternsTests {

    @ParameterizedTest
    @ValueSource(strings = {
            "/eli/cz/sb/2026/60/2026-05-27/dokument/norma/cast_1/hlava_1/par_3",
            "https://e-sbirka.gov.cz/eli/cz/sb/2026/60/2026-05-27/dokument/norma/cast_1/hlava_1/par_3",
            "ftp://mirror.test/eli/cz/sb/2026/60/2026-05-27/par_3",
            "arbitrary prefix with spaces /eli/cz/sb/2026/60/2026-05-27/par_3"
    })
    void acceptsRelativeAndAbsoluteEliIdentifiers(String identifier) {
        assertThat(Pattern.matches(ValidationPatterns.ELI_URI, identifier)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "§3",
            "eli/cz/sb/2026/60/2026-05-27/par_3",
            "https://e-sbirka.gov.cz/not-eli/cz/sb/2026/60/2026-05-27/par_3",
            "https://e-sbirka.gov.cz/eli/cz/sb/2026/60/par_3?version=1"
    })
    void rejectsValuesWithoutATerminalEliSuffix(String identifier) {
        assertThat(Pattern.matches(ValidationPatterns.ELI_URI, identifier)).isFalse();
    }
}
