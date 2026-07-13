package cz.dia.ismd.assistant.model.feedback;


import java.time.Instant;
import java.util.UUID;

public record FeedbackRecord(UUID jobId, String suggestionId, FeedbackType type, Instant createdAt) {
}
