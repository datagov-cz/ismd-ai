package cz.dia.ismd.assistant.service;

import cz.dia.ismd.assistant.api.suggestion.vocabulary.*;
import cz.dia.ismd.assistant.exception.InvalidVocabularyRequestException;
import cz.dia.ismd.assistant.model.legal.LegalActText;
import cz.dia.ismd.assistant.model.suggestion.*;
import cz.dia.ismd.assistant.model.suggestion.attribute.*;
import cz.dia.ismd.assistant.model.suggestion.classsuggestion.*;
import cz.dia.ismd.assistant.model.suggestion.relationship.*;
import cz.dia.ismd.assistant.model.suggestion.vocabulary.VocabularyDraft;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class VocabularyEditingTests {
    private static final String SOURCE = "2024/1/2024-01-01/par_1";
    private final ClassSuggestionLlmService classes = mock(ClassSuggestionLlmService.class);
    private final PropertySuggestionLlmService properties = mock(PropertySuggestionLlmService.class);
    private final RelationshipSuggestionLlmService relationships = mock(RelationshipSuggestionLlmService.class);
    private final VocabularySuggestionOrchestrator orchestrator = new VocabularySuggestionOrchestrator(classes, properties, relationships);
    private final List<LegalActText> texts = List.of(new LegalActText(1L, 1, SOURCE, "Source", "par", "1"));
    private final List<VocabularyDraft> results = new ArrayList<>();
    private final KnownConceptualModel known = new KnownConceptualModel(
            List.of(cls("vehicle", "Motocykl upravený uživatelem"), cls("driver", "Řidič")),
            List.of(new KnownAttributeTerm("volume", id("vehicle"), LangString.cs("Objem válců"), null, null, SOURCE)),
            List.of(new KnownRelationshipTerm("drives", id("driver"), id("vehicle"), LangString.cs("řídí"), null, null, SOURCE)));

    @Test
    void expandsOnlyPropertiesUsingEntireCurrentModelAndSuppressesRepeatedNames() {
        when(properties.suggestProperties(anyString(), any(), any())).thenAnswer(inv -> {
            var r = inv.getArgument(1, cz.dia.ismd.assistant.api.suggestion.attribute.PropertySuggestionJobRequest.class);
            assertThat(r.knownConceptualModel()).isEqualTo(known);
            assertThat(r.selectedClassId()).isEqualTo("vehicle");
            return List.of(attr(" OBJEM   VÁLCŮ "), attr("Výkon"), attr("výkon"));
        });
        orchestrator.expand("user", expansion(VocabularyExpansionJobRequest.Kind.PROPERTIES, "vehicle", known), texts, results::add);
        var delta = last();
        assertThat(delta.attributes()).hasSize(1);
        assertThat(delta.attributes().get(0).name()).isEqualTo(LangString.cs("Výkon"));
        assertThat(delta.attributes().get(0).ref()).startsWith("attribute-");
        assertThat(delta.attributes().get(0).associatedClass().ref()).isEqualTo("vehicle");
        assertThat(delta.classes()).isEmpty(); assertThat(delta.relationships()).isEmpty();
        assertThat(known.attributes()).hasSize(1);
        verifyNoInteractions(classes, relationships);
    }

    @Test
    void allowsReverseDirectionAndDifferentPredicatesButNotSameDirectedDuplicate() {
        when(relationships.suggestRelationships(anyString(), any(), any())).thenReturn(List.of(
                relation("vehicle", "driver", "řídí"), relation("vehicle", "driver", "je řízeno")));
        orchestrator.expand("user", expansion(VocabularyExpansionJobRequest.Kind.RELATIONSHIPS, "vehicle", known), texts, results::add);
        assertThat(last().relationships()).hasSize(2); // reverse endpoints are not a duplicate, even with same name
        when(relationships.suggestRelationships(anyString(), any(), any())).thenReturn(List.of(
                relation("driver", "vehicle", " ŘÍDÍ "), relation("driver", "vehicle", "vlastní")));
        orchestrator.expand("user", expansion(VocabularyExpansionJobRequest.Kind.RELATIONSHIPS, "driver", known), texts, results::add);
        assertThat(last().relationships()).extracting(r -> r.name().values().get("cs")).containsExactly("vlastní");
        verifyNoInteractions(classes, properties);
    }

    @Test
    void expandsClassesWithoutChildrenAndKeepsSameNamedClassDistinctFromKnownRef() {
        when(classes.suggestClasses(anyString(), any(), any())).thenReturn(List.of(
                new ClassSuggestion("duplicate", LangString.cs("Řidič"), null, null, TermType.CLASS, List.of(), SOURCE),
                new ClassSuggestion("new", LangString.cs("Profesionální řidič"), null, null, TermType.CLASS, List.of(id("duplicate")), SOURCE)));
        orchestrator.expand("user", expansion(VocabularyExpansionJobRequest.Kind.CLASSES, null, known), texts, results::add);
        assertThat(last().classes()).hasSize(2);
        String generatedDriver = last().classes().get(0).ref();
        assertThat(generatedDriver).isNotEqualTo("driver");
        assertThat(last().classes().get(1).specializes().get(0).ref()).isEqualTo(generatedDriver);
        verifyNoInteractions(properties, relationships);
    }

    @Test
    void validatesReferencesAndSelectionBeforeAnyWorkerIsScheduled() {
        var legalActs = mock(LegalActSPARQLService.class);
        var tokens = mock(TokenUsageService.class);
        var executor = mock(java.util.concurrent.Executor.class);
        var jobs = new SuggestionJobService(classes, properties, relationships, legalActs, tokens, null, executor);
        var bad = new VocabularyExpansionJobRequest(VocabularyExpansionJobRequest.Kind.PROPERTIES, 1, "absent", List.of(SOURCE), null, known);
        assertThatThrownBy(() -> jobs.expandVocabulary("user", bad)).isInstanceOf(InvalidVocabularyRequestException.class);
        var dangling = new KnownConceptualModel(known.classes(), List.of(new KnownAttributeTerm("a", id("missing"), LangString.cs("A"), null, null, null)), List.of());
        assertThatThrownBy(() -> VocabularyRequestValidator.validateWorkingModel(dangling)).isInstanceOf(InvalidVocabularyRequestException.class);
        var collision = new KnownConceptualModel(List.of(cls("same", "A"), cls("same", "B")), null, null);
        assertThatThrownBy(() -> VocabularyRequestValidator.validateWorkingModel(collision)).isInstanceOf(InvalidVocabularyRequestException.class);
        assertThatThrownBy(() -> VocabularyRequestValidator.validateExpansion(
                new VocabularyExpansionJobRequest(VocabularyExpansionJobRequest.Kind.CLASSES, 1, "driver", List.of(SOURCE), null, known)))
                .isInstanceOf(InvalidVocabularyRequestException.class);
        verifyNoInteractions(executor, classes, properties, relationships, legalActs);
    }

    @Test
    void expansionWithNoNewResultsCompletesWithEmptyDelta() {
        when(properties.suggestProperties(anyString(), any(), any())).thenReturn(List.of(attr("Objem válců")));
        orchestrator.expand("user", expansion(VocabularyExpansionJobRequest.Kind.PROPERTIES, "vehicle", known), texts, results::add);
        assertThat(last().attributes()).isEmpty();
        assertThat(last().phase()).isEqualTo(VocabularyDraft.Phase.DONE);
    }

    @Test
    void regenerationKeepsRefTypeAndSpecializationAndDoesNotCallOtherGenerators() {
        var model = new KnownConceptualModel(List.of(cls("parent", "Vozidlo"),
                new KnownClassTerm("vehicle", LangString.cs("Motocykl"), null, null, TermType.CLASS, List.of(id("parent")), SOURCE)), null, null);
        when(classes.regenerate(anyString(), any(), any(), any())).thenReturn(List.of(metadata()));
        orchestrator.regenerate("user", regeneration("vehicle", model), texts, results::add);
        var replacement = last().classes().get(0);
        assertThat(replacement.ref()).isEqualTo("vehicle");
        assertThat(replacement.type()).isEqualTo(TermType.CLASS);
        assertThat(replacement.specializes().get(0).ref()).isEqualTo("parent");
        assertThat(replacement.name()).isEqualTo(LangString.cs("Nové znění"));
        assertThat(last().attributes()).isEmpty(); assertThat(last().relationships()).isEmpty();
        verifyNoInteractions(properties, relationships);
        verify(classes, never()).suggestClasses(anyString(), any(), any());
    }

    @Test
    void regenerationKeepsAttributeDomainAndRelationshipDirection() {
        when(properties.regenerate(anyString(), any(), any(), any())).thenReturn(List.of(metadata()));
        orchestrator.regenerate("user", regeneration("volume", known), texts, results::add);
        assertThat(last().attributes().get(0).ref()).isEqualTo("volume");
        assertThat(last().attributes().get(0).associatedClass().ref()).isEqualTo("vehicle");
        when(relationships.regenerate(anyString(), any(), any(), any())).thenReturn(List.of(metadata()));
        orchestrator.regenerate("user", regeneration("drives", known), texts, results::add);
        var r = last().relationships().get(0);
        assertThat(r.ref()).isEqualTo("drives"); assertThat(r.sourceClass().ref()).isEqualTo("driver");
        assertThat(r.targetClass().ref()).isEqualTo("vehicle");
        verify(relationships, never()).suggestRelationships(anyString(), any(), any());
    }

    @Test
    void noSupportedRewriteReturnsNoReplacementAndUnknownOrPersistedTargetsAreRejected() {
        when(properties.regenerate(anyString(), any(), any(), any())).thenReturn(List.of());
        orchestrator.regenerate("user", regeneration("volume", known), texts, results::add);
        assertThat(last().attributes()).isEmpty();
        assertThatThrownBy(() -> regeneration("missing", known)).isInstanceOf(InvalidVocabularyRequestException.class);
        var persisted = new KnownConceptualModel(List.of(cls("https://example.test/vehicle", "Vozidlo")), null, null);
        assertThatThrownBy(() -> regeneration("https://example.test/vehicle", persisted)).isInstanceOf(InvalidVocabularyRequestException.class);
    }

    private VocabularyExpansionJobRequest expansion(VocabularyExpansionJobRequest.Kind kind, String selected, KnownConceptualModel model) {
        var request = new VocabularyExpansionJobRequest(kind, 3, selected, null, "Kontext", model)
                .forLegalAct(2024, 1, LocalDate.of(2024, 1, 1));
        VocabularyRequestValidator.validateExpansion(request);
        return request;
    }
    private VocabularyRegenerationJobRequest regeneration(String ref, KnownConceptualModel model) {
        var request = new VocabularyRegenerationJobRequest(ref, null, "Uprav text", model)
                .forLegalAct(2024, 1, LocalDate.of(2024, 1, 1));
        VocabularyRequestValidator.validateRegeneration(request);
        return request;
    }
    private VocabularyDraft last() { return results.get(results.size() - 1); }
    private KnownClassTerm cls(String id, String name) { return new KnownClassTerm(id, LangString.cs(name), null, null, TermType.CLASS, List.of(), SOURCE); }
    private IdReference id(String value) { return new IdReference(value); }
    private AttributeSuggestion attr(String name) { return new AttributeSuggestion("provider-attr", id("vehicle"), LangString.cs(name), null, null, SOURCE); }
    private RelationshipSuggestion relation(String from, String to, String name) { return new RelationshipSuggestion("provider-rel", id(from), id(to), LangString.cs(name), null, null, SOURCE); }
    private ConceptRegenerationLlmService.Metadata metadata() { return new ConceptRegenerationLlmService.Metadata(LangString.cs("Nové znění"), null, null, SOURCE); }
}
