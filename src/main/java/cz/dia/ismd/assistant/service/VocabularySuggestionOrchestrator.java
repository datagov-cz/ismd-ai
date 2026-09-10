package cz.dia.ismd.assistant.service;

import cz.dia.ismd.assistant.api.suggestion.attribute.PropertySuggestionJobRequest;
import cz.dia.ismd.assistant.api.suggestion.classsuggestion.ClassSuggestionJobRequest;
import cz.dia.ismd.assistant.api.suggestion.relationship.RelationshipSuggestionJobRequest;
import cz.dia.ismd.assistant.api.suggestion.vocabulary.VocabularySuggestionJobRequest;
import cz.dia.ismd.assistant.api.suggestion.vocabulary.VocabularyExpansionJobRequest;
import cz.dia.ismd.assistant.api.suggestion.vocabulary.VocabularyRegenerationJobRequest;
import cz.dia.ismd.assistant.exception.LlmException;
import cz.dia.ismd.assistant.model.legal.LegalActText;
import cz.dia.ismd.assistant.model.suggestion.*;
import cz.dia.ismd.assistant.model.suggestion.attribute.AttributeSuggestion;
import cz.dia.ismd.assistant.model.suggestion.attribute.KnownAttributeTerm;
import cz.dia.ismd.assistant.model.suggestion.classsuggestion.ClassSuggestion;
import cz.dia.ismd.assistant.model.suggestion.classsuggestion.KnownClassTerm;
import cz.dia.ismd.assistant.model.suggestion.relationship.KnownRelationshipTerm;
import cz.dia.ismd.assistant.model.suggestion.relationship.RelationshipSuggestion;
import cz.dia.ismd.assistant.model.suggestion.vocabulary.ConceptReference;
import cz.dia.ismd.assistant.model.suggestion.vocabulary.VocabularyDraft;
import cz.dia.ismd.assistant.model.suggestion.vocabulary.VocabularyDraft.*;

import java.net.URI;
import java.text.Normalizer;
import java.util.*;
import java.util.function.Consumer;

/** One synchronous pipeline, invoked once on a background worker. No child jobs or HTTP polling. */
final class VocabularySuggestionOrchestrator {
    private final ClassSuggestionLlmService classes;
    private final PropertySuggestionLlmService properties;
    private final RelationshipSuggestionLlmService relationships;

    VocabularySuggestionOrchestrator(ClassSuggestionLlmService classes, PropertySuggestionLlmService properties,
                                     RelationshipSuggestionLlmService relationships) {
        this.classes = classes;
        this.properties = properties;
        this.relationships = relationships;
    }

    void generate(String userId, VocabularySuggestionJobRequest request, List<LegalActText> texts,
                  Consumer<VocabularyDraft> progress) {
        Session session = new Session(request.knownConceptualModel(), texts, progress);
        generateClasses(userId, new ClassSuggestionJobRequest(request.effectiveClassCount(),
                request.structuralElementIds(), request.contextText(), session.known), texts, session);
        session.publish(Phase.PROPERTIES);
        for (DraftClass c : session.draftClasses) {
            if (request.effectivePropertiesPerClass() == 0) break;
            var generatedProperties = batch(properties.suggestProperties(userId, new PropertySuggestionJobRequest(
                    request.effectivePropertiesPerClass(), c.ref(), request.structuralElementIds(),
                    request.contextText(), session.known), texts), request.effectivePropertiesPerClass());
            acceptProperties(session, generatedProperties, c.ref());
        }
        session.publish(Phase.RELATIONSHIPS);
        for (DraftClass c : session.draftClasses) {
            if (request.effectiveRelationshipsPerClass() == 0) break;
            var generatedRelationships = batch(relationships.suggestRelationships(userId, new RelationshipSuggestionJobRequest(
                    request.effectiveRelationshipsPerClass(), c.ref(), request.structuralElementIds(),
                    request.contextText(), session.known), texts), request.effectiveRelationshipsPerClass());
            acceptRelationships(session, generatedRelationships, c.ref());
        }
        session.publish(Phase.DONE);
    }

    private void generateClasses(String userId, ClassSuggestionJobRequest request, List<LegalActText> texts,
                                 Session session) {
        List<ClassSuggestion> generated = batch(classes.suggestClasses(userId, request, texts), request.effectiveK());
        // Allocate every class ref before resolving specialization, including forward references.
        // Names (even with matching types/definitions) do not establish concept identity.
        // Keep each suggestion distinct and resolve references only by explicit IDs.
        Map<String, String> classRefs = new HashMap<>();
        Set<String> providerIds = new HashSet<>();
        for (ClassSuggestion c : generated) {
            if (c == null || c.suggestionID() == null || c.suggestionID().isBlank()
                    || session.usedIds.contains(c.suggestionID()) || !providerIds.add(c.suggestionID())) {
                throw new LlmException("Generated class identifiers must be non-blank, unique and distinct from known terms");
            }
            name(c.name());
            String ref = session.newRef("class");
            classRefs.put(c.suggestionID(), ref);
        }
        for (ClassSuggestion c : generated) {
            String ref = classRefs.get(c.suggestionID());
            List<ConceptReference> specializes = list(c.specializes()).stream()
                    .map(id -> session.resolveGeneratedClass(id, classRefs)).toList();
            if (specializes.stream().anyMatch(parent -> ref.equals(parent.ref()))) {
                throw new LlmException("A generated class cannot specialize itself");
            }
            if (c.type() == null) throw new LlmException("Generated class type is missing");
            session.draftClasses.add(new DraftClass(ref, name(c.name()), c.definition(), c.explanation(),
                    c.type(), specializes, session.legalAct(c.legalAct())));
        }
        for (DraftClass c : session.draftClasses) {
            session.knownClasses.add(new KnownClassTerm(c.ref(), c.name(), c.definition(), c.explanation(), c.type(),
                    c.specializes().stream().map(VocabularySuggestionOrchestrator::id).toList(), c.legalAct()));
            session.classReferences.put(c.ref(), new ConceptReference(c.ref(), null));
        }
    }

    private void acceptProperties(Session session,
            List<AttributeSuggestion> generatedProperties,
            String selectedClassId) {
        List<DraftAttribute> accepted = new ArrayList<>();
        for (var a : generatedProperties) {
            if (a == null) throw new LlmException("Generated property is null");
            session.requireSelectedClass(a.associatedClass(), selectedClassId);
            name(a.name());
            if (session.knownAttributes.stream().anyMatch(known ->
                    known.associatedClass() != null && selectedClassId.equals(known.associatedClass().id()) && sameName(known.name(), a.name()))
                    || accepted.stream().anyMatch(known -> sameName(known.name(), a.name()))) continue;
            accepted.add(new DraftAttribute(session.newRef("attribute"), session.resolve(a.associatedClass()),
                    name(a.name()), a.definition(), a.explanation(), session.legalAct(a.legalAct())));
        }
        session.draftAttributes.addAll(accepted);
        accepted.forEach(a -> session.knownAttributes.add(new KnownAttributeTerm(a.ref(), id(a.associatedClass()),
                a.name(), a.definition(), a.explanation(), a.legalAct())));
        session.publish(Phase.PROPERTIES);
    }

    private void acceptRelationships(Session session,
            List<RelationshipSuggestion> generatedRelationships,
            String selectedClassId) {
        List<DraftRelationship> accepted = new ArrayList<>();
        for (var r : generatedRelationships) {
            if (r == null) throw new LlmException("Generated relationship is null");
            session.requireSelectedClass(r.sourceClass(), selectedClassId);
            session.resolve(r.targetClass());
            name(r.name());
            if (session.knownRelationships.stream().anyMatch(known ->
                    known.sourceClass() != null && selectedClassId.equals(known.sourceClass().id())
                            && known.targetClass() != null && r.targetClass().id().equals(known.targetClass().id()) && sameName(known.name(), r.name()))
                    || accepted.stream().anyMatch(known -> id(known.targetClass()).id().equals(r.targetClass().id())
                            && sameName(known.name(), r.name()))) continue;
            accepted.add(new DraftRelationship(session.newRef("relationship"), session.resolve(r.sourceClass()),
                    session.resolve(r.targetClass()), name(r.name()), r.definition(), r.explanation(),
                    session.legalAct(r.legalAct())));
        }
        session.draftRelationships.addAll(accepted);
        accepted.forEach(r -> session.knownRelationships.add(new KnownRelationshipTerm(r.ref(), id(r.sourceClass()),
                id(r.targetClass()), r.name(), r.definition(), r.explanation(), r.legalAct())));
        session.publish(Phase.RELATIONSHIPS);
    }

    static boolean sameName(LangString left, LangString right) {
        return left != null && right != null && normalizedName(left).equals(normalizedName(right));
    }

    private static String normalizedName(LangString name) {
        return Normalizer.normalize(name.values().getOrDefault("cs", ""), Normalizer.Form.NFC)
                .strip().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    void expand(String userId, VocabularyExpansionJobRequest request, List<LegalActText> texts,
                Consumer<VocabularyDraft> progress) {
        Session session = new Session(request.knownConceptualModel(), texts, progress);
        if (request.kind() == VocabularyExpansionJobRequest.Kind.CLASSES) {
            generateClasses(userId, new ClassSuggestionJobRequest(request.effectiveCount(),
                    request.structuralElementIds(), request.contextText(), session.known), texts, session);
        } else if (request.kind() == VocabularyExpansionJobRequest.Kind.PROPERTIES) {
            acceptProperties(session, batch(properties.suggestProperties(userId, new PropertySuggestionJobRequest(
                    request.effectiveCount(), request.selectedClassId(), request.structuralElementIds(),
                    request.contextText(), session.known), texts), request.effectiveCount()), request.selectedClassId());
        } else {
            acceptRelationships(session, batch(relationships.suggestRelationships(userId, new RelationshipSuggestionJobRequest(
                    request.effectiveCount(), request.selectedClassId(), request.structuralElementIds(),
                    request.contextText(), session.known), texts), request.effectiveCount()), request.selectedClassId());
        }
        session.publish(Phase.DONE);
    }

    void regenerate(String userId, VocabularyRegenerationJobRequest request, List<LegalActText> texts,
                    Consumer<VocabularyDraft> progress) {
        Session session = new Session(request.knownConceptualModel(), texts, progress);
        String ref = request.conceptRef();
        var targetClass = session.knownClasses.stream().filter(c -> c.termID().equals(ref)).findFirst();
        var targetAttribute = session.knownAttributes.stream().filter(a -> a.termID().equals(ref)).findFirst();
        if (targetClass.isPresent()) {
            var target = targetClass.orElseThrow();
            session.publish(Phase.CLASSES);
            for (var m : batch(classes.regenerate(userId, request, target, texts), 1)) {
                session.draftClasses.add(new DraftClass(ref, name(m.name()), m.definition(), m.explanation(),
                        target.type(), list(target.specializes()).stream().map(session::resolve).toList(), session.legalAct(m.legalAct())));
            }
        } else if (targetAttribute.isPresent()) {
            var target = targetAttribute.orElseThrow();
            session.publish(Phase.PROPERTIES);
            for (var m : batch(properties.regenerate(userId, request, target, texts), 1)) {
                session.draftAttributes.add(new DraftAttribute(ref, session.resolve(target.associatedClass()),
                        name(m.name()), m.definition(), m.explanation(), session.legalAct(m.legalAct())));
            }
        } else {
            var target = session.knownRelationships.stream().filter(r -> r.termID().equals(ref)).findFirst().orElseThrow();
            session.publish(Phase.RELATIONSHIPS);
            for (var m : batch(relationships.regenerate(userId, request, target, texts), 1)) {
                session.draftRelationships.add(new DraftRelationship(ref, session.resolve(target.sourceClass()),
                        session.resolve(target.targetClass()), name(m.name()), m.definition(), m.explanation(), session.legalAct(m.legalAct())));
            }
        }
        session.publish(Phase.DONE);
    }

    private static <T> List<T> list(List<T> value) { return value == null ? List.of() : value; }

    private static <T> List<T> batch(List<T> value, int maximum) {
        if (value == null) throw new LlmException("LLM returned no suggestion array");
        if (value.size() > maximum) throw new LlmException(
                "LLM returned " + value.size() + " suggestions, exceeding the requested count limit of " + maximum);
        return value;
    }

    private static LangString name(LangString value) {
        if (value == null || value.values().get("cs") == null || value.values().get("cs").isBlank()) {
            throw new LlmException("Generated concept must have a non-blank Czech name");
        }
        return value;
    }

    private static IdReference id(ConceptReference reference) {
        return new IdReference(reference.ref() == null ? reference.iri() : reference.ref());
    }

    private static ConceptReference knownReference(String id) {
        try {
            if (URI.create(id).isAbsolute()) return new ConceptReference(null, id);
        } catch (IllegalArgumentException ignored) {
            // Existing suggestion APIs also allow opaque, non-URI term IDs for unsaved terms.
        }
        return new ConceptReference(id, null);
    }

    private static final class Session {
        private final List<KnownClassTerm> knownClasses;
        private final List<KnownAttributeTerm> knownAttributes;
        private final List<KnownRelationshipTerm> knownRelationships;
        private final KnownConceptualModel known;
        private final Map<String, ConceptReference> classReferences = new HashMap<>();
        private final Set<String> usedIds = new HashSet<>();
        private final List<DraftClass> draftClasses = new ArrayList<>();
        private final List<DraftAttribute> draftAttributes = new ArrayList<>();
        private final List<DraftRelationship> draftRelationships = new ArrayList<>();
        private final List<LegalActText> texts;
        private final Consumer<VocabularyDraft> progress;

        Session(KnownConceptualModel known, List<LegalActText> texts, Consumer<VocabularyDraft> progress) {
            // One worker owns these lists. Reuse a read-only view for synchronous LLM calls.
            knownClasses = new ArrayList<>(known == null ? List.of() : list(known.classes()));
            knownAttributes = new ArrayList<>(known == null ? List.of() : list(known.attributes()));
            knownRelationships = new ArrayList<>(known == null ? List.of() : list(known.relationships()));
            this.known = new KnownConceptualModel(Collections.unmodifiableList(knownClasses),
                    Collections.unmodifiableList(knownAttributes), Collections.unmodifiableList(knownRelationships));
            knownClasses.forEach(c -> { classReferences.put(c.termID(), knownReference(c.termID())); usedIds.add(c.termID()); });
            knownAttributes.forEach(a -> usedIds.add(a.termID()));
            knownRelationships.forEach(r -> usedIds.add(r.termID()));
            this.texts = texts;
            this.progress = progress;
        }

        String newRef(String kind) {
            String ref;
            do { ref = kind + "-" + UUID.randomUUID(); } while (!usedIds.add(ref));
            return ref;
        }

        ConceptReference resolve(IdReference reference) {
            ConceptReference result = reference == null ? null : classReferences.get(reference.id());
            if (result == null) throw new LlmException("Generated reference does not identify a class in the working model");
            return result;
        }

        ConceptReference resolveGeneratedClass(IdReference reference, Map<String, String> classRefs) {
            String ref = reference == null ? null : classRefs.get(reference.id());
            return ref == null ? resolve(reference) : new ConceptReference(ref, null);
        }

        void requireSelectedClass(IdReference reference, String selected) {
            if (reference == null || !selected.equals(reference.id())) {
                throw new LlmException("Generated domain does not match the selected class");
            }
        }

        String legalAct(String source) {
            if (source == null || source.isBlank()) throw new LlmException("Generated legal source is missing");
            int marker = source.indexOf("/eli/cz/sb/");
            String path = marker >= 0 ? source.substring(marker + 11) : source;
            if (!path.matches("[0-9]{1,4}/[0-9]+(?:/[A-Za-z0-9_./:-]+)?") || texts.stream().noneMatch(
                    text -> text.path().equals(path) || text.path().startsWith(path + "/"))) {
                throw new LlmException("Generated legal source does not match the supplied legal texts");
            }
            return "https://e-sbirka.gov.cz/eli/cz/sb/" + path;
        }

        void publish(Phase phase) {
            progress.accept(new VocabularyDraft(phase, draftClasses, draftAttributes, draftRelationships));
        }
    }
}
