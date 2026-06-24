package cz.dia.ismd.assistant.records.suggestion;

import com.fasterxml.jackson.annotation.JsonProperty;

public record LangString(
        @JsonProperty("@value") String value,
        @JsonProperty("@language") String language
) {
    public static LangString en(String value) {
        return new LangString(value, "en");
    }
}
