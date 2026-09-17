package cz.dia.ismd.assistant.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import cz.dia.ismd.assistant.api.suggestion.classsuggestion.ClassSuggestionJobRequest;
import cz.dia.ismd.assistant.api.suggestion.vocabulary.VocabularyRegenerationJobRequest;
import cz.dia.ismd.assistant.model.legal.LegalActText;
import cz.dia.ismd.assistant.model.llm.ClassSuggestionLlmResponse;
import cz.dia.ismd.assistant.model.llm.LlmCompletionRequest;
import cz.dia.ismd.assistant.model.suggestion.classsuggestion.ClassSuggestion;
import cz.dia.ismd.assistant.model.suggestion.classsuggestion.KnownClassTerm;
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
            This step generates classes only, not their attributes or relationships.
            request.known_conceptual_model contains concepts already known to the caller. Do not generate an
            existing class again under a new identifier, synonym or paraphrase. Existing classes can be used
            as specialization targets with their exact termID values. Return an empty suggestions array if
            the source supports no additional classes. Prior suggestions are context, not independent source
            evidence; ground new definitions and explanations in the supplied legal text.
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
                LlmRequestSupport.withSuggestionLimit(SYSTEM_PROMPT, request.effectiveK()),
                SAMPLE_PROMPT + "\n\n" + requestData,
                null,
                null
        );
        ClassSuggestionLlmResponse response = llmClient.completeStructured(
                userId,
                completionRequest,
                "class_suggestions",
                LlmRequestSupport.classSuggestionResponseSchema(objectMapper, request.effectiveK()),
                ClassSuggestionLlmResponse.class
        );
        return response.suggestions();
    }

    public List<ConceptRegenerationLlmService.Metadata> regenerate(String userId,
                                                                   VocabularyRegenerationJobRequest request,
                                                                   KnownClassTerm target, List<LegalActText> texts) {
        return ConceptRegenerationLlmService.regenerate(llmClient, objectMapper, userId, ConceptRegenerationLlmService.Kind.CLASS, target, request, texts);
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
                completionRequest(requestData, request.effectiveK()),
                "class_suggestions",
                LlmRequestSupport.classSuggestionResponseSchema(objectMapper, request.effectiveK()),
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

    private LlmCompletionRequest completionRequest(String requestData, int maximum) {
        return new LlmCompletionRequest(LlmRequestSupport.withSuggestionLimit(SYSTEM_PROMPT, maximum), SAMPLE_PROMPT + "\n\n" + requestData, null, null);
    }
}
