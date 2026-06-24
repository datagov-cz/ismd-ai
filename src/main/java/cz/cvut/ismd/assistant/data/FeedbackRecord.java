package cz.cvut.ismd.assistant.data;

import java.time.Instant;
import java.util.UUID;

public record FeedbackRecord(UUID jobId, String suggestionId, FeedbackType type, Instant createdAt) {
}
