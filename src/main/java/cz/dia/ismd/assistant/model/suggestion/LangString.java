package cz.dia.ismd.assistant.model.suggestion;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Map;
import java.util.Objects;

public record LangString(
        @Schema(description = "Localized string values keyed by language code, for example cs.")
        Map<String, String> values
) {
    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public LangString {
        values = Map.copyOf(Objects.requireNonNull(values, "values"));
    }

    public static LangString cs(String value) {
        return new LangString(Map.of("cs", value));
    }

    @JsonValue
    @Override
    public Map<String, String> values() {
        return values;
    }
}
