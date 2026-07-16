package cz.dia.ismd.assistant.service;

import cz.dia.ismd.assistant.api.suggestion.classsuggestion.ClassSuggestionJobRequest;
import cz.dia.ismd.assistant.api.suggestion.attribute.PropertySuggestionJobRequest;
import cz.dia.ismd.assistant.api.suggestion.relationship.RelationshipSuggestionJobRequest;
import cz.dia.ismd.assistant.model.job.JobKind;
import cz.dia.ismd.assistant.model.job.SuggestionJob;
import cz.dia.ismd.assistant.model.suggestion.DocumentContext;
import cz.dia.ismd.assistant.exception.JobNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class SuggestionJobService {

    private final SuggestionGenerator suggestionGenerator;
    private final TokenUsageService tokenUsageService;
    private final Map<UUID, SuggestionJob> jobs = new ConcurrentHashMap<>();

    public SuggestionJobService(SuggestionGenerator suggestionGenerator, TokenUsageService tokenUsageService) {
        this.suggestionGenerator = suggestionGenerator;
        this.tokenUsageService = tokenUsageService;
    }

    public SuggestionJob startClassJob(String userId, DocumentContext context, ClassSuggestionJobRequest request) {
        tokenUsageService.ensureRequestAllowed(userId);
        SuggestionJob job = createJob(JobKind.CLASS, null);
        CompletableFuture.runAsync(() -> {
            try {
                job.completeClasses(suggestionGenerator.classSuggestions(context, request));
            } catch (RuntimeException exception) {
                job.fail();
            }
        });
        return job;
    }

    public SuggestionJob startPropertyJob(String userId, DocumentContext context, PropertySuggestionJobRequest request) {
        tokenUsageService.ensureRequestAllowed(userId);
        SuggestionJob job = createJob(JobKind.PROPERTY, request.selectedClassId());
        CompletableFuture.runAsync(() -> {
            try {
                job.completeAttributes(suggestionGenerator.attributeSuggestions(context, request));
            } catch (RuntimeException exception) {
                job.fail();
            }
        });
        return job;
    }

    public SuggestionJob startRelationshipJob(String userId, DocumentContext context, RelationshipSuggestionJobRequest request) {
        tokenUsageService.ensureRequestAllowed(userId);
        SuggestionJob job = createJob(JobKind.RELATIONSHIP, request.selectedClassId());
        CompletableFuture.runAsync(() -> {
            try {
                job.completeRelationships(suggestionGenerator.relationshipSuggestions(context, request));
            } catch (RuntimeException exception) {
                job.fail();
            }
        });
        return job;
    }

    public SuggestionJob get(UUID jobId) {
        SuggestionJob job = jobs.get(jobId);
        if (job == null) {
            throw new JobNotFoundException(jobId);
        }
        return job;
    }

    public List<SuggestionJob> getAll(List<UUID> jobIds) {
        return jobIds.stream()
                .map(this::get)
                .toList();
    }

    public void ensureExists(UUID jobId) {
        get(jobId);
    }

    private SuggestionJob createJob(JobKind kind, String selectedClassId) {
        UUID jobId = UUID.randomUUID();
        SuggestionJob job = new SuggestionJob(jobId, kind, selectedClassId);
        jobs.put(jobId, job);
        return job;
    }

}
