package cz.dia.ismd.assistant;

import cz.dia.ismd.assistant.model.job.JobKind;
import cz.dia.ismd.assistant.model.job.JobStatus;
import cz.dia.ismd.assistant.model.job.SuggestionJob;
import cz.dia.ismd.assistant.model.suggestion.LangString;
import cz.dia.ismd.assistant.model.suggestion.TermType;
import cz.dia.ismd.assistant.model.suggestion.classsuggestion.ClassSuggestion;
import cz.dia.ismd.assistant.service.SuggestionJobRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class JobPersistenceIntegrationTests extends AssistantIntegrationTest {

    @Autowired
    private SuggestionJobRepository suggestionJobRepository;

    @Test
    void persistsCompletedJobAndCanRestoreItsResponseData() throws Exception {
        MvcResult start = mockMvc.perform(post(
                        "/legal-acts/2024/1/2024-01-01/class-suggestions-top-k-extraction-jobs")
                        .with(oidcAuthentication())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "k": 1,
                                  "structural_element_ids": [
                                    "https://e-sbirka.gov.cz/eli/cz/sb/2026/60/2026-05-27/dokument/norma/par_3"
                                  ]
                                }
                                """))
                .andExpect(status().isAccepted())
                .andReturn();

        UUID jobId = UUID.fromString(Json.read(start, "job_id"));
        waitForAsyncJob(jobId);

        SuggestionJob restored = suggestionJobRepository.findById(jobId).orElseThrow();
        assertThat(restored.kind()).isEqualTo(JobKind.CLASS);
        assertThat(restored.status()).isEqualTo(JobStatus.COMPLETED);
        assertThat(restored.classSuggestions()).hasSize(1);
        assertThat(restored.drainClassSuggestions()).containsExactlyElementsOf(restored.classSuggestions());
    }

    @Test
    void marksPersistedInProgressJobsAsFailed() {
        SuggestionJob interrupted = new SuggestionJob(
                UUID.randomUUID(),
                JobKind.CLASS,
                null,
                Instant.now(),
                JobStatus.IN_PROGRESS,
                List.of(new ClassSuggestion(
                        "class_interrupted", LangString.cs("Interrupted"), null, null,
                        TermType.CLASS, List.of(), "/eli/cz/sb/2024/1")),
                List.of(),
                List.of());
        suggestionJobRepository.insert(interrupted);

        suggestionJobRepository.failInterruptedJobs();

        SuggestionJob restored = suggestionJobRepository.findById(interrupted.jobId()).orElseThrow();
        assertThat(restored.status()).isEqualTo(JobStatus.FAILED);
        assertThat(restored.classSuggestions()).containsExactlyElementsOf(interrupted.classSuggestions());
    }

    @Test
    void feedbackJobIdIsProtectedByForeignKey() {
        assertThatThrownBy(() -> jdbcTemplate.update("""
                        INSERT INTO feedback_records(job_id, suggestion_id, feedback_type, created_at)
                        VALUES (?, ?, ?, ?)
                        """,
                UUID.randomUUID(),
                "missing",
                "LIKED",
                OffsetDateTime.now(ZoneOffset.UTC)))
                .hasMessageContaining("fk_feedback_job");
    }
}
