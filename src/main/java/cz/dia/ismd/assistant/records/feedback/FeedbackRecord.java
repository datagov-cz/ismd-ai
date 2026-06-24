package cz.dia.ismd.assistant.records.feedback;

import cz.dia.ismd.assistant.data.feedback.FeedbackType;

import java.time.Instant;
import java.util.UUID;

public record FeedbackRecord(UUID jobId, String suggestionId, FeedbackType type, Instant createdAt) {
}
