package cz.dia.ismd.assistant.api;

import cz.dia.ismd.assistant.api.suggestion.vocabulary.VocabularyExpansionJobRequest;
import cz.dia.ismd.assistant.api.suggestion.vocabulary.VocabularyRegenerationJobRequest;
import cz.dia.ismd.assistant.exception.InvalidVocabularyRequestException;
import cz.dia.ismd.assistant.model.suggestion.IdReference;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.net.URI;

import cz.dia.ismd.assistant.api.feedback.FeedbackResponse;
import cz.dia.ismd.assistant.api.job.JobStartResponse;
import cz.dia.ismd.assistant.api.suggestion.attribute.PropertySuggestionsJobResponse;
import cz.dia.ismd.assistant.api.suggestion.classsuggestion.ClassSuggestionsJobResponse;
import cz.dia.ismd.assistant.api.suggestion.relationship.RelationshipSuggestionsJobResponse;
import cz.dia.ismd.assistant.model.job.JobStatus;
import cz.dia.ismd.assistant.model.suggestion.TermType;
import cz.dia.ismd.assistant.model.suggestion.classsuggestion.ClassSuggestion;
import cz.dia.ismd.assistant.model.suggestion.attribute.AttributeSuggestion;
import cz.dia.ismd.assistant.model.suggestion.relationship.RelationshipSuggestion;
import cz.dia.ismd.assistant.model.suggestion.IdReference;
import cz.dia.ismd.assistant.model.suggestion.LangString;
import org.springframework.stereotype.Component;

import cz.dia.ismd.assistant.api.suggestion.vocabulary.VocabularySuggestionsJobResponse;
import cz.dia.ismd.assistant.model.suggestion.vocabulary.ConceptReference;
import cz.dia.ismd.assistant.model.suggestion.vocabulary.VocabularyDraft;
import cz.dia.ismd.assistant.model.suggestion.vocabulary.VocabularyDraft.*;
import java.util.List;
import java.util.UUID;

@Component
public class DevelopmentApiResponses {

    private static final UUID CLASS_JOB_ID = UUID.fromString("00000000-0000-0000-0000-000000000101");
    private static final UUID PROPERTY_JOB_ID = UUID.fromString("00000000-0000-0000-0000-000000000201");
    private static final UUID RELATIONSHIP_JOB_ID = UUID.fromString("00000000-0000-0000-0000-000000000301");
    private static final String CLASS_ID = "mock-class-001";
    private static final String LEGAL_ACT = "/eli/cz/sb/2024/1";

    public JobStartResponse startClassSuggestions() {
        return new JobStartResponse(CLASS_JOB_ID, JobStatus.IN_PROGRESS);
    }

    public List<ClassSuggestionsJobResponse> classSuggestions() {
        return List.of(new ClassSuggestionsJobResponse(
                CLASS_JOB_ID,
                JobStatus.COMPLETED,
                List.of(
                        new ClassSuggestion(
                                "mock-class-suggestion-001",
                                LangString.cs("Development Class"),
                                LangString.cs("A deterministic class suggestion returned in development."),
                                LangString.cs("This response is independent of the request body."),
                                TermType.CLASS,
                                List.of(),
                                LEGAL_ACT
                        ),
                        new ClassSuggestion(
                                "mock-class-suggestion-002",
                                LangString.cs("Development Specialization"),
                                LangString.cs("A deterministic specialized class suggestion returned in development."),
                                LangString.cs("This response is independent of the request body."),
                                TermType.CLASS,
                                List.of(new IdReference("mock-class-suggestion-001")),
                                LEGAL_ACT
                        )
                )
        ));
    }

    public JobStartResponse startPropertySuggestions() {
        return new JobStartResponse(PROPERTY_JOB_ID, JobStatus.IN_PROGRESS);
    }

    public List<PropertySuggestionsJobResponse> propertySuggestions() {
        return List.of(new PropertySuggestionsJobResponse(
                PROPERTY_JOB_ID,
                CLASS_ID,
                JobStatus.COMPLETED,
                List.of(new AttributeSuggestion(
                        "mock-attribute-suggestion-001",
                        new IdReference(CLASS_ID),
                        LangString.cs("development_attribute"),
                        LangString.cs("A deterministic attribute suggestion returned in development."),
                        LangString.cs("This response is independent of the request body."),
                        LEGAL_ACT
                ))
        ));
    }

    public JobStartResponse startRelationshipSuggestions() {
        return new JobStartResponse(RELATIONSHIP_JOB_ID, JobStatus.IN_PROGRESS);
    }

    public List<RelationshipSuggestionsJobResponse> relationshipSuggestions() {
        return List.of(new RelationshipSuggestionsJobResponse(
                RELATIONSHIP_JOB_ID,
                CLASS_ID,
                JobStatus.COMPLETED,
                List.of(new RelationshipSuggestion(
                        "mock-relationship-suggestion-001",
                        new IdReference(CLASS_ID),
                        new IdReference("mock-target-class-001"),
                        LangString.cs("development_relationship"),
                        LangString.cs("A deterministic relationship suggestion returned in development."),
                        LangString.cs("This response is independent of the request body."),
                        LEGAL_ACT
                ))
        ));
    }

    private final Map<UUID, VocabularySuggestionsJobResponse> vocabularyEdits = new ConcurrentHashMap<>();

    public JobStartResponse expandVocabulary(VocabularyExpansionJobRequest request) {
        String source = request.structuralElementIds().get(0);
        ConceptReference selected = request.selectedClassId() == null ? null : mockReference(request.selectedClassId());
        List<DraftClass> classes = List.of();
        List<DraftAttribute> attributes = List.of();
        List<DraftRelationship> relationships = List.of();
        switch (request.kind()) {
            case CLASSES -> classes = List.of(new DraftClass("mock-class-" + UUID.randomUUID(), LangString.cs("Další třída"),
                    null, null, TermType.CLASS, List.of(), source));
            case PROPERTIES -> attributes = List.of(new DraftAttribute("mock-attribute-" + UUID.randomUUID(), selected,
                    LangString.cs("Další vlastnost"), null, null, source));
            case RELATIONSHIPS -> {
                var target = request.knownConceptualModel().classes().stream()
                        .filter(c -> !c.termID().equals(request.selectedClassId())).findFirst();
                if (target.isPresent()) relationships = List.of(new DraftRelationship("mock-relationship-" + UUID.randomUUID(),
                        selected, mockReference(target.orElseThrow().termID()), LangString.cs("Souvisí s"), null, null, source));
            }
        }
        return storeVocabularyEdit(new VocabularyDraft(Phase.DONE, classes, attributes, relationships));
    }

    public JobStartResponse regenerateVocabulary(VocabularyRegenerationJobRequest request) {
        var model = request.knownConceptualModel();
        String ref = request.conceptRef();
        for (var c : model.classes() == null ? List.<cz.dia.ismd.assistant.model.suggestion.classsuggestion.KnownClassTerm>of() : model.classes()) {
            if (c.termID().equals(ref)) return storeVocabularyEdit(new VocabularyDraft(Phase.DONE,
                    List.of(new DraftClass(ref, c.name(), c.definition(), LangString.cs("Ukázka přegenerování."), c.type(),
                            c.specializes() == null ? List.of() : c.specializes().stream().map(r -> mockReference(r.id())).toList(), c.legalAct())),
                    List.of(), List.of()));
        }
        for (var a : model.attributes() == null ? List.<cz.dia.ismd.assistant.model.suggestion.attribute.KnownAttributeTerm>of() : model.attributes()) {
            if (a.termID().equals(ref)) return storeVocabularyEdit(new VocabularyDraft(Phase.DONE, List.of(),
                    List.of(new DraftAttribute(ref, mockReference(a.associatedClass().id()), a.name(), a.definition(),
                            LangString.cs("Ukázka přegenerování."), a.legalAct())), List.of()));
        }
        for (var r : model.relationships() == null ? List.<cz.dia.ismd.assistant.model.suggestion.relationship.KnownRelationshipTerm>of() : model.relationships()) {
            if (r.termID().equals(ref)) return storeVocabularyEdit(new VocabularyDraft(Phase.DONE, List.of(), List.of(),
                    List.of(new DraftRelationship(ref, mockReference(r.sourceClass().id()), mockReference(r.targetClass().id()),
                            r.name(), r.definition(), LangString.cs("Ukázka přegenerování."), r.legalAct()))));
        }
        throw new InvalidVocabularyRequestException("concept_ref must identify a supplied term");
    }

    private ConceptReference mockReference(String id) {
        return URI.create(id).isAbsolute() ? new ConceptReference(null, id) : new ConceptReference(id, null);
    }

    private JobStartResponse storeVocabularyEdit(VocabularyDraft draft) {
        UUID id = UUID.randomUUID();
        vocabularyEdits.put(id, new VocabularySuggestionsJobResponse(id, JobStatus.COMPLETED, draft));
        return new JobStartResponse(id, JobStatus.IN_PROGRESS);
    }

    public List<VocabularySuggestionsJobResponse> vocabularySuggestions(List<UUID> ids) {
        if (ids.stream().anyMatch(vocabularyEdits::containsKey)) {
            return ids.stream().map(id -> vocabularyEdits.getOrDefault(id, vocabularySuggestions().get(0))).toList();
        }
        return vocabularySuggestions();
    }

    public JobStartResponse startVocabularySuggestions() {
        return new JobStartResponse(UUID.fromString("00000000-0000-0000-0000-000000000401"), JobStatus.IN_PROGRESS);
    }

    public List<VocabularySuggestionsJobResponse> vocabularySuggestions() {
        ConceptReference vehicle = new ConceptReference("mock-class-vehicle", null);
        ConceptReference person = new ConceptReference("mock-class-person", null);
        String legalAct = "https://e-sbirka.gov.cz/eli/cz/sb/2000/361/2024-01-01";
        VocabularyDraft draft = new VocabularyDraft(Phase.DONE,
                List.of(new DraftClass(vehicle.ref(), LangString.cs("Vozidlo"), LangString.cs("Dopravní prostředek."),
                                null, TermType.CLASS, List.of(), legalAct),
                        new DraftClass(person.ref(), LangString.cs("Osoba"), LangString.cs("Účastník provozu."),
                                null, TermType.CLASS, List.of(), legalAct)),
                List.of(new DraftAttribute("mock-attribute-registration", vehicle, LangString.cs("Registrační značka"),
                        null, null, legalAct)),
                List.of(new DraftRelationship("mock-relationship-owner", vehicle, person, LangString.cs("Má vlastníka"),
                        null, null, legalAct)));
        return List.of(new VocabularySuggestionsJobResponse(startVocabularySuggestions().jobId(), JobStatus.COMPLETED, draft));
    }

    public FeedbackResponse acceptFeedback() {
        return new FeedbackResponse("accepted", "accepted");
    }

    public FeedbackResponse likeFeedback() {
        return new FeedbackResponse("accepted", "liked");
    }

    public FeedbackResponse dislikeFeedback() {
        return new FeedbackResponse("accepted", "disliked");
    }
}
