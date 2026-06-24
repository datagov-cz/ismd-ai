package cz.dia.ismd.assistant.service.feedback;

import cz.dia.ismd.assistant.records.feedback.FeedbackRecord;
import cz.dia.ismd.assistant.data.feedback.FeedbackType;
import cz.dia.ismd.assistant.service.suggestion.SuggestionJobService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class FeedbackService {

    private static final RowMapper<FeedbackRecord> FEEDBACK_RECORD_ROW_MAPPER = (resultSet, rowNum) ->
            new FeedbackRecord(
                    UUID.fromString(resultSet.getString("job_id")),
                    resultSet.getString("suggestion_id"),
                    FeedbackType.valueOf(resultSet.getString("feedback_type")),
                    Instant.parse(resultSet.getString("created_at"))
            );

    private final SuggestionJobService suggestionJobService;
    private final JdbcTemplate jdbcTemplate;
    private final Object writeMonitor = new Object();

    public FeedbackService(SuggestionJobService suggestionJobService, JdbcTemplate jdbcTemplate) {
        this.suggestionJobService = suggestionJobService;
        this.jdbcTemplate = jdbcTemplate;
    }

    public void record(FeedbackRecord record) {
        record(List.of(record));
    }

    public void record(List<FeedbackRecord> records) {
        if (records.isEmpty()) {
            return;
        }

        records.stream()
                .map(FeedbackRecord::jobId)
                .distinct()
                .forEach(suggestionJobService::ensureExists);

        synchronized (writeMonitor) {
            jdbcTemplate.batchUpdate("""
                            INSERT INTO feedback_records(job_id, suggestion_id, feedback_type, created_at)
                            VALUES (?, ?, ?, ?)
                            """,
                    records,
                    records.size(),
                    (statement, record) -> {
                        statement.setString(1, record.jobId().toString());
                        statement.setString(2, record.suggestionId());
                        statement.setString(3, record.type().name());
                        statement.setString(4, record.createdAt().toString());
                    });
        }
    }

    public void record(UUID jobId, String suggestionId, FeedbackType type) {
        record(new FeedbackRecord(jobId, suggestionId, type, Instant.now()));
    }

    public List<FeedbackRecord> records() {
        return jdbcTemplate.query("""
                SELECT job_id, suggestion_id, feedback_type, created_at
                FROM feedback_records
                ORDER BY id
                """, FEEDBACK_RECORD_ROW_MAPPER);
    }
}
