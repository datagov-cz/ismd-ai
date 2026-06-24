package cz.dia.ismd.assistant.dto.feedback;

import com.fasterxml.jackson.annotation.JsonProperty;
import cz.dia.ismd.assistant.data.feedback.FeedbackType;
import cz.dia.ismd.assistant.records.feedback.FeedbackRecord;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record FeedbackRequest(
        @JsonProperty("jobID")
        @NotNull UUID jobId,
        @JsonProperty("suggestionID")
        @NotEmpty List<@NotBlank String> suggestionIds
) {
    public List<FeedbackRecord> toRecords(FeedbackType type) {
        Instant createdAt = Instant.now();
        return suggestionIds.stream()
                .map(suggestionId -> new FeedbackRecord(jobId, suggestionId, type, createdAt))
                .toList();
    }
}
