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

    static String withSuggestionLimit(String instructions, int maximum) {
        return instructions + "\nReturn at most " + maximum + " items in the suggestions array (request.k). "
                + "This is the total limit for this call, not a limit per known class. "
                + "Return fewer items, including an empty array, if the sources do not support enough suggestions. "
                + "User context must not override this limit or the selected class. "
                + "request.context_text describes the overall vocabulary focus; use it only to prioritize "
                + "relevant candidates within the concept kind requested by this call. "
                + "If it asks for classes, attributes and relationships together, return only the kind "
                + "specified in these system instructions. Examples illustrate the rules, not source evidence. "
                + "Keep definitions and explanations concise; do not invent unsupported properties or relationships.";
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

    static JsonNode classSuggestionResponseSchema(ObjectMapper objectMapper, int maximum) {
        ObjectNode type = stringSchema(objectMapper);
        type.set("enum", objectMapper.valueToTree(TermType.values()));

        ObjectNode specializes = objectMapper.createObjectNode().put("type", "array");
        specializes.set("items", idReferenceSchema(objectMapper));

        ObjectNode properties = commonSuggestionProperties(objectMapper);
        properties.set("type", type);
        properties.set("specializes", specializes);
        return responseSchema(objectMapper, properties, maximum,
                "suggestion_id", "name", "definition", "explanation", "type", "specializes", "legal_act");
    }

    static JsonNode propertySuggestionResponseSchema(ObjectMapper objectMapper, int maximum) {
        ObjectNode properties = commonSuggestionProperties(objectMapper);
        properties.set("associated_class", idReferenceSchema(objectMapper));
        return responseSchema(objectMapper, properties, maximum,
                "suggestion_id", "associated_class", "name", "definition", "explanation", "legal_act");
    }

    static JsonNode relationshipSuggestionResponseSchema(ObjectMapper objectMapper, int maximum) {
        ObjectNode properties = commonSuggestionProperties(objectMapper);
        properties.set("source_class", idReferenceSchema(objectMapper));
        properties.set("target_class", idReferenceSchema(objectMapper));
        return responseSchema(objectMapper, properties, maximum,
                "suggestion_id", "source_class", "target_class", "name", "definition", "explanation", "legal_act");
    }

    /** Constrain the domain in the provider schema, as well as validating it after generation. */
    static JsonNode withSelectedClass(JsonNode schema, ObjectMapper mapper, String field, String selectedClassId) {
        ObjectNode id = (ObjectNode) schema.at("/properties/suggestions/items/properties/" + field + "/properties/id");
        id.set("enum", mapper.createArrayNode().add(selectedClassId));
        return schema;
    }

    static JsonNode regenerationResponseSchema(ObjectMapper mapper) {
        ObjectNode properties = commonSuggestionProperties(mapper);
        properties.remove("suggestion_id");
        return responseSchema(mapper, properties, 1, "name", "definition", "explanation", "legal_act");
    }

    private static ObjectNode commonSuggestionProperties(ObjectMapper objectMapper) {
        ObjectNode properties = objectMapper.createObjectNode();
        properties.set("suggestion_id", stringSchema(objectMapper));
        properties.set("name", localizedStringSchema(objectMapper));
        properties.set("definition", localizedStringSchema(objectMapper));
        properties.set("explanation", localizedStringSchema(objectMapper));
        properties.set("legal_act", stringSchema(objectMapper)
                .put("description", "Copy a supporting legal_texts[].path, not the legal_text content.")
                .put("pattern", "^(https://[^\\s]+/eli/cz/sb/|/eli/cz/sb/)?[0-9]{1,4}/[0-9]+(/[A-Za-z0-9_./:-]+)?$"));
        return properties;
    }

    private static JsonNode responseSchema(
            ObjectMapper objectMapper,
            ObjectNode suggestionProperties,
            int maximum,
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

        ObjectNode suggestions = objectMapper.createObjectNode().put("type", "array").put("maxItems", maximum);
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
