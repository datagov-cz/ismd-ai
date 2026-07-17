package cz.dia.ismd.assistant;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FeedbackIntegrationTests extends AssistantIntegrationTest {

    @Test
    void recordsFeedbackForExistingJobs() throws Exception {
        MvcResult firstStart = mockMvc.perform(post("/legal-acts/2024/1/2024-01-01/class-suggestions-top-k-extraction-jobs")
                        .with(oidcAuthentication())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "k": 1,
                                  "structural_element_ids": [
        "https://e-sbirka.gov.cz/eli/cz/sb/2026/60/2026-05-27/dokument/norma/cast_1/hlava_1/par_3"
      ]
                                }
                                """))
                .andExpect(status().isAccepted())
                .andReturn();

        MvcResult secondStart = mockMvc.perform(post("/legal-acts/2024/1/2024-01-01/class-suggestions-top-k-extraction-jobs")
                        .with(oidcAuthentication())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "k": 3,
                                  "structural_element_ids": [
        "https://e-sbirka.gov.cz/eli/cz/sb/2026/60/2026-05-27/dokument/norma/cast_1/hlava_1/par_3"
      ]
                                }
                                """))
                .andExpect(status().isAccepted())
                .andReturn();

        UUID firstJobId = UUID.fromString(Json.read(firstStart, "job_id"));
        UUID secondJobId = UUID.fromString(Json.read(secondStart, "job_id"));
        waitForAsyncJob(firstJobId, secondJobId);

        mockMvc.perform(post("/like-suggestion")
                        .with(oidcAuthentication())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                [
                                  {
                                    "jobID": "%s",
                                    "suggestionID": ["class_001"]
                                  },
                                  {
                                    "jobID": "%s",
                                    "suggestionID": ["class_002", "class_003"]
                                  }
                                ]
                                """.formatted(firstJobId, secondJobId)))
                .andExpect(status().isNoContent());

        Integer records = jdbcTemplate.queryForObject("""
                        SELECT COUNT(*)
                        FROM feedback_records
                        WHERE feedback_type = ?
                          AND (
                            (job_id = ? AND suggestion_id = ?)
                            OR (job_id = ? AND suggestion_id = ?)
                            OR (job_id = ? AND suggestion_id = ?)
                          )
                        """,
                Integer.class,
                "LIKED",
                firstJobId,
                "class_001",
                secondJobId,
                "class_002",
                secondJobId,
                "class_003");
        assertThat(records).isEqualTo(3);
    }

    @Test
    void recordsConcurrentFeedbackWrites() throws Exception {
        MvcResult start = mockMvc.perform(post("/legal-acts/2024/1/2024-01-01/class-suggestions-top-k-extraction-jobs")
                        .with(oidcAuthentication())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "k": 1,
                                  "structural_element_ids": [
        "https://e-sbirka.gov.cz/eli/cz/sb/2026/60/2026-05-27/dokument/norma/cast_1/hlava_1/par_3"
      ]
                                }
                                """))
                .andExpect(status().isAccepted())
                .andReturn();

        UUID jobId = UUID.fromString(Json.read(start, "job_id"));
        waitForAsyncJob(jobId);
        int writeCount = 20;
        CountDownLatch startGate = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(8);

        try {
            List<Future<Void>> writes = IntStream.range(0, writeCount)
                    .mapToObj(index -> executor.submit((Callable<Void>) () -> {
                        startGate.await();
                        mockMvc.perform(post("/like-suggestion")
                                        .with(oidcAuthentication())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("""
                                                [
                                                  {
                                                    "jobID": "%s",
                                                    "suggestionID": ["class_001"]
                                                  }
                                                ]
                                                """.formatted(jobId)))
                                .andExpect(status().isNoContent());
                        return null;
                    }))
                    .toList();

            startGate.countDown();
            for (Future<Void> write : writes) {
                write.get(5, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
        }

        Integer records = jdbcTemplate.queryForObject("""
                        SELECT COUNT(*)
                        FROM feedback_records
                        WHERE job_id = ?
                """,
                Integer.class,
                jobId);
        assertThat(records).isEqualTo(writeCount);
    }

    @Test
    void returnsNotFoundWhenFeedbackReferencesMissingJob() throws Exception {
        UUID missingJobId = UUID.fromString("00000000-0000-0000-0000-000000009999");

        mockMvc.perform(post("/like-suggestion")
                        .with(oidcAuthentication())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                [
                                  {
                                    "jobID": "%s",
                                    "suggestionID": ["class_001"]
                                  }
                                ]
                                """.formatted(missingJobId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Job not found: " + missingJobId));
    }

    @Test
    void returnsNotFoundWhenFeedbackReferencesMissingSuggestion() throws Exception {
        MvcResult start = mockMvc.perform(post("/legal-acts/2024/1/2024-01-01/class-suggestions-top-k-extraction-jobs")
                        .with(oidcAuthentication())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "k": 1,
                                  "structural_element_ids": [
        "https://e-sbirka.gov.cz/eli/cz/sb/2026/60/2026-05-27/dokument/norma/cast_1/hlava_1/par_3"
      ]
                                }
                                """))
                .andExpect(status().isAccepted())
                .andReturn();

        UUID jobId = UUID.fromString(Json.read(start, "job_id"));
        waitForAsyncJob(jobId);

        mockMvc.perform(post("/like-suggestion")
                        .with(oidcAuthentication())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                [
                                  {
                                    "jobID": "%s",
                                    "suggestionID": ["missing-suggestion-id"]
                                  }
                                ]
                                """.formatted(jobId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Suggestion not found for job " + jobId + ": missing-suggestion-id"));
    }
}
