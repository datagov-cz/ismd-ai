package cz.dia.ismd.assistant.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import cz.dia.ismd.assistant.exception.LlmException;

import java.util.Objects;
import java.util.function.Consumer;

/** Extracts complete objects from a streamed top-level {@code suggestions} array. */
final class StructuredSuggestionsParser<T> {

    private static final String SUGGESTIONS_FIELD = "\"suggestions\"";

    private final ObjectMapper objectMapper;
    private final Class<T> suggestionType;
    private final Consumer<T> consumer;
    private final StringBuilder content = new StringBuilder();
    private int scanPosition;
    private boolean arrayStarted;
    private int objectStart = -1;
    private int objectDepth;
    private boolean inString;
    private boolean escaped;

    StructuredSuggestionsParser(ObjectMapper objectMapper, Class<T> suggestionType, Consumer<T> consumer) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.suggestionType = Objects.requireNonNull(suggestionType, "suggestionType");
        this.consumer = Objects.requireNonNull(consumer, "consumer");
    }

    void accept(String delta) {
        if (delta == null || delta.isEmpty()) {
            return;
        }
        content.append(delta);
        if (!arrayStarted && !findSuggestionsArray()) {
            return;
        }
        scanObjects();
    }

    String content() {
        return content.toString();
    }

    private boolean findSuggestionsArray() {
        int field = content.indexOf(SUGGESTIONS_FIELD, scanPosition);
        if (field < 0) {
            scanPosition = Math.max(0, content.length() - SUGGESTIONS_FIELD.length());
            return false;
        }
        int colon = skipWhitespace(field + SUGGESTIONS_FIELD.length());
        if (colon >= content.length() || content.charAt(colon) != ':') {
            return false;
        }
        int array = skipWhitespace(colon + 1);
        if (array >= content.length()) {
            return false;
        }
        if (content.charAt(array) != '[') {
            throw new LlmException("LLM structured response suggestions field is not an array");
        }
        arrayStarted = true;
        scanPosition = array + 1;
        return true;
    }

    private int skipWhitespace(int position) {
        while (position < content.length() && Character.isWhitespace(content.charAt(position))) {
            position++;
        }
        return position;
    }

    private void scanObjects() {
        for (; scanPosition < content.length(); scanPosition++) {
            char current = content.charAt(scanPosition);
            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (current == '\\') {
                    escaped = true;
                } else if (current == '"') {
                    inString = false;
                }
                continue;
            }
            if (current == '"') {
                inString = true;
            } else if (current == '{') {
                if (objectDepth++ == 0) {
                    objectStart = scanPosition;
                }
            } else if (current == '}' && objectDepth > 0 && --objectDepth == 0) {
                publish(content.substring(objectStart, scanPosition + 1));
                objectStart = -1;
            } else if (current == ']' && objectDepth == 0) {
                scanPosition++;
                return;
            }
        }
    }

    private void publish(String json) {
        try {
            consumer.accept(objectMapper.readValue(json, suggestionType));
        } catch (JsonProcessingException exception) {
            throw new LlmException("LLM provider streamed an invalid suggestion", exception);
        }
    }
}
