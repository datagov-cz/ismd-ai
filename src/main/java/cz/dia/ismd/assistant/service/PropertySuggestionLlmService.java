package cz.dia.ismd.assistant.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import cz.dia.ismd.assistant.api.suggestion.attribute.PropertySuggestionJobRequest;
import cz.dia.ismd.assistant.model.legal.LegalActText;
import cz.dia.ismd.assistant.model.llm.LlmCompletionRequest;
import cz.dia.ismd.assistant.model.llm.PropertySuggestionLlmResponse;
import cz.dia.ismd.assistant.model.suggestion.attribute.AttributeSuggestion;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

@Service
public class PropertySuggestionLlmService {

    private static final String SYSTEM_PROMPT = """
            You identify conceptual-model properties of a selected class in legal text. Return only property
            suggestions supported by the supplied request data and legal_texts sources. The associated_class id of
            every suggestion must equal request.selected_class_id. Each source keeps its legal_text associated with
            its path; use that path to identify the legal_act source. Use Czech localized strings and the requested
            maximum number of suggestions.
            """;
    private static final String USER_PROMPT =
            "Suggest conceptual-model properties for this property-suggestion job request:";

    private final LlmClient llmClient;
    private final ObjectMapper objectMapper;

    public PropertySuggestionLlmService(LlmClient llmClient, ObjectMapper objectMapper) {
        this.llmClient = llmClient;
        this.objectMapper = objectMapper;
    }

    public List<AttributeSuggestion> suggestProperties(
            String userId,
            PropertySuggestionJobRequest request,
            List<LegalActText> legalActTexts
    ) {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(legalActTexts, "legalActTexts");
        String requestData = LlmRequestSupport.serializePrompt(
                objectMapper, request, legalActTexts, "Property");
        PropertySuggestionLlmResponse response = llmClient.completeStructured(
                userId,
                new LlmCompletionRequest(SYSTEM_PROMPT, USER_PROMPT + "\n\n" + requestData, null, null),
                "property_suggestions",
                LlmRequestSupport.propertySuggestionResponseSchema(objectMapper),
                PropertySuggestionLlmResponse.class
        );
        return response.suggestions();
    }
}
