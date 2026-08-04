package cz.dia.ismd.assistant.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import cz.dia.ismd.assistant.model.job.JobKind;
import cz.dia.ismd.assistant.model.job.JobStatus;
import cz.dia.ismd.assistant.model.job.SuggestionJob;
import cz.dia.ismd.assistant.model.suggestion.attribute.AttributeSuggestion;
import cz.dia.ismd.assistant.model.suggestion.classsuggestion.ClassSuggestion;
import cz.dia.ismd.assistant.model.suggestion.relationship.RelationshipSuggestion;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class SuggestionJobRepository {

    private static final Logger log = LoggerFactory.getLogger(SuggestionJobRepository.class);

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public SuggestionJobRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void failInterruptedJobs() {
        int updatedJobs = jdbcTemplate.update("""
                UPDATE suggestion_jobs
                SET status = 'FAILED'
                WHERE status = 'IN_PROGRESS'
                """);
        log.info("Marked persisted in-progress suggestion jobs as failed: {}", updatedJobs);
    }

    public void insert(SuggestionJob job) {
        jdbcTemplate.update("""
                        INSERT INTO suggestion_jobs(
                            job_id, job_kind, selected_class_id, status, suggestions, created_at
                        )
                        VALUES (?, ?, ?, ?, CAST(? AS JSONB), ?)
                        """,
                job.jobId(),
                job.kind().name(),
                job.selectedClassId(),
                job.status().name(),
                serializeSuggestions(job),
                job.createdAt().atOffset(ZoneOffset.UTC));
    }

    public void update(SuggestionJob job) {
        jdbcTemplate.update("""
                        UPDATE suggestion_jobs
                        SET status = ?, suggestions = CAST(? AS JSONB)
                        WHERE job_id = ?
                        """,
                job.status().name(),
                serializeSuggestions(job),
                job.jobId());
    }

    public Optional<SuggestionJob> findById(UUID jobId) {
        List<SuggestionJob> matches = jdbcTemplate.query("""
                        SELECT job_id, job_kind, selected_class_id, status, suggestions, created_at
                        FROM suggestion_jobs
                        WHERE job_id = ?
                        """,
                (resultSet, rowNum) -> mapJob(resultSet),
                jobId);
        return matches.stream().findFirst();
    }

    private SuggestionJob mapJob(ResultSet resultSet) throws SQLException {
        JobKind kind = JobKind.valueOf(resultSet.getString("job_kind"));
        List<ClassSuggestion> classes = List.of();
        List<AttributeSuggestion> attributes = List.of();
        List<RelationshipSuggestion> relationships = List.of();
        String suggestionsJson = resultSet.getString("suggestions");

        switch (kind) {
            case CLASS -> classes = deserializeSuggestions(suggestionsJson, ClassSuggestion.class);
            case PROPERTY -> attributes = deserializeSuggestions(suggestionsJson, AttributeSuggestion.class);
            case RELATIONSHIP -> relationships = deserializeSuggestions(suggestionsJson, RelationshipSuggestion.class);
        }

        return new SuggestionJob(
                resultSet.getObject("job_id", UUID.class),
                kind,
                resultSet.getString("selected_class_id"),
                resultSet.getObject("created_at", OffsetDateTime.class).toInstant(),
                JobStatus.valueOf(resultSet.getString("status")),
                classes,
                attributes,
                relationships
        );
    }

    private String serializeSuggestions(SuggestionJob job) {
        Object suggestions = switch (job.kind()) {
            case CLASS -> job.classSuggestions();
            case PROPERTY -> job.attributeSuggestions();
            case RELATIONSHIP -> job.relationshipSuggestions();
        };
        try {
            return objectMapper.writeValueAsString(suggestions);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize suggestion job " + job.jobId(), exception);
        }
    }

    private <T> List<T> deserializeSuggestions(String json, Class<T> suggestionType) {
        JavaType listType = objectMapper.getTypeFactory().constructCollectionType(List.class, suggestionType);
        try {
            return objectMapper.readValue(json, listType);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not deserialize persisted suggestion job", exception);
        }
    }
}
