package cz.dia.ismd.assistant.model.job;

import cz.dia.ismd.assistant.model.suggestion.attribute.AttributeSuggestion;
import cz.dia.ismd.assistant.model.suggestion.classsuggestion.ClassSuggestion;
import cz.dia.ismd.assistant.model.suggestion.relationship.RelationshipSuggestion;

import cz.dia.ismd.assistant.model.suggestion.vocabulary.VocabularyDraft;

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
    private volatile VocabularyDraft vocabularyDraft = VocabularyDraft.empty();
    private volatile List<ClassSuggestion> classSuggestions;
    private volatile List<AttributeSuggestion> attributeSuggestions;
    private volatile List<RelationshipSuggestion> relationshipSuggestions;

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
    }

    public synchronized void addAttributeSuggestion(AttributeSuggestion suggestion) {
        attributeSuggestions = append(attributeSuggestions, suggestion);
    }

    public synchronized void addRelationshipSuggestion(RelationshipSuggestion suggestion) {
        relationshipSuggestions = append(relationshipSuggestions, suggestion);
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

    public VocabularyDraft vocabularyDraft() {
        return vocabularyDraft;
    }

    public synchronized void updateVocabularyDraft(VocabularyDraft draft) {
        this.vocabularyDraft = draft;
    }

    public synchronized void completeVocabulary() {
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

}
