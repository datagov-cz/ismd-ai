package cz.dia.ismd.assistant.model.job;

import cz.dia.ismd.assistant.model.suggestion.attribute.AttributeSuggestion;
import cz.dia.ismd.assistant.model.suggestion.classsuggestion.ClassSuggestion;
import cz.dia.ismd.assistant.model.suggestion.relationship.RelationshipSuggestion;

import java.time.Instant;
import java.util.List;
import java.util.ArrayList;
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
    private final List<ClassSuggestion> pendingClassSuggestions;
    private final List<AttributeSuggestion> pendingAttributeSuggestions;
    private final List<RelationshipSuggestion> pendingRelationshipSuggestions;

    public SuggestionJob(UUID jobId, JobKind kind, String selectedClassId) {
        this(jobId, kind, selectedClassId, Instant.now(), JobStatus.IN_PROGRESS,
                List.of(), List.of(), List.of());
    }

    public SuggestionJob(
            UUID jobId,
            JobKind kind,
            String selectedClassId,
            Instant createdAt,
            JobStatus status,
            List<ClassSuggestion> classSuggestions,
            List<AttributeSuggestion> attributeSuggestions,
            List<RelationshipSuggestion> relationshipSuggestions
    ) {
        this.jobId = jobId;
        this.kind = kind;
        this.selectedClassId = selectedClassId;
        this.createdAt = createdAt;
        this.status = status;
        this.classSuggestions = List.copyOf(classSuggestions);
        this.attributeSuggestions = List.copyOf(attributeSuggestions);
        this.relationshipSuggestions = List.copyOf(relationshipSuggestions);
        // A job restored from storage has not yet been observed by this application
        // instance, so its persisted suggestions are new to the next API poll.
        this.pendingClassSuggestions = new ArrayList<>(classSuggestions);
        this.pendingAttributeSuggestions = new ArrayList<>(attributeSuggestions);
        this.pendingRelationshipSuggestions = new ArrayList<>(relationshipSuggestions);
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

    public synchronized void addClassSuggestion(ClassSuggestion suggestion) {
        classSuggestions = append(classSuggestions, suggestion);
        pendingClassSuggestions.add(suggestion);
    }

    public synchronized void addAttributeSuggestion(AttributeSuggestion suggestion) {
        attributeSuggestions = append(attributeSuggestions, suggestion);
        pendingAttributeSuggestions.add(suggestion);
    }

    public synchronized void addRelationshipSuggestion(RelationshipSuggestion suggestion) {
        relationshipSuggestions = append(relationshipSuggestions, suggestion);
        pendingRelationshipSuggestions.add(suggestion);
    }

    public synchronized List<ClassSuggestion> drainClassSuggestions() {
        return drain(pendingClassSuggestions);
    }

    public synchronized List<AttributeSuggestion> drainAttributeSuggestions() {
        return drain(pendingAttributeSuggestions);
    }

    public synchronized List<RelationshipSuggestion> drainRelationshipSuggestions() {
        return drain(pendingRelationshipSuggestions);
    }

    public synchronized void completeClasses(List<ClassSuggestion> suggestions) {
        addMissingClasses(suggestions);
        this.status = JobStatus.COMPLETED;
    }

    public synchronized void completeAttributes(List<AttributeSuggestion> suggestions) {
        addMissingAttributes(suggestions);
        this.status = JobStatus.COMPLETED;
    }

    public synchronized void completeRelationships(List<RelationshipSuggestion> suggestions) {
        addMissingRelationships(suggestions);
        this.status = JobStatus.COMPLETED;
    }

    public synchronized void fail() {
        this.status = JobStatus.FAILED;
    }

    private void addMissingClasses(List<ClassSuggestion> suggestions) {
        suggestions.stream().skip(classSuggestions.size()).forEach(this::addClassSuggestion);
    }

    private void addMissingAttributes(List<AttributeSuggestion> suggestions) {
        suggestions.stream().skip(attributeSuggestions.size()).forEach(this::addAttributeSuggestion);
    }

    private void addMissingRelationships(List<RelationshipSuggestion> suggestions) {
        suggestions.stream().skip(relationshipSuggestions.size()).forEach(this::addRelationshipSuggestion);
    }

    private static <T> List<T> append(List<T> existing, T item) {
        List<T> result = new ArrayList<>(existing);
        result.add(item);
        return List.copyOf(result);
    }

    private static <T> List<T> drain(List<T> pending) {
        List<T> result = List.copyOf(pending);
        pending.clear();
        return result;
    }
}
