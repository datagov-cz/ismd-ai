package cz.dia.ismd.assistant.model.suggestion.vocabulary;

import cz.dia.ismd.assistant.model.suggestion.LangString;
import cz.dia.ismd.assistant.model.suggestion.TermType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.stream.Stream;

@Schema(description = "Immutable snapshot of a vocabulary proposal. New concepts have server-assigned refs, never final IRIs.")
public record VocabularyDraft(
        Phase phase,
        List<DraftClass> classes,
        List<DraftAttribute> attributes,
        List<DraftRelationship> relationships
) {
    public VocabularyDraft {
        classes = List.copyOf(classes);
        attributes = List.copyOf(attributes);
        relationships = List.copyOf(relationships);
    }

    public static VocabularyDraft empty() {
        return new VocabularyDraft(Phase.CLASSES, List.of(), List.of(), List.of());
    }

    public boolean containsRef(String ref) {
        return Stream.of(classes.stream().map(DraftClass::ref),
                        attributes.stream().map(DraftAttribute::ref),
                        relationships.stream().map(DraftRelationship::ref))
                .flatMap(stream -> stream).anyMatch(ref::equals);
    }

    public enum Phase { CLASSES, PROPERTIES, RELATIONSHIPS, DONE }

    public record DraftClass(String ref, LangString name, LangString definition, LangString explanation,
                             TermType type, List<ConceptReference> specializes, String legalAct) {
        public DraftClass {
            specializes = List.copyOf(specializes);
        }
    }

    public record DraftAttribute(String ref, ConceptReference associatedClass, LangString name,
                                 LangString definition, LangString explanation, String legalAct) {}

    public record DraftRelationship(String ref, ConceptReference sourceClass, ConceptReference targetClass,
                                    LangString name, LangString definition, LangString explanation, String legalAct) {}
}
