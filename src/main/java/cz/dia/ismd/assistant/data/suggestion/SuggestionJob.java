package cz.dia.ismd.assistant.data.suggestion;

import cz.dia.ismd.assistant.records.propertysuggestion.AttributeSuggestion;
import cz.dia.ismd.assistant.records.classsuggestion.ClassSuggestion;
import cz.dia.ismd.assistant.domain.JobStatus;
import cz.dia.ismd.assistant.records.relationshipsuggestion.RelationshipSuggestion;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class SuggestionJob {

    private final UUID jobId;
    private final JobKind kind;
    private final String selectedClassId;
    private final Instant createdAt;
    private volatile JobStatus status;
    private volatile List<ClassSuggestion> classSuggestions;
    private volatile List<AttributeSuggestion> attributeSuggestions;
    private volatile List<RelationshipSuggestion> relationshipSuggestions;

    public SuggestionJob(UUID jobId, JobKind kind, String selectedClassId) {
        this.jobId = jobId;
        this.kind = kind;
        this.selectedClassId = selectedClassId;
        this.createdAt = Instant.now();
        this.status = JobStatus.IN_PROGRESS;
        this.classSuggestions = List.of();
        this.attributeSuggestions = List.of();
        this.relationshipSuggestions = List.of();
    }

    public UUID jobId() {
        return jobId;
    }

    public JobKind kind() {
        return kind;
    }

    public String selectedClassId() {
        return selectedClassId;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public JobStatus status() {
        return status;
    }

    public List<ClassSuggestion> classSuggestions() {
        return classSuggestions;
    }

    public List<AttributeSuggestion> attributeSuggestions() {
        return attributeSuggestions;
    }

    public List<RelationshipSuggestion> relationshipSuggestions() {
        return relationshipSuggestions;
    }

    public void completeClasses(List<ClassSuggestion> suggestions) {
        this.classSuggestions = List.copyOf(suggestions);
        this.status = JobStatus.COMPLETED;
    }

    public void completeAttributes(List<AttributeSuggestion> suggestions) {
        this.attributeSuggestions = List.copyOf(suggestions);
        this.status = JobStatus.COMPLETED;
    }

    public void completeRelationships(List<RelationshipSuggestion> suggestions) {
        this.relationshipSuggestions = List.copyOf(suggestions);
        this.status = JobStatus.COMPLETED;
    }

    public void fail() {
        this.status = JobStatus.FAILED;
    }
}
