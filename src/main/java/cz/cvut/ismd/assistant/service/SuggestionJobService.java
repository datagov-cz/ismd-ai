package cz.cvut.ismd.assistant.service;

import cz.cvut.ismd.assistant.controller.dto.ClassSuggestionJobRequest;
import cz.cvut.ismd.assistant.controller.dto.PropertySuggestionJobRequest;
import cz.cvut.ismd.assistant.data.JobKind;
import cz.cvut.ismd.assistant.data.SuggestionJob;
import cz.cvut.ismd.assistant.domain.DocumentContext;
import cz.cvut.ismd.assistant.exception.JobNotFoundException;
import cz.cvut.ismd.assistant.exception.TokenLimitReachedException;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneOffset;
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

    public SuggestionJob startRelationshipJob(String userId, DocumentContext context, PropertySuggestionJobRequest request) {
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

    private record DailyTokenUsage(LocalDate date, int usedTokens) {
    }
}
