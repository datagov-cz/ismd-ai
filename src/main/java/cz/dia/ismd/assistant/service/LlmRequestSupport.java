package cz.dia.ismd.assistant.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import cz.dia.ismd.assistant.exception.LlmException;
import cz.dia.ismd.assistant.model.legal.LegalActText;
import cz.dia.ismd.assistant.model.suggestion.TermType;

import java.util.List;

final class LlmRequestSupport {

    static final String CLOSED_WORLD_INSTRUCTION = """
            Work only with information explicitly included in this request. Do not use the internet, web search,
            external tools, retrieval systems, URLs, files, or unstated background knowledge. If the supplied data
            does not support an answer, report or return no result instead of filling gaps from outside knowledge.
            """;

    private LlmRequestSupport() {
    }

    static String serializePrompt(
            ObjectMapper objectMapper,
            Object request,
            List<LegalActText> legalActTexts,
            String suggestionKind
    ) {
        try {
            List<LegalTextSource> sources = legalActTexts.stream()
                    .map(text -> new LegalTextSource(text.path(), text.legalText()))
                    .toList();
            return objectMapper.writeValueAsString(new SuggestionPrompt(request, sources));
        } catch (JsonProcessingException exception) {
            throw new LlmException(suggestionKind + " suggestion request could not be serialized", exception);
        }
    }

    static JsonNode classSuggestionResponseSchema(ObjectMapper objectMapper) {
        ObjectNode type = stringSchema(objectMapper);
        type.set("enum", objectMapper.valueToTree(TermType.values()));

        ObjectNode specializes = objectMapper.createObjectNode().put("type", "array");
        specializes.set("items", idReferenceSchema(objectMapper));

        ObjectNode properties = commonSuggestionProperties(objectMapper);
        properties.set("type", type);
        properties.set("specializes", specializes);
        return responseSchema(objectMapper, properties,
                "suggestion_id", "name", "definition", "explanation", "type", "specializes", "legal_act");
    }

    static JsonNode propertySuggestionResponseSchema(ObjectMapper objectMapper) {
        ObjectNode properties = commonSuggestionProperties(objectMapper);
        properties.set("associated_class", idReferenceSchema(objectMapper));
        return responseSchema(objectMapper, properties,
                "suggestion_id", "associated_class", "name", "definition", "explanation", "legal_act");
    }

    static JsonNode relationshipSuggestionResponseSchema(ObjectMapper objectMapper) {
        ObjectNode properties = commonSuggestionProperties(objectMapper);
        properties.set("source_class", idReferenceSchema(objectMapper));
        properties.set("target_class", idReferenceSchema(objectMapper));
        return responseSchema(objectMapper, properties,
                "suggestion_id", "source_class", "target_class", "name", "definition", "explanation", "legal_act");
    }

    private static ObjectNode commonSuggestionProperties(ObjectMapper objectMapper) {
        ObjectNode properties = objectMapper.createObjectNode();
        properties.set("suggestion_id", stringSchema(objectMapper));
        properties.set("name", localizedStringSchema(objectMapper));
        properties.set("definition", localizedStringSchema(objectMapper));
        properties.set("explanation", localizedStringSchema(objectMapper));
        properties.set("legal_act", stringSchema(objectMapper));
        return properties;
    }

    private static JsonNode responseSchema(
            ObjectMapper objectMapper,
            ObjectNode suggestionProperties,
            String... requiredSuggestionProperties
    ) {
        ObjectNode suggestion = objectMapper.createObjectNode()
                .put("type", "object")
                .put("additionalProperties", false);
        suggestion.set("properties", suggestionProperties);
        var required = objectMapper.createArrayNode();
        for (String property : requiredSuggestionProperties) {
            required.add(property);
        }
        suggestion.set("required", required);

        ObjectNode suggestions = objectMapper.createObjectNode().put("type", "array");
        suggestions.set("items", suggestion);
        ObjectNode properties = objectMapper.createObjectNode();
        properties.set("suggestions", suggestions);
        ObjectNode schema = objectMapper.createObjectNode()
                .put("type", "object")
                .put("additionalProperties", false);
        schema.set("properties", properties);
        schema.set("required", objectMapper.createArrayNode().add("suggestions"));
        return schema;
    }

    private static JsonNode localizedStringSchema(ObjectMapper objectMapper) {
        ObjectNode schema = objectMapper.createObjectNode()
                .put("type", "object")
                .put("additionalProperties", false);
        schema.set("properties", objectMapper.createObjectNode().set("cs", stringSchema(objectMapper)));
        schema.set("required", objectMapper.createArrayNode().add("cs"));
        return schema;
    }

    private static JsonNode idReferenceSchema(ObjectMapper objectMapper) {
        ObjectNode schema = objectMapper.createObjectNode()
                .put("type", "object")
                .put("additionalProperties", false);
        schema.set("properties", objectMapper.createObjectNode().set("id", stringSchema(objectMapper)));
        schema.set("required", objectMapper.createArrayNode().add("id"));
        return schema;
    }

    private static ObjectNode stringSchema(ObjectMapper objectMapper) {
        return objectMapper.createObjectNode().put("type", "string");
    }

    private record SuggestionPrompt(Object request, List<LegalTextSource> legalTexts) {
    }

    private record LegalTextSource(String path, String legalText) {
    }
}
