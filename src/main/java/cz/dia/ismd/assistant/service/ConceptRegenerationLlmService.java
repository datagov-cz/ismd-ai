package cz.dia.ismd.assistant.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import cz.dia.ismd.assistant.api.suggestion.vocabulary.VocabularyRegenerationJobRequest;
import cz.dia.ismd.assistant.model.legal.LegalActText;
import cz.dia.ismd.assistant.model.llm.LlmCompletionRequest;
import cz.dia.ismd.assistant.model.suggestion.KnownConceptualModel;
import cz.dia.ismd.assistant.model.suggestion.LangString;

import java.util.List;

/** Metadata-only LLM contract: refs and graph edges never come from this response. */
public final class ConceptRegenerationLlmService {
    private ConceptRegenerationLlmService() {}

    static List<Metadata> regenerate(LlmClient client, ObjectMapper mapper, String userId, Kind kind,
                                    Object target, VocabularyRegenerationJobRequest request, List<LegalActText> texts) {
        String semantics = switch (kind) {
            case CLASS -> "The target is a class. Preserve its identity and scope, including qualifications or exceptions in the source.";
            case ATTRIBUTE -> "The target is a value-bearing attribute of its associated class, such as text, a number, a date or a boolean. "
                    + "Do not describe a connection between classes as an attribute or replace it with an unrelated characteristic.";
            case RELATIONSHIP -> "The target is a relationship between its fixed source and target classes. "
                    + "Use a specific Czech predicate read from source to target. Reverse-direction relationships may coexist. "
                    + "Read the result as SOURCE CLASS + name + TARGET CLASS; the source is the grammatical subject. "
                    + "Correct an incorrectly oriented name using the fixed endpoints and the source evidence. "
                    + "Do not turn it into a value-bearing attribute or change its direction or meaning.";
        };
        String instructions = """
                Regenerate only the Czech name, definition, explanation and legal_act reference of request.target_concept.
                This is an explicit rewrite of an existing unsaved concept, not a request for additional concepts.
                The target's ref, kind, class type and all graph edges are fixed by the server and must remain unchanged.
                Use request.known_conceptual_model to understand the target and its connected classes, not as source evidence.
                request.context_text may guide wording and emphasis but must not replace the target with another concept.
                Ground every claim in the supplied legal_texts. Retain relevant conditions and exceptions; do not add duties
                or background facts absent from the source. Use a supplied supporting source path for legal_act.
                Return at most one suggestion. If the target cannot be described as the required kind from these sources,
                return {"suggestions":[]} instead of inventing content. Return concise Czech localized strings.
                """ + semantics;
        String prompt = LlmRequestSupport.serializePrompt(mapper,
                new RewriteContext(request.contextText(), request.knownConceptualModel(), target), texts, "Regeneration");
        return client.completeStructured(userId, new LlmCompletionRequest(instructions, prompt, null, null),
                "concept_regeneration", LlmRequestSupport.regenerationResponseSchema(mapper), Response.class).suggestions();
    }

    enum Kind { CLASS, ATTRIBUTE, RELATIONSHIP }

    public record Metadata(LangString name, LangString definition, LangString explanation, String legalAct) {}
    record Response(List<Metadata> suggestions) {}
    private record RewriteContext(String contextText, KnownConceptualModel knownConceptualModel, Object targetConcept) {}
}
