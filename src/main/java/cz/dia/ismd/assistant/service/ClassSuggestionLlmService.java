package cz.dia.ismd.assistant.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import cz.dia.ismd.assistant.api.suggestion.classsuggestion.ClassSuggestionJobRequest;
import cz.dia.ismd.assistant.model.legal.LegalActText;
import cz.dia.ismd.assistant.model.llm.ClassSuggestionLlmResponse;
import cz.dia.ismd.assistant.model.llm.LlmCompletionRequest;
import cz.dia.ismd.assistant.model.suggestion.classsuggestion.ClassSuggestion;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

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
        String requestData = LlmRequestSupport.serializePrompt(
                objectMapper, request, legalActTexts, "Class");

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
                LlmRequestSupport.classSuggestionResponseSchema(objectMapper),
                ClassSuggestionLlmResponse.class
        );
        return response.suggestions();
    }

    public StreamingSuggestions<ClassSuggestion> streamClasses(
            String userId,
            ClassSuggestionJobRequest request,
            List<LegalActText> legalActTexts,
            Consumer<ClassSuggestion> suggestionConsumer
    ) {
        Objects.requireNonNull(suggestionConsumer, "suggestionConsumer");
        String requestData = requestData(userId, request, legalActTexts);
        ClassSuggestionLlmResponse response = llmClient.completeStructuredStreaming(
                userId,
                completionRequest(requestData),
                "class_suggestions",
                LlmRequestSupport.classSuggestionResponseSchema(objectMapper),
                ClassSuggestionLlmResponse.class,
                ClassSuggestion.class,
                suggestionConsumer
        );
        return new StreamingSuggestions<>(response.suggestions());
    }

    private String requestData(String userId, ClassSuggestionJobRequest request, List<LegalActText> legalActTexts) {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(legalActTexts, "legalActTexts");
        return LlmRequestSupport.serializePrompt(objectMapper, request, legalActTexts, "Class");
    }

    private LlmCompletionRequest completionRequest(String requestData) {
        return new LlmCompletionRequest(SYSTEM_PROMPT, SAMPLE_PROMPT + "\n\n" + requestData, null, null);
    }
}
