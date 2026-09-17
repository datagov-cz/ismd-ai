package cz.dia.ismd.assistant.service;

import cz.dia.ismd.assistant.api.suggestion.vocabulary.VocabularyExpansionJobRequest;
import cz.dia.ismd.assistant.api.suggestion.vocabulary.VocabularyRegenerationJobRequest;
import cz.dia.ismd.assistant.exception.InvalidVocabularyRequestException;
import cz.dia.ismd.assistant.model.suggestion.IdReference;
import cz.dia.ismd.assistant.model.suggestion.KnownConceptualModel;
import cz.dia.ismd.assistant.model.suggestion.LangString;
import cz.dia.ismd.assistant.model.suggestion.attribute.KnownAttributeTerm;
import cz.dia.ismd.assistant.model.suggestion.classsuggestion.KnownClassTerm;
import cz.dia.ismd.assistant.model.suggestion.relationship.KnownRelationshipTerm;

import java.net.URI;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

/** Validates inputs before enqueueing; does not copy or modify them. */
final class VocabularyRequestValidator {
    private VocabularyRequestValidator() {}

    static void validateKnownModel(KnownConceptualModel model) {
        if (model == null) return;
        if (list(model.classes()).size() + list(model.attributes()).size() + list(model.relationships()).size() > 1000)
            throw new InvalidVocabularyRequestException("Known model exceeds 1000 terms");
        Set<String> ids = new HashSet<>();
        validateTerms(model.classes(), KnownClassTerm::termID, ids);
        validateTerms(model.attributes(), KnownAttributeTerm::termID, ids);
        validateTerms(model.relationships(), KnownRelationshipTerm::termID, ids);
        for (var c : list(model.classes())) {
            if (list(c.specializes()).stream().anyMatch(Objects::isNull))
                throw new InvalidVocabularyRequestException("Known model must not contain null terms or references");
        }
    }

    private static <T> void validateTerms(List<T> terms, Function<T, String> identifier, Set<String> ids) {
        for (T term : list(terms)) {
            if (term == null)
                throw new InvalidVocabularyRequestException("Known model must not contain null terms or references");
            String id = identifier.apply(term);
            if (id == null || id.isBlank() || !ids.add(id))
                throw new InvalidVocabularyRequestException("Known termID values must be non-blank and unique");
        }
    }

    static void validateWorkingModel(KnownConceptualModel model) {
        if (model == null) throw new InvalidVocabularyRequestException("known_conceptual_model is required");
        validateKnownModel(model);
        Set<String> classes = new HashSet<>();
        list(model.classes()).forEach(c -> classes.add(c.termID()));
        Consumer<IdReference> requireClass = ref -> {
            if (ref == null || !classes.contains(ref.id())) throw new InvalidVocabularyRequestException(
                    "Every working-model reference must identify a supplied class");
        };
        list(model.classes()).forEach(c -> {
            requireKnownName(c.name());
            if (c.type() == null) throw new InvalidVocabularyRequestException("Known class type is required");
            list(c.specializes()).forEach(requireClass);
        });
        list(model.attributes()).forEach(a -> { requireKnownName(a.name()); requireClass.accept(a.associatedClass()); });
        list(model.relationships()).forEach(r -> {
            requireKnownName(r.name()); requireClass.accept(r.sourceClass()); requireClass.accept(r.targetClass());
        });
    }

    static void validateExpansion(VocabularyExpansionJobRequest request) {
        validateWorkingModel(request.knownConceptualModel());
        if (request.kind() == null) throw new InvalidVocabularyRequestException("kind is required");
        if (request.kind() == VocabularyExpansionJobRequest.Kind.CLASSES) {
            if (request.selectedClassId() != null) throw new InvalidVocabularyRequestException(
                    "selected_class_id must be omitted when expanding classes");
        } else if (list(request.knownConceptualModel().classes()).stream()
                .noneMatch(c -> c.termID().equals(request.selectedClassId()))) {
            throw new InvalidVocabularyRequestException("selected_class_id must identify a supplied class");
        }
    }

    static void validateRegeneration(VocabularyRegenerationJobRequest request) {
        KnownConceptualModel known = request.knownConceptualModel();
        validateWorkingModel(known);
        String ref = request.conceptRef();
        boolean found = list(known.classes()).stream().anyMatch(c -> c.termID().equals(ref))
                || list(known.attributes()).stream().anyMatch(a -> a.termID().equals(ref))
                || list(known.relationships()).stream().anyMatch(r -> r.termID().equals(ref));
        if (!found || isAbsoluteIri(ref)) throw new InvalidVocabularyRequestException(
                "concept_ref must identify an unsaved term with an opaque ref in the supplied model");
    }

    private static boolean isAbsoluteIri(String id) {
        try {
            return URI.create(id).isAbsolute();
        } catch (IllegalArgumentException ignored) {
            return false; // Opaque draft refs need not be valid URIs.
        }
    }

    private static void requireKnownName(LangString value) {
        if (value == null || value.values().getOrDefault("cs", "").isBlank())
            throw new InvalidVocabularyRequestException("Known terms must have a non-blank Czech name");
    }

    private static <T> List<T> list(List<T> value) { return value == null ? List.of() : value; }
}
