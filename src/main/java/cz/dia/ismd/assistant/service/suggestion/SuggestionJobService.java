package cz.dia.ismd.assistant.service.suggestion;

import cz.dia.ismd.assistant.dto.classsuggestion.ClassSuggestionJobRequest;
import cz.dia.ismd.assistant.dto.propertysuggestion.PropertySuggestionJobRequest;
import cz.dia.ismd.assistant.dto.relationshipsuggestion.RelationshipSuggestionJobRequest;
import cz.dia.ismd.assistant.data.suggestion.JobKind;
import cz.dia.ismd.assistant.data.suggestion.SuggestionJob;
import cz.dia.ismd.assistant.records.suggestion.DocumentContext;
import cz.dia.ismd.assistant.exception.JobNotFoundException;
import cz.dia.ismd.assistant.exception.TokenLimitReachedException;
import cz.dia.ismd.assistant.records.tokenusage.DailyTokenUsage;
import cz.dia.ismd.assistant.records.tokenusage.TokenUsageProperties;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class SuggestionJobService {

    private final SuggestionGenerator suggestionGenerator;
    private final TokenUsageProperties tokenUsageProperties;
    private final Map<UUID, SuggestionJob> jobs = new ConcurrentHashMap<>();
    private final Map<String, DailyTokenUsage> dailyTokenUsage = new ConcurrentHashMap<>();

    public SuggestionJobService(SuggestionGenerator suggestionGenerator, TokenUsageProperties tokenUsageProperties) {
        this.suggestionGenerator = suggestionGenerator;
        this.tokenUsageProperties = tokenUsageProperties;
    }

    public SuggestionJob startClassJob(String userId, DocumentContext context, ClassSuggestionJobRequest request) {
        SuggestionJob job = createJob(userId, request.effectiveK(), JobKind.CLASS, null);
        CompletableFuture.runAsync(() -> {
            try {
                pauseBriefly();
                job.completeClasses(suggestionGenerator.classSuggestions(context, request));
            } catch (RuntimeException exception) {
                job.fail();
            }
        });
        return job;
    }

    public SuggestionJob startPropertyJob(String userId, DocumentContext context, PropertySuggestionJobRequest request) {
        SuggestionJob job = createJob(userId, request.effectiveK(), JobKind.PROPERTY, request.selectedClassId());
        CompletableFuture.runAsync(() -> {
            try {
                pauseBriefly();
                job.completeAttributes(suggestionGenerator.attributeSuggestions(context, request));
            } catch (RuntimeException exception) {
                job.fail();
            }
        });
        return job;
    }

    public SuggestionJob startRelationshipJob(String userId, DocumentContext context, RelationshipSuggestionJobRequest request) {
        SuggestionJob job = createJob(userId, request.effectiveK(), JobKind.RELATIONSHIP, request.selectedClassId());
        CompletableFuture.runAsync(() -> {
            try {
                pauseBriefly();
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

    private SuggestionJob createJob(String userId, int tokenCost, JobKind kind, String selectedClassId) {
        UUID jobId = UUID.randomUUID();
        consumeTokens(jobId, userId, tokenCost);
        SuggestionJob job = new SuggestionJob(jobId, kind, selectedClassId);
        jobs.put(jobId, job);
        return job;
    }

    private void consumeTokens(UUID jobId, String userId, int tokenCost) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        dailyTokenUsage.compute(userId, (key, currentUsage) -> {
            int currentTokens = currentUsage == null || !currentUsage.date().equals(today)
                    ? 0
                    : currentUsage.usedTokens();
            int updatedTokens = currentTokens + tokenCost;
            if (updatedTokens > tokenUsageProperties.maxAllowedPerDay()) {
                throw new TokenLimitReachedException(jobId, userId);
            }
            return new DailyTokenUsage(today, updatedTokens);
        });
    }

    private void pauseBriefly() {
        try {
            Thread.sleep(75);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Job interrupted", exception);
        }
    }
}
