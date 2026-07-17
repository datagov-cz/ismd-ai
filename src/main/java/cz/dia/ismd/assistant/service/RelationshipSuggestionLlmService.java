package cz.dia.ismd.assistant.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import cz.dia.ismd.assistant.api.suggestion.relationship.RelationshipSuggestionJobRequest;
import cz.dia.ismd.assistant.model.legal.LegalActText;
import cz.dia.ismd.assistant.model.llm.LlmCompletionRequest;
import cz.dia.ismd.assistant.model.llm.RelationshipSuggestionLlmResponse;
import cz.dia.ismd.assistant.model.suggestion.relationship.RelationshipSuggestion;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

@Service
public class RelationshipSuggestionLlmService {

    private static final String SYSTEM_PROMPT = """
            You identify conceptual-model relationships of a selected class in legal text. Return only relationship
            suggestions supported by the supplied request data and legal_texts sources. The source_class id of every
            suggestion must equal request.selected_class_id, and referenced classes must come from the known
            conceptual model. Each source keeps its legal_text associated with its path; use that path to identify
            the legal_act source. Use Czech localized strings and the requested maximum number of suggestions.
            """;
    private static final String USER_PROMPT =
            "Suggest conceptual-model relationships for this relationship-suggestion job request:";

    private final LlmClient llmClient;
    private final ObjectMapper objectMapper;

    public RelationshipSuggestionLlmService(LlmClient llmClient, ObjectMapper objectMapper) {
        this.llmClient = llmClient;
        this.objectMapper = objectMapper;
    }

    public List<RelationshipSuggestion> suggestRelationships(
            String userId,
            RelationshipSuggestionJobRequest request,
            List<LegalActText> legalActTexts
    ) {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(legalActTexts, "legalActTexts");
        String requestData = LlmRequestSupport.serializePrompt(
                objectMapper, request, legalActTexts, "Relationship");
        RelationshipSuggestionLlmResponse response = llmClient.completeStructured(
                userId,
                new LlmCompletionRequest(SYSTEM_PROMPT, USER_PROMPT + "\n\n" + requestData, null, null),
                "relationship_suggestions",
                LlmRequestSupport.relationshipSuggestionResponseSchema(objectMapper),
                RelationshipSuggestionLlmResponse.class
        );
        return response.suggestions();
    }
}
