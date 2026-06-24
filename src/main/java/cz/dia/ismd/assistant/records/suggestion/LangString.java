package cz.dia.ismd.assistant.records.suggestion;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Map;
import java.util.Objects;

public record LangString(Map<String, String> values) {
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
