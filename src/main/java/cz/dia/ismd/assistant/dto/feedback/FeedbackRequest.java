package cz.dia.ismd.assistant.dto.feedback;

import com.fasterxml.jackson.annotation.JsonAlias;
import cz.dia.ismd.assistant.data.feedback.FeedbackType;
import cz.dia.ismd.assistant.records.feedback.FeedbackRecord;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

public record FeedbackRequest(
        @JsonAlias("jobID")
        @NotNull UUID jobId,
        @JsonAlias("suggestionID")
        @NotBlank String suggestionId
) {
    public FeedbackRecord toRecord(FeedbackType type) {
        return new FeedbackRecord(jobId, suggestionId, type, Instant.now());
    }
}
