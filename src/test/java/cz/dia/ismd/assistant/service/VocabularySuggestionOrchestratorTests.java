package cz.dia.ismd.assistant.service;

import cz.dia.ismd.assistant.api.suggestion.attribute.PropertySuggestionJobRequest;
import cz.dia.ismd.assistant.api.suggestion.relationship.RelationshipSuggestionJobRequest;
import cz.dia.ismd.assistant.api.suggestion.vocabulary.VocabularyExpansionJobRequest;
import cz.dia.ismd.assistant.api.suggestion.vocabulary.VocabularySuggestionJobRequest;
import cz.dia.ismd.assistant.exception.InvalidVocabularyRequestException;
import cz.dia.ismd.assistant.exception.LlmException;
import cz.dia.ismd.assistant.model.legal.LegalActText;
import cz.dia.ismd.assistant.model.suggestion.*;
import cz.dia.ismd.assistant.model.suggestion.attribute.AttributeSuggestion;
import cz.dia.ismd.assistant.model.suggestion.classsuggestion.ClassSuggestion;
import cz.dia.ismd.assistant.model.suggestion.classsuggestion.KnownClassTerm;
import cz.dia.ismd.assistant.model.suggestion.relationship.RelationshipSuggestion;
import cz.dia.ismd.assistant.model.suggestion.vocabulary.ConceptReference;
import cz.dia.ismd.assistant.model.suggestion.vocabulary.VocabularyDraft;
import cz.dia.ismd.assistant.model.suggestion.vocabulary.VocabularyDraft.Phase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class VocabularySuggestionOrchestratorTests {
    private static final String SOURCE = "2024/1/2024-01-01/par_1/odst_5:2";
    private static final String EXISTING = "https://example.test/pojem/osoba";
    private final ClassSuggestionLlmService classes = mock(ClassSuggestionLlmService.class);
    private final PropertySuggestionLlmService properties = mock(PropertySuggestionLlmService.class);
    private final RelationshipSuggestionLlmService relationships = mock(RelationshipSuggestionLlmService.class);
    private final VocabularySuggestionOrchestrator orchestrator =
            new VocabularySuggestionOrchestrator(classes, properties, relationships);
    private final List<VocabularyDraft> snapshots = new ArrayList<>();
    private final List<LegalActText> texts = List.of(new LegalActText(1L, 1L, SOURCE, "Text zákona", "paragraph", "1"));

    @ParameterizedTest
    @CsvSource({"false, false", "false, true", "true, false", "true, true"})
    void sameNamedKnownClassesNeverRedirectGeneratedSpecialization(boolean expand, boolean reverseKnownOrder) {
        var subject = new KnownClassTerm("osoba-subjekt", LangString.cs("Osoba"), null, null,
                TermType.SUBJECT, List.of(), SOURCE);
        var object = new KnownClassTerm("osoba-objekt", LangString.cs("Osoba"), null, null,
                TermType.OBJECT, List.of(), SOURCE);
        var known = new KnownConceptualModel(reverseKnownOrder ? List.of(object, subject) : List.of(subject, object),
                List.of(), List.of());
        when(classes.suggestClasses(anyString(), any(), any())).thenReturn(List.of(
                new ClassSuggestion("navrh-1", LangString.cs("Osoba"), null, null, TermType.SUBJECT, List.of(), SOURCE),
                new ClassSuggestion("navrh-2", LangString.cs("Řidič"), null, null, TermType.SUBJECT,
                        List.of(new IdReference("navrh-1"), new IdReference("osoba-subjekt")), SOURCE)));

        var result = generateOrExpand(expand, 2, known);

        assertThat(result.classes()).hasSize(2);
        var person = result.classes().get(0);
        assertThat(person.type()).isEqualTo(TermType.SUBJECT);
        assertThat(person.ref()).isNotIn("osoba-subjekt", "osoba-objekt");
        assertThat(result.classes().get(1).specializes()).containsExactly(
                new ConceptReference(person.ref(), null), new ConceptReference("osoba-subjekt", null));
        verifyNoInteractions(properties, relationships);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void sameNameAndTypeDoNotProveIdentityWithKnownClass(boolean expand) {
        var known = new KnownConceptualModel(List.of(new KnownClassTerm(EXISTING, LangString.cs("Osoba"),
                LangString.cs("Osoba evidovaná v registru obyvatel."), null, TermType.SUBJECT, List.of(), SOURCE)),
                List.of(), List.of());
        var definition = LangString.cs("Osoba vystupující jako účastník řízení.");
        when(classes.suggestClasses(anyString(), any(), any())).thenReturn(List.of(
                new ClassSuggestion("person", LangString.cs("Osoba"), definition, null, TermType.SUBJECT, List.of(), SOURCE),
                cls("child", List.of(new IdReference("person"), new IdReference(EXISTING)))));

        var result = generateOrExpand(expand, 2, known);

        assertThat(result.classes()).hasSize(2);
        assertThat(result.classes().get(0).definition()).isEqualTo(definition);
        assertThat(result.classes().get(1).specializes()).containsExactly(
                new ConceptReference(result.classes().get(0).ref(), null), new ConceptReference(null, EXISTING));
    }

    @ParameterizedTest
    @CsvSource({"false, Řidič vozidla", "true, Řidič vozidla",
            "false, ' R\u030cIDIC\u030c   VOZIDLA '", "true, ' R\u030cIDIC\u030c   VOZIDLA '"})
    void sameNamedGeneratedClassesKeepTheirOwnIdsIncludingForwardReferences(boolean expand, String secondName) {
        when(classes.suggestClasses(anyString(), any(), any())).thenReturn(List.of(
                new ClassSuggestion("first", LangString.cs("Řidič vozidla"), LangString.cs("Řidič silničního vozidla."),
                        null, TermType.SUBJECT, List.of(), SOURCE),
                cls("child", List.of(new IdReference("second"), new IdReference("first"))),
                new ClassSuggestion("second", LangString.cs(secondName), LangString.cs("Řidič drážního vozidla."),
                        null, TermType.SUBJECT, List.of(), SOURCE)));

        var result = generateOrExpand(expand, 3, new KnownConceptualModel(List.of(), List.of(), List.of()));

        assertThat(result.classes()).hasSize(3);
        assertThat(result.classes()).extracting(VocabularyDraft.DraftClass::ref).doesNotHaveDuplicates();
        assertThat(result.classes().get(1).specializes()).containsExactly(
                new ConceptReference(result.classes().get(2).ref(), null),
                new ConceptReference(result.classes().get(0).ref(), null));
        assertThat(result.classes().get(0).definition()).isEqualTo(LangString.cs("Řidič silničního vozidla."));
        assertThat(result.classes().get(2).definition()).isEqualTo(LangString.cs("Řidič drážního vozidla."));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void sameNamedClassStillRejectsSelfSpecialization(boolean expand) {
        var known = new KnownConceptualModel(List.of(known(EXISTING)), List.of(), List.of());
        when(classes.suggestClasses(anyString(), any(), any())).thenReturn(List.of(
                new ClassSuggestion("self", known(EXISTING).name(), null, null, TermType.CLASS,
                        List.of(new IdReference("self")), SOURCE)));

        assertThatThrownBy(() -> generateOrExpand(expand, 1, known)).isInstanceOf(LlmException.class)
                .hasMessageContaining("cannot specialize itself");
        assertThat(snapshots).isEmpty();
        verifyNoInteractions(properties, relationships);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", EXISTING})
    void rejectsInvalidClassIdsEvenWhenNameMatchesKnownClass(String invalidId) {
        var known = new KnownConceptualModel(List.of(known(EXISTING)), List.of(), List.of());
        when(classes.suggestClasses(anyString(), any(), any())).thenReturn(List.of(
                new ClassSuggestion(invalidId, known(EXISTING).name(), null, null, TermType.CLASS, List.of(), SOURCE)));

        assertThatThrownBy(() -> generateOrExpand(false, 1, known)).isInstanceOf(LlmException.class)
                .hasMessageContaining("identifiers");
        assertThatThrownBy(() -> generateOrExpand(true, 1, known)).isInstanceOf(LlmException.class)
                .hasMessageContaining("identifiers");
        assertThat(snapshots).isEmpty();
        verifyNoInteractions(properties, relationships);
    }

    @Test
    void linksForwardSpecializationAndPassesAllClassesAndGeneratedTermsToSubsequentCalls() {
        var known = new KnownConceptualModel(List.of(known(EXISTING), known("previous-draft-class")), List.of(), List.of());
        when(classes.suggestClasses(anyString(), any(), any())).thenReturn(List.of(
                cls("child", List.of(new IdReference("parent"), new IdReference(EXISTING))),
                cls("parent", List.of(new IdReference("previous-draft-class")))));
        var propertyCalls = new java.util.concurrent.atomic.AtomicInteger();
        var relationshipCalls = new java.util.concurrent.atomic.AtomicInteger();
        when(properties.suggestProperties(anyString(), any(), any())).thenAnswer(invocation -> {
            PropertySuggestionJobRequest request = invocation.getArgument(1);
            assertThat(request.knownConceptualModel().attributes()).hasSize(propertyCalls.getAndIncrement());
            assertThat(request.knownConceptualModel().classes()).hasSize(4);
            assertThat(request.isSelectedClassPresentInKnownConceptualModel()).isTrue();
            assertThat(request.contextText()).isEqualTo("Vozidla");
            return List.of(new AttributeSuggestion("provider-id-reused", new IdReference(request.selectedClassId()),
                    LangString.cs("Značka"), null, null, SOURCE));
        });
        when(relationships.suggestRelationships(anyString(), any(), any())).thenAnswer(invocation -> {
            RelationshipSuggestionJobRequest request = invocation.getArgument(1);
            assertThat(request.knownConceptualModel().relationships()).hasSize(relationshipCalls.getAndIncrement());
            assertThat(request.knownConceptualModel().attributes()).hasSize(2);
            String target = request.knownConceptualModel().classes().get(3).termID();
            return List.of(new RelationshipSuggestion("provider-id-reused", new IdReference(request.selectedClassId()),
                    new IdReference(target), LangString.cs("Patří"), null, null, SOURCE));
        });

        generate(request(2, 1, 1, known));
        VocabularyDraft result = snapshots.get(snapshots.size() - 1);
        assertThat(result.phase()).isEqualTo(Phase.DONE);
        assertThat(result.classes()).hasSize(2);
        assertThat(result.attributes()).hasSize(2);
        assertThat(result.relationships()).hasSize(2);
        String child = result.classes().get(0).ref();
        String parent = result.classes().get(1).ref();
        assertThat(child).startsWith("class-").doesNotContain(":");
        assertThat(result.classes().get(0).specializes().get(0).ref()).isEqualTo(parent);
        assertThat(result.classes().get(0).specializes().get(1).iri()).isEqualTo(EXISTING);
        assertThat(result.classes().get(1).specializes().get(0).ref()).isEqualTo("previous-draft-class");
        assertThat(result.attributes().get(0).associatedClass().ref()).isEqualTo(child);
        assertThat(result.relationships().get(0).sourceClass().ref()).isEqualTo(child);
        assertThat(result.relationships().get(0).targetClass().ref()).isEqualTo(parent);
        assertThat(result.classes().get(0).legalAct()).isEqualTo("https://e-sbirka.gov.cz/eli/cz/sb/" + SOURCE);
        assertThat(Stream.of(result.classes().stream().map(VocabularyDraft.DraftClass::ref),
                        result.attributes().stream().map(VocabularyDraft.DraftAttribute::ref),
                        result.relationships().stream().map(VocabularyDraft.DraftRelationship::ref))
                .flatMap(s -> s).toList()).doesNotHaveDuplicates();
        assertThat(snapshots.get(0).attributes()).isEmpty(); // Earlier polling snapshots stay immutable.
        verify(classes, times(1)).suggestClasses(eq("user"), any(), same(texts));
        verify(properties, times(2)).suggestProperties(eq("user"), any(), same(texts));
        verify(relationships, times(2)).suggestRelationships(eq("user"), any(), same(texts));
        assertThat(known.classes()).hasSize(2); // Input model is not mutated.
        assertThat(known.attributes()).isEmpty();
        assertThat(known.relationships()).isEmpty();
    }

    @Test
    void rejectsDanglingRelationshipAndRetainsOnlyValidatedBatches() {
        when(classes.suggestClasses(anyString(), any(), any())).thenReturn(List.of(cls("a", List.of())));
        when(relationships.suggestRelationships(anyString(), any(), any())).thenAnswer(invocation -> {
            RelationshipSuggestionJobRequest request = invocation.getArgument(1);
            return List.of(new RelationshipSuggestion("r", new IdReference(request.selectedClassId()),
                    new IdReference("invented-class"), LangString.cs("Vztah"), null, null, SOURCE));
        });
        assertThatThrownBy(() -> generate(request(1, 0, 1, null))).isInstanceOf(LlmException.class);
        VocabularyDraft last = snapshots.get(snapshots.size() - 1);
        assertThat(last.phase()).isEqualTo(Phase.RELATIONSHIPS);
        assertThat(last.classes()).hasSize(1);
        assertThat(last.relationships()).isEmpty();
        verifyNoInteractions(properties);
    }

    @Test
    void rejectsWrongPropertyDomain() {
        when(classes.suggestClasses(anyString(), any(), any())).thenReturn(List.of(cls("a", List.of())));
        when(properties.suggestProperties(anyString(), any(), any())).thenReturn(List.of(
                new AttributeSuggestion("p", new IdReference("a"), LangString.cs("Název"), null, null, SOURCE)));
        assertThatThrownBy(() -> generate(request(1, 1, 1, null))).isInstanceOf(LlmException.class)
                .hasMessageContaining("selected class");
        assertThat(snapshots.get(snapshots.size() - 1).attributes()).isEmpty();
        verifyNoInteractions(relationships);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void rejectsAmbiguousClassIdsBeforePublishingAnything(boolean expand) {
        when(classes.suggestClasses(anyString(), any(), any())).thenReturn(List.of(cls("a", List.of()), cls("a", List.of())));
        assertThatThrownBy(() -> generateOrExpand(expand, 2, new KnownConceptualModel(List.of(), List.of(), List.of())))
                .isInstanceOf(LlmException.class).hasMessageContaining("identifiers");
        assertThat(snapshots).isEmpty();
        verifyNoInteractions(properties, relationships);
    }

    @Test
    void rejectsClassIdsCollidingWithKnownTermsAndUnknownSpecialization() {
        when(classes.suggestClasses(anyString(), any(), any())).thenReturn(List.of(cls(EXISTING, List.of())));
        assertThatThrownBy(() -> generate(request(1, 0, 0,
                new KnownConceptualModel(List.of(known(EXISTING)), null, null)))).isInstanceOf(LlmException.class);
        when(classes.suggestClasses(anyString(), any(), any())).thenReturn(List.of(cls("a", List.of(new IdReference("missing")))));
        assertThatThrownBy(() -> generate(request(1, 0, 0, null))).isInstanceOf(LlmException.class);
        assertThat(snapshots).isEmpty();
    }

    @Test
    void enforcesCountsEvenIfProviderIgnoresPrompt() {
        when(classes.suggestClasses(anyString(), any(), any())).thenReturn(List.of(cls("a", List.of()), cls("b", List.of())));
        assertThatThrownBy(() -> generate(request(1, 0, 0, null))).isInstanceOf(LlmException.class)
                .hasMessageContaining("count");
    }

    @Test
    void emptyClassResultCompletesWithoutChildCalls() {
        when(classes.suggestClasses(anyString(), any(), any())).thenReturn(List.of());
        generate(request(1, 1, 1, null));
        assertThat(snapshots.get(snapshots.size() - 1).phase()).isEqualTo(Phase.DONE);
        verifyNoInteractions(properties, relationships);
    }

    @Test
    void allocatesDifferentRefsAcrossGenerations() {
        when(classes.suggestClasses(anyString(), any(), any())).thenReturn(List.of(cls("a", List.of())));
        generate(request(1, 0, 0, null));
        String first = snapshots.get(snapshots.size() - 1).classes().get(0).ref();
        generate(request(1, 0, 0, null));
        assertThat(snapshots.get(snapshots.size() - 1).classes().get(0).ref()).isNotEqualTo(first);
    }

    @Test
    void invalidKnownModelFailsValidation() {
        assertThatThrownBy(() -> VocabularyRequestValidator.validateKnownModel(
                new KnownConceptualModel(List.of(known("a"), known("a")), null, null)))
                .isInstanceOf(InvalidVocabularyRequestException.class);
    }

    @Test
    void rejectsIncompleteLegalActSource() {
        when(classes.suggestClasses(anyString(), any(), any())).thenReturn(List.of(new ClassSuggestion(
                "a", LangString.cs("Třída"), null, null, TermType.CLASS, List.of(), "2024")));
        assertThatThrownBy(() -> generate(request(1, 0, 0, null))).isInstanceOf(LlmException.class);
        assertThat(snapshots).isEmpty();
    }

    @Test
    void rejectsLegalSourcesOutsideRetrievedTexts() {
        ClassSuggestion invalid = new ClassSuggestion("a", LangString.cs("Třída"), null, null,
                TermType.CLASS, List.of(), "https://e-sbirka.gov.cz/eli/cz/sb/1999/99");
        when(classes.suggestClasses(anyString(), any(), any())).thenReturn(List.of(invalid));
        assertThatThrownBy(() -> generate(request(1, 0, 0, null))).isInstanceOf(LlmException.class)
                .hasMessageContaining("legal source");
        assertThat(snapshots).isEmpty();
    }

    private void generate(VocabularySuggestionJobRequest request) {
        orchestrator.generate("user", request, texts, snapshots::add);
    }

    private VocabularyDraft generateOrExpand(boolean expand, int count, KnownConceptualModel known) {
        if (expand) {
            var request = new VocabularyExpansionJobRequest(VocabularyExpansionJobRequest.Kind.CLASSES,
                    count, null, null, "Vozidla", known).forLegalAct(2024, 1, LocalDate.of(2024, 1, 1));
            orchestrator.expand("user", request, texts, snapshots::add);
        } else {
            generate(request(count, 0, 0, known));
        }
        return snapshots.get(snapshots.size() - 1);
    }

    private VocabularySuggestionJobRequest request(int count, int props, int rels, KnownConceptualModel known) {
        return new VocabularySuggestionJobRequest(count, props, rels, null, "Vozidla", known)
                .forLegalAct(2024, 1, LocalDate.of(2024, 1, 1));
    }

    private ClassSuggestion cls(String id, List<IdReference> specializes) {
        return new ClassSuggestion(id, LangString.cs(id), null, null, TermType.CLASS, specializes, SOURCE);
    }

    private KnownClassTerm known(String id) {
        return new KnownClassTerm(id, LangString.cs("Známá třída"), null, null, TermType.CLASS, List.of(), null);
    }
}
