package cz.dia.ismd.assistant.service.feedback;

import cz.dia.ismd.assistant.data.suggestion.SuggestionJob;
import cz.dia.ismd.assistant.exception.SuggestionNotFoundException;
import cz.dia.ismd.assistant.records.classsuggestion.ClassSuggestion;
import cz.dia.ismd.assistant.records.feedback.FeedbackRecord;
import cz.dia.ismd.assistant.data.feedback.FeedbackType;
import cz.dia.ismd.assistant.records.propertysuggestion.AttributeSuggestion;
import cz.dia.ismd.assistant.records.relationshipsuggestion.RelationshipSuggestion;
import cz.dia.ismd.assistant.service.suggestion.SuggestionJobService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Service
public class FeedbackService {

    private static final RowMapper<FeedbackRecord> FEEDBACK_RECORD_ROW_MAPPER = (resultSet, rowNum) ->
            new FeedbackRecord(
                    resultSet.getObject("job_id", UUID.class),
                    resultSet.getString("suggestion_id"),
                    FeedbackType.valueOf(resultSet.getString("feedback_type")),
                    resultSet.getObject("created_at", OffsetDateTime.class).toInstant()
            );

    private final SuggestionJobService suggestionJobService;
    private final JdbcTemplate jdbcTemplate;
    private final Object writeMonitor = new Object();

    public FeedbackService(SuggestionJobService suggestionJobService, JdbcTemplate jdbcTemplate) {
        this.suggestionJobService = suggestionJobService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public void record(FeedbackRecord record) {
        record(List.of(record));
    }

    @Transactional
    public void record(List<FeedbackRecord> records) {
        if (records.isEmpty()) {
            return;
        }

        records.forEach(this::ensureSuggestionExists);

        synchronized (writeMonitor) {
            jdbcTemplate.batchUpdate("""
                            INSERT INTO feedback_records(job_id, suggestion_id, feedback_type, created_at)
                            VALUES (?, ?, ?, ?)
                            """,
                    records,
                    records.size(),
                    (statement, record) -> {
                        statement.setObject(1, record.jobId());
                        statement.setString(2, record.suggestionId());
                        statement.setString(3, record.type().name());
                        statement.setObject(4, record.createdAt().atOffset(ZoneOffset.UTC));
                    });
        }
    }

    @Transactional
    public void record(UUID jobId, String suggestionId, FeedbackType type) {
        record(new FeedbackRecord(jobId, suggestionId, type, Instant.now()));
    }

    private void ensureSuggestionExists(FeedbackRecord record) {
        SuggestionJob job = suggestionJobService.get(record.jobId());
        if (!containsSuggestion(job, record.suggestionId())) {
            throw new SuggestionNotFoundException(record.jobId(), record.suggestionId());
        }
    }

    private boolean containsSuggestion(SuggestionJob job, String suggestionId) {
        return switch (job.kind()) {
            case CLASS -> job.classSuggestions().stream()
                    .map(ClassSuggestion::suggestionID)
                    .anyMatch(suggestionId::equals);
            case PROPERTY -> job.attributeSuggestions().stream()
                    .map(AttributeSuggestion::suggestionID)
                    .anyMatch(suggestionId::equals);
            case RELATIONSHIP -> job.relationshipSuggestions().stream()
                    .map(RelationshipSuggestion::suggestionID)
                    .anyMatch(suggestionId::equals);
        };
    }

    @Transactional(readOnly = true)
    public List<FeedbackRecord> records() {
        return jdbcTemplate.query("""
                SELECT job_id, suggestion_id, feedback_type, created_at
                FROM feedback_records
                ORDER BY id
                """, FEEDBACK_RECORD_ROW_MAPPER);
    }
}
