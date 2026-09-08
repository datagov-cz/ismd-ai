package cz.dia.ismd.assistant.service;

import cz.dia.ismd.assistant.api.suggestion.vocabulary.VocabularySuggestionJobRequest;
import cz.dia.ismd.assistant.api.suggestion.vocabulary.VocabularyRegenerationJobRequest;
import cz.dia.ismd.assistant.exception.InvalidVocabularyRequestException;
import cz.dia.ismd.assistant.exception.TokenLimitReachedException;
import cz.dia.ismd.assistant.exception.VocabularyJobCapacityException;
import cz.dia.ismd.assistant.model.job.JobKind;
import cz.dia.ismd.assistant.model.job.JobStatus;
import cz.dia.ismd.assistant.model.legal.LegalActText;
import cz.dia.ismd.assistant.model.suggestion.LangString;
import cz.dia.ismd.assistant.model.suggestion.IdReference;
import cz.dia.ismd.assistant.model.suggestion.KnownConceptualModel;
import cz.dia.ismd.assistant.model.suggestion.TermType;
import cz.dia.ismd.assistant.model.suggestion.classsuggestion.ClassSuggestion;
import cz.dia.ismd.assistant.model.suggestion.classsuggestion.KnownClassTerm;
import cz.dia.ismd.assistant.model.suggestion.relationship.KnownRelationshipTerm;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class VocabularySuggestionJobServiceTests {
    private final ClassSuggestionLlmService classes = mock(ClassSuggestionLlmService.class);
    private final PropertySuggestionLlmService properties = mock(PropertySuggestionLlmService.class);
    private final RelationshipSuggestionLlmService relationships = mock(RelationshipSuggestionLlmService.class);
    private final LegalActSPARQLService legalActs = mock(LegalActSPARQLService.class);
    private final TokenUsageService tokens = mock(TokenUsageService.class);
    private final VocabularySuggestionJobRequest request = new VocabularySuggestionJobRequest(1, 0, 0, null, null, null)
            .forLegalAct(2024, 1, LocalDate.of(2024, 1, 1));

    @Test
    void returnsBeforeWorkRunsAndUsesOneWorkerTaskAndOneLegalTextRead() {
        List<Runnable> work = new ArrayList<>();
        SuggestionJobService service = service(work::add);
        when(legalActs.retrieveLegalActTexts(any())).thenReturn(List.of(new LegalActText(
                1L, 1L, "2024/1/2024-01-01", "Text", "law", "1")));
        when(classes.suggestClasses(anyString(), any(), any())).thenReturn(List.of(new ClassSuggestion(
                "llm-id", LangString.cs("Třída"), null, null, TermType.CLASS, List.of(), "2024/1/2024-01-01")));
        var job = service.startVocabularyJob("user", request);
        assertThat(job.status()).isEqualTo(JobStatus.IN_PROGRESS);
        assertThat(job.kind()).isEqualTo(JobKind.VOCABULARY);
        assertThat(work).hasSize(1);
        verifyNoInteractions(classes, properties, relationships, legalActs);
        work.get(0).run();
        assertThat(service.get(job.jobId()).status()).isEqualTo(JobStatus.COMPLETED);
        assertThat(job.vocabularyDraft().classes()).hasSize(1);
        verify(legalActs, times(1)).retrieveLegalActTexts(any());
        verify(classes, times(1)).suggestClasses(eq("user"), any(), any());
        verifyNoInteractions(properties, relationships);
    }

    @Test
    void rejectsOverloadedQueueWithoutCallingLlm() {
        SuggestionJobService service = service(command -> { throw new RejectedExecutionException("full"); });
        assertThatThrownBy(() -> service.startVocabularyJob("user", request)).isInstanceOf(VocabularyJobCapacityException.class);
        verifyNoInteractions(classes, properties, relationships, legalActs);
    }

    @Test
    void enforcesTokenLimitBeforeScheduling() {
        Executor executor = mock(Executor.class);
        doThrow(new TokenLimitReachedException("user", 1000)).when(tokens).ensureRequestAllowed("user");
        assertThatThrownBy(() -> service(executor).startVocabularyJob("user", request))
                .isInstanceOf(TokenLimitReachedException.class);
        verifyNoInteractions(executor, classes, properties, relationships, legalActs);
    }

    @Test
    void missingSourceFailsTheJobWithoutGeneratingUnsupportedConcepts() {
        when(legalActs.retrieveLegalActTexts(any())).thenReturn(List.of());
        var job = service(Runnable::run).startVocabularyJob("user", request);
        assertThat(job.status()).isEqualTo(JobStatus.FAILED);
        assertThat(job.vocabularyDraft().classes()).isEmpty();
        verifyNoInteractions(classes, properties, relationships);
    }

    @Test
    void validatesKnownModelBeforeEnqueueing() {
        Executor executor = mock(Executor.class);
        var c = new KnownClassTerm("duplicate", LangString.cs("Třída"), null, null, TermType.CLASS, null, null);
        var invalid = new VocabularySuggestionJobRequest(1, 0, 0, request.structuralElementIds(), null,
                new KnownConceptualModel(List.of(c, c), null, null));
        assertThatThrownBy(() -> service(executor).startVocabularyJob("user", invalid))
                .isInstanceOf(InvalidVocabularyRequestException.class);
        verifyNoInteractions(executor, classes, properties, relationships, legalActs, tokens);
    }

    @Test
    void regeneratesClassAndRelationshipWithOmittedOptionalCollections() {
        String source = "2024/1/2024-01-01";
        var driver = new KnownClassTerm("driver", LangString.cs("Řidič"), null, null, TermType.CLASS, null, null);
        var vehicle = new KnownClassTerm("vehicle", LangString.cs("Vozidlo"), null, null, TermType.CLASS, null, null);
        var relation = new KnownRelationshipTerm("drives", new IdReference("driver"), new IdReference("vehicle"),
                LangString.cs("řídí"), null, null, null);
        var known = new KnownConceptualModel(List.of(driver, vehicle), null, List.of(relation));
        when(legalActs.retrieveLegalActTexts(any())).thenReturn(List.of(new LegalActText(1L, 1L, source, "Text", "law", "1")));
        var metadata = new ConceptRegenerationLlmService.Metadata(LangString.cs("Nové znění"), null, null, source);
        when(classes.regenerate(anyString(), any(), any(), any())).thenReturn(List.of(metadata));
        when(relationships.regenerate(anyString(), any(), any(), any())).thenReturn(List.of(metadata));
        var service = service(Runnable::run);

        var classJob = service.regenerateVocabularyConcept("user",
                new VocabularyRegenerationJobRequest("driver", request.structuralElementIds(), null, known));
        var relationJob = service.regenerateVocabularyConcept("user",
                new VocabularyRegenerationJobRequest("drives", request.structuralElementIds(), null, known));

        assertThat(classJob.status()).isEqualTo(JobStatus.COMPLETED);
        assertThat(classJob.vocabularyDraft().classes().get(0).specializes()).isEmpty();
        assertThat(relationJob.status()).isEqualTo(JobStatus.COMPLETED);
        assertThat(relationJob.vocabularyDraft().relationships().get(0).sourceClass().ref()).isEqualTo("driver");
        assertThat(relationJob.vocabularyDraft().relationships().get(0).targetClass().ref()).isEqualTo("vehicle");
        assertThat(known.attributes()).isNull();
        assertThat(driver.specializes()).isNull();
    }

    private SuggestionJobService service(Executor executor) {
        return new SuggestionJobService(classes, properties, relationships, legalActs, tokens, null, executor);
    }
}
