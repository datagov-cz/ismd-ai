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
import java.util.function.Consumer;

@Service
public class RelationshipSuggestionLlmService {

    private static final String SYSTEM_PROMPT = """
            Generate only relationships between classes, supported by the supplied legal_texts.
            A relationship connects an instance of the source class to an instance of the target class.
            Value-bearing characteristics such as text, numbers, dates, or booleans belong to attributes and
            must not be returned as relationships. Do not invent a target class for a literal value.

            Every source_class.id must equal request.selected_class_id exactly. Both source_class.id and
            target_class.id must be existing termID values in request.known_conceptual_model.classes.
            Name each relationship as a specific predicate read from source to target, rather than a generic
            label such as "Vztah mezi A a B". Its definition and explanation must agree with that direction.
            Before returning each item, read it as a Czech sentence: SOURCE CLASS + name + TARGET CLASS.
            The source class is the grammatical subject, even when this requires a passive predicate.
            Opposite directions are allowed: for example, driver -- drives --> vehicle and vehicle -- is driven
            by --> driver may both be valid. This example illustrates direction only; generate such a relationship
            only when supported by the supplied source. Do not reject a candidate merely because the reverse
            direction already exists in the known model.

            Use the known model as context, not as a list of outputs to repeat. Do not propose the same relationship
            meaning for the same directed source and target if already present, even under a synonymous name.
            Different relationship meanings between the same classes may be valid. Prior suggestions are not
            independent evidence from the legal text; do not copy their wording without source support.
            Return fewer than request.k suggestions, including an empty array, if no further supported
            relationships exist. Do not invent a relationship to fill the count.

            Each source has a path and legal_text; use its supplied path as the legal_act reference.
            Use Czech localized names, definitions and explanations, grounded in the supplied source.
            """;
    private static final String USER_PROMPT =
            "Return only additional relationships whose grammatical subject is the selected source class. Do not reproduce the known relationship list:";

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
                new LlmCompletionRequest(LlmRequestSupport.withSuggestionLimit(SYSTEM_PROMPT, request.effectiveK()), USER_PROMPT + "\n\n" + requestData, null, null),
                "relationship_suggestions",
                LlmRequestSupport.withSelectedClass(
                        LlmRequestSupport.relationshipSuggestionResponseSchema(objectMapper, request.effectiveK()),
                        objectMapper, "source_class", request.selectedClassId()),
                RelationshipSuggestionLlmResponse.class
        );
        return response.suggestions();
    }

    public List<ConceptRegenerationLlmService.Metadata> regenerate(String userId,
            cz.dia.ismd.assistant.api.suggestion.vocabulary.VocabularyRegenerationJobRequest request,
            cz.dia.ismd.assistant.model.suggestion.relationship.KnownRelationshipTerm target, List<LegalActText> texts) {
        return ConceptRegenerationLlmService.regenerate(llmClient, objectMapper, userId, ConceptRegenerationLlmService.Kind.RELATIONSHIP, target, request, texts);
    }

    public StreamingSuggestions<RelationshipSuggestion> streamRelationships(
            String userId,
            RelationshipSuggestionJobRequest request,
            List<LegalActText> legalActTexts,
            Consumer<RelationshipSuggestion> suggestionConsumer
    ) {
        Objects.requireNonNull(suggestionConsumer, "suggestionConsumer");
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(legalActTexts, "legalActTexts");
        String requestData = LlmRequestSupport.serializePrompt(objectMapper, request, legalActTexts, "Relationship");
        RelationshipSuggestionLlmResponse response = llmClient.completeStructuredStreaming(
                userId,
                new LlmCompletionRequest(LlmRequestSupport.withSuggestionLimit(SYSTEM_PROMPT, request.effectiveK()), USER_PROMPT + "\n\n" + requestData, null, null),
                "relationship_suggestions",
                LlmRequestSupport.withSelectedClass(
                        LlmRequestSupport.relationshipSuggestionResponseSchema(objectMapper, request.effectiveK()),
                        objectMapper, "source_class", request.selectedClassId()),
                RelationshipSuggestionLlmResponse.class,
                RelationshipSuggestion.class,
                suggestionConsumer
        );
        return new StreamingSuggestions<>(response.suggestions());
    }
}
