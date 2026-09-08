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
import java.util.function.Consumer;

@Service
public class PropertySuggestionLlmService {

    private static final String SYSTEM_PROMPT = """
            Generate only value-bearing attributes of the selected class, supported by the supplied legal_texts.
            In this task, a property means an attribute whose value is a literal, such as text, a number, a date,
            or a boolean. It describes a characteristic of an instance of the selected class.
            A connection, interaction, action, or association between instances of two classes is a relationship,
            not an attribute. Do not return such a connection as an attribute, even if it can be described in text.
            Do not turn a class definition into an attribute or rename a relationship to make it look like one.
            Inspect all supplied fragments for measurements and characteristics of the selected class, including
            those mentioned in conditions, limits or inspection duties. The measurable characteristic may be an
            attribute; the act of inspecting it is not. Preserve the source's scope and conditions instead of
            applying a threshold from a particular situation to every instance of the class.

            Illustrative distinctions, not additional source evidence:
            - A vehicle's colour can be an attribute if the supplied text supports it.
            - A driver driving a vehicle is a relationship and must not appear in this response.
            - If the source only defines driver and vehicle and connects them by driving, without describing
              any value-bearing characteristics, return {"suggestions":[]} for the attribute step.

            Find the selected class in request.known_conceptual_model.classes by its termID.
            Every associated_class.id must equal request.selected_class_id exactly.
            Use the known model as context, not as a list of outputs to repeat. Do not propose an attribute
            already present for this selected class, including a synonym or paraphrase of the same attribute.
            An attribute belonging to a different class is not automatically a duplicate, but a new proposal
            still needs source evidence for the selected class. Known relationships must not be copied into
            attributes. Prior suggestions are not independent evidence from the legal text.

            Return fewer than request.k suggestions, including an empty array, whenever the source does not
            support enough new attributes. Never invent an attribute to fill the requested count.
            Each source has a path and legal_text; use its supplied path as the legal_act reference.
            Use concise Czech names for characteristics, with Czech localized definitions and explanations
            grounded in the supplied source. Do not add unsupported duties or other background knowledge.
            """;
    private static final String USER_PROMPT =
            "Find additional value-bearing characteristics of the selected class in all supplied fragments; return only supported, previously unknown attributes:";

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
                new LlmCompletionRequest(LlmRequestSupport.withSuggestionLimit(SYSTEM_PROMPT, request.effectiveK()), USER_PROMPT + "\n\n" + requestData, null, null),
                "property_suggestions",
                LlmRequestSupport.withSelectedClass(
                        LlmRequestSupport.propertySuggestionResponseSchema(objectMapper, request.effectiveK()),
                        objectMapper, "associated_class", request.selectedClassId()),
                PropertySuggestionLlmResponse.class
        );
        return response.suggestions();
    }

    public List<ConceptRegenerationLlmService.Metadata> regenerate(String userId,
            cz.dia.ismd.assistant.api.suggestion.vocabulary.VocabularyRegenerationJobRequest request,
            cz.dia.ismd.assistant.model.suggestion.attribute.KnownAttributeTerm target, List<LegalActText> texts) {
        return ConceptRegenerationLlmService.regenerate(llmClient, objectMapper, userId, ConceptRegenerationLlmService.Kind.ATTRIBUTE, target, request, texts);
    }

    public StreamingSuggestions<AttributeSuggestion> streamProperties(
            String userId,
            PropertySuggestionJobRequest request,
            List<LegalActText> legalActTexts,
            Consumer<AttributeSuggestion> suggestionConsumer
    ) {
        Objects.requireNonNull(suggestionConsumer, "suggestionConsumer");
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(legalActTexts, "legalActTexts");
        String requestData = LlmRequestSupport.serializePrompt(objectMapper, request, legalActTexts, "Property");
        PropertySuggestionLlmResponse response = llmClient.completeStructuredStreaming(
                userId,
                new LlmCompletionRequest(LlmRequestSupport.withSuggestionLimit(SYSTEM_PROMPT, request.effectiveK()), USER_PROMPT + "\n\n" + requestData, null, null),
                "property_suggestions",
                LlmRequestSupport.withSelectedClass(
                        LlmRequestSupport.propertySuggestionResponseSchema(objectMapper, request.effectiveK()),
                        objectMapper, "associated_class", request.selectedClassId()),
                PropertySuggestionLlmResponse.class,
                AttributeSuggestion.class,
                suggestionConsumer
        );
        return new StreamingSuggestions<>(response.suggestions());
    }
}
