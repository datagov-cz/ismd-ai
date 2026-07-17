package cz.dia.ismd.assistant.service;

import cz.dia.ismd.assistant.api.suggestion.classsuggestion.ClassSuggestionJobRequest;
import cz.dia.ismd.assistant.api.suggestion.attribute.PropertySuggestionJobRequest;
import cz.dia.ismd.assistant.api.suggestion.relationship.RelationshipSuggestionJobRequest;
import cz.dia.ismd.assistant.model.job.JobStatus;
import cz.dia.ismd.assistant.model.job.SuggestionJob;
import cz.dia.ismd.assistant.model.legal.LegalActText;
import cz.dia.ismd.assistant.model.suggestion.LangString;
import cz.dia.ismd.assistant.model.suggestion.TermType;
import cz.dia.ismd.assistant.model.suggestion.IdReference;
import cz.dia.ismd.assistant.model.suggestion.KnownConceptualModel;
import cz.dia.ismd.assistant.model.suggestion.attribute.AttributeSuggestion;
import cz.dia.ismd.assistant.model.suggestion.classsuggestion.ClassSuggestion;
import cz.dia.ismd.assistant.model.suggestion.classsuggestion.KnownClassTerm;
import cz.dia.ismd.assistant.model.suggestion.relationship.RelationshipSuggestion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(OutputCaptureExtension.class)
class SuggestionJobServiceTests {

    private final ClassSuggestionLlmService classSuggestionLlmService = mock(ClassSuggestionLlmService.class);
    private final PropertySuggestionLlmService propertySuggestionLlmService = mock(PropertySuggestionLlmService.class);
    private final RelationshipSuggestionLlmService relationshipSuggestionLlmService =
            mock(RelationshipSuggestionLlmService.class);
    private final LegalActSPARQLService legalActSPARQLService = mock(LegalActSPARQLService.class);
    private final TokenUsageService tokenUsageService = mock(TokenUsageService.class);
    private final SuggestionJobService service = new SuggestionJobService(
            classSuggestionLlmService,
            propertySuggestionLlmService,
            relationshipSuggestionLlmService,
            legalActSPARQLService,
            tokenUsageService
    );

    @Test
    void completesClassJobWithLlmSuggestions() throws Exception {
        String structuralElementId = "/eli/cz/sb/2024/1/2024-01-01/par_1";
        ClassSuggestionJobRequest request = new ClassSuggestionJobRequest(
                1, List.of(structuralElementId), "Osoba", null);
        LegalActText legalActText = legalActText("2024/1/2024-01-01/par_1", "Osoba je fyzická osoba.");
        ClassSuggestion suggestion = new ClassSuggestion(
                "class_001",
                LangString.cs("Osoba"),
                LangString.cs("Fyzická osoba."),
                LangString.cs("Pojem nalezený v právním textu."),
                TermType.CLASS,
                List.of(),
                "/eli/cz/sb/2024/1"
        );
        when(legalActSPARQLService.retrieveLegalActTexts(List.of(structuralElementId)))
                .thenReturn(List.of(legalActText));
        when(classSuggestionLlmService.suggestClasses("test-user", request, List.of(legalActText)))
                .thenReturn(List.of(suggestion));

        SuggestionJob job = service.startClassJob("test-user", request);

        awaitFinished(job);
        assertThat(job.status()).isEqualTo(JobStatus.COMPLETED);
        assertThat(job.classSuggestions()).containsExactly(suggestion);
        verify(tokenUsageService).ensureRequestAllowed("test-user");
        verify(classSuggestionLlmService).suggestClasses("test-user", request, List.of(legalActText));
    }

    @Test
    void marksClassJobAsFailedAndLogsFailureDetails(CapturedOutput output) throws Exception {
        String structuralElementId = "/eli/cz/sb/2024/1/2024-01-01/par_1";
        ClassSuggestionJobRequest request = new ClassSuggestionJobRequest(
                1, List.of(structuralElementId), null, null);
        LegalActText legalActText = legalActText("2024/1/2024-01-01/par_1", "Legal text");
        when(legalActSPARQLService.retrieveLegalActTexts(List.of(structuralElementId)))
                .thenReturn(List.of(legalActText));
        when(classSuggestionLlmService.suggestClasses("test-user", request, List.of(legalActText)))
                .thenThrow(new IllegalStateException("LLM unavailable"));

        SuggestionJob job = service.startClassJob("test-user", request);

        awaitFinished(job);
        assertThat(job.status()).isEqualTo(JobStatus.FAILED);
        assertThat(job.classSuggestions()).isEmpty();
        assertThat(output.getOut())
                .contains("Suggestion job failed")
                .contains("jobId=" + job.jobId())
                .contains("kind=CLASS")
                .contains("selectedClassId=null")
                .contains("LLM unavailable")
                .contains("java.lang.IllegalStateException");
    }

    @Test
    void continuesWithFoundTextsAndWarnsForMissingStructuralElements(CapturedOutput output) throws Exception {
        String found = "opaque prefix /eli/cz/sb/2024/1/2024-01-01/par_1";
        String missing = "/eli/cz/sb/2024/1/2024-01-01/par_2";
        ClassSuggestionJobRequest request = new ClassSuggestionJobRequest(1, List.of(found, missing), null, null);
        LegalActText legalActText = legalActText("2024/1/2024-01-01/par_1/odst_1", "Found text");
        when(legalActSPARQLService.retrieveLegalActTexts(List.of(found, missing)))
                .thenReturn(List.of(legalActText));
        when(classSuggestionLlmService.suggestClasses("test-user", request, List.of(legalActText)))
                .thenReturn(List.of());

        SuggestionJob job = service.startClassJob("test-user", request);

        awaitFinished(job);
        assertThat(job.status()).isEqualTo(JobStatus.COMPLETED);
        assertThat(output.getOut())
                .contains("Legal text was not found for structural element")
                .contains("structuralElementId=" + missing);
        verify(classSuggestionLlmService).suggestClasses("test-user", request, List.of(legalActText));
    }

    @Test
    void failsJobAndDoesNotCallLlmWhenAllStructuralElementsAreMissing(CapturedOutput output) throws Exception {
        String first = "/eli/cz/sb/2024/1/2024-01-01/par_1";
        String second = "/eli/cz/sb/2024/1/2024-01-01/par_2";
        ClassSuggestionJobRequest request = new ClassSuggestionJobRequest(1, List.of(first, second), null, null);
        when(legalActSPARQLService.retrieveLegalActTexts(List.of(first, second))).thenReturn(List.of());

        SuggestionJob job = service.startClassJob("test-user", request);

        awaitFinished(job);
        assertThat(job.status()).isEqualTo(JobStatus.FAILED);
        assertThat(output.getOut())
                .contains("Suggestion job failed")
                .contains("No legal texts were found for the requested structural elements");
        verifyNoInteractions(classSuggestionLlmService);
    }

    @Test
    void completesPropertyJobWithRetrievedLegalTextAndLlmSuggestions() throws Exception {
        String structuralElementId = "/eli/cz/sb/2024/1/2024-01-01/par_1";
        PropertySuggestionJobRequest request = new PropertySuggestionJobRequest(
                1, "class_001", List.of(structuralElementId), null, knownConceptualModel());
        LegalActText legalActText = legalActText("2024/1/2024-01-01/par_1", "Osoba má jméno.");
        AttributeSuggestion suggestion = new AttributeSuggestion(
                "property_001", new IdReference("class_001"), LangString.cs("jméno"),
                LangString.cs("Jméno osoby."), LangString.cs("Nalezeno v textu."), "/eli/cz/sb/2024/1");
        when(legalActSPARQLService.retrieveLegalActTexts(List.of(structuralElementId)))
                .thenReturn(List.of(legalActText));
        when(propertySuggestionLlmService.suggestProperties("test-user", request, List.of(legalActText)))
                .thenReturn(List.of(suggestion));

        SuggestionJob job = service.startPropertyJob("test-user", request);

        awaitFinished(job);
        assertThat(job.status()).isEqualTo(JobStatus.COMPLETED);
        assertThat(job.attributeSuggestions()).containsExactly(suggestion);
        verify(tokenUsageService).ensureRequestAllowed("test-user");
        verify(propertySuggestionLlmService).suggestProperties("test-user", request, List.of(legalActText));
    }

    @Test
    void completesRelationshipJobWithRetrievedLegalTextAndLlmSuggestions() throws Exception {
        String structuralElementId = "/eli/cz/sb/2024/1/2024-01-01/par_1";
        RelationshipSuggestionJobRequest request = new RelationshipSuggestionJobRequest(
                1, "class_001", List.of(structuralElementId), null, knownConceptualModel());
        LegalActText legalActText = legalActText("2024/1/2024-01-01/par_1", "Osoba vlastní věc.");
        RelationshipSuggestion suggestion = new RelationshipSuggestion(
                "relationship_001", new IdReference("class_001"), new IdReference("class_002"),
                LangString.cs("vlastní"), LangString.cs("Vztah vlastnictví."),
                LangString.cs("Nalezeno v textu."), "/eli/cz/sb/2024/1");
        when(legalActSPARQLService.retrieveLegalActTexts(List.of(structuralElementId)))
                .thenReturn(List.of(legalActText));
        when(relationshipSuggestionLlmService.suggestRelationships("test-user", request, List.of(legalActText)))
                .thenReturn(List.of(suggestion));

        SuggestionJob job = service.startRelationshipJob("test-user", request);

        awaitFinished(job);
        assertThat(job.status()).isEqualTo(JobStatus.COMPLETED);
        assertThat(job.relationshipSuggestions()).containsExactly(suggestion);
        verify(tokenUsageService).ensureRequestAllowed("test-user");
        verify(relationshipSuggestionLlmService).suggestRelationships(
                "test-user", request, List.of(legalActText));
    }

    private KnownConceptualModel knownConceptualModel() {
        return new KnownConceptualModel(
                List.of(
                        new KnownClassTerm("class_001", LangString.cs("Osoba"), null, null,
                                TermType.CLASS, List.of(), "/eli/cz/sb/2024/1"),
                        new KnownClassTerm("class_002", LangString.cs("Věc"), null, null,
                                TermType.CLASS, List.of(), "/eli/cz/sb/2024/1")
                ),
                List.of(),
                List.of()
        );
    }

    private LegalActText legalActText(String path, String text) {
        return new LegalActText(1L, 2L, path, text, "paragraph", "1");
    }

    private void awaitFinished(SuggestionJob job) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (job.status() == JobStatus.IN_PROGRESS && System.nanoTime() < deadline) {
            Thread.sleep(5);
        }
    }
}
