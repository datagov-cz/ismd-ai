package cz.dia.ismd.assistant.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import cz.dia.ismd.assistant.api.suggestion.classsuggestion.ClassSuggestionJobRequest;
import cz.dia.ismd.assistant.exception.LlmException;
import cz.dia.ismd.assistant.model.legal.LegalActText;
import cz.dia.ismd.assistant.model.llm.ClassSuggestionLlmResponse;
import cz.dia.ismd.assistant.model.llm.LlmCompletionRequest;
import cz.dia.ismd.assistant.model.suggestion.TermType;
import cz.dia.ismd.assistant.model.suggestion.classsuggestion.ClassSuggestion;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

@Service
public class ClassSuggestionLlmService {

    private static final String SYSTEM_PROMPT = """
            You identify conceptual-model classes in legal text. Return only class suggestions supported by the
            supplied request data and legal_texts sources. Each source keeps its legal_text associated with its
            path; use that path as the legal_act reference for suggestions supported by the source. Use Czech
            localized strings and the requested maximum number of suggestions.
            """;
    private static final String SAMPLE_PROMPT = "Suggest conceptual-model classes for this class-suggestion job request:";

    private final LlmClient llmClient;
    private final ObjectMapper objectMapper;

    public ClassSuggestionLlmService(LlmClient llmClient, ObjectMapper objectMapper) {
        this.llmClient = llmClient;
        this.objectMapper = objectMapper;
    }

    public List<ClassSuggestion> suggestClasses(
            String userId,
            ClassSuggestionJobRequest request,
            List<LegalActText> legalActTexts
    ) {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(legalActTexts, "legalActTexts");
        String requestData;
        try {
            List<LegalTextSource> sources = legalActTexts.stream()
                    .map(text -> new LegalTextSource(text.path(), text.legalText()))
                    .toList();
            requestData = objectMapper.writeValueAsString(new ClassSuggestionPrompt(request, sources));
        } catch (JsonProcessingException exception) {
            throw new LlmException("Class suggestion request could not be serialized", exception);
        }

        LlmCompletionRequest completionRequest = new LlmCompletionRequest(
                SYSTEM_PROMPT,
                SAMPLE_PROMPT + "\n\n" + requestData,
                null,
                null
        );
        ClassSuggestionLlmResponse response = llmClient.completeStructured(
                userId,
                completionRequest,
                "class_suggestions",
                classSuggestionResponseSchema(),
                ClassSuggestionLlmResponse.class
        );
        return response.suggestions();
    }

    private JsonNode classSuggestionResponseSchema() {
        ObjectNode suggestions = objectMapper.createObjectNode()
                .put("type", "array");
        suggestions.set("items", classSuggestionSchema());

        ObjectNode properties = objectMapper.createObjectNode();
        properties.set("suggestions", suggestions);

        ObjectNode schema = objectMapper.createObjectNode()
                .put("type", "object")
                .put("additionalProperties", false);
        schema.set("properties", properties);
        schema.set("required", objectMapper.createArrayNode().add("suggestions"));
        return schema;
    }

    private JsonNode classSuggestionSchema() {
        ObjectNode type = objectMapper.createObjectNode().put("type", "string");
        type.set("enum", objectMapper.valueToTree(TermType.values()));

        ObjectNode specializes = objectMapper.createObjectNode().put("type", "array");
        specializes.set("items", idReferenceSchema());

        ObjectNode properties = objectMapper.createObjectNode();
        properties.set("suggestion_id", stringSchema());
        properties.set("name", localizedStringSchema());
        properties.set("definition", localizedStringSchema());
        properties.set("explanation", localizedStringSchema());
        properties.set("type", type);
        properties.set("specializes", specializes);
        properties.set("legal_act", stringSchema());

        ObjectNode schema = objectMapper.createObjectNode()
                .put("type", "object")
                .put("additionalProperties", false);
        schema.set("properties", properties);
        schema.set("required", objectMapper.createArrayNode()
                        .add("suggestion_id")
                        .add("name")
                        .add("definition")
                        .add("explanation")
                        .add("type")
                        .add("specializes")
                        .add("legal_act"));
        return schema;
    }

    private JsonNode localizedStringSchema() {
        ObjectNode schema = objectMapper.createObjectNode()
                .put("type", "object")
                .put("additionalProperties", false);
        schema.set("properties", objectMapper.createObjectNode().set("cs", stringSchema()));
        schema.set("required", objectMapper.createArrayNode().add("cs"));
        return schema;
    }

    private JsonNode idReferenceSchema() {
        ObjectNode schema = objectMapper.createObjectNode()
                .put("type", "object")
                .put("additionalProperties", false);
        schema.set("properties", objectMapper.createObjectNode().set("id", stringSchema()));
        schema.set("required", objectMapper.createArrayNode().add("id"));
        return schema;
    }

    private JsonNode stringSchema() {
        return objectMapper.createObjectNode().put("type", "string");
    }

    private record ClassSuggestionPrompt(
            ClassSuggestionJobRequest request,
            List<LegalTextSource> legalTexts
    ) {
    }

    private record LegalTextSource(String path, String legalText) {
    }
}
