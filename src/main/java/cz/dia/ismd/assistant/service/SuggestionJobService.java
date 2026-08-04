package cz.dia.ismd.assistant.service;

import cz.dia.ismd.assistant.api.suggestion.classsuggestion.ClassSuggestionJobRequest;
import cz.dia.ismd.assistant.api.suggestion.attribute.PropertySuggestionJobRequest;
import cz.dia.ismd.assistant.api.suggestion.relationship.RelationshipSuggestionJobRequest;
import cz.dia.ismd.assistant.model.job.JobKind;
import cz.dia.ismd.assistant.model.job.SuggestionJob;
import cz.dia.ismd.assistant.model.legal.LegalActText;
import cz.dia.ismd.assistant.model.suggestion.attribute.AttributeSuggestion;
import cz.dia.ismd.assistant.model.suggestion.classsuggestion.ClassSuggestion;
import cz.dia.ismd.assistant.model.suggestion.relationship.RelationshipSuggestion;
import cz.dia.ismd.assistant.exception.JobNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class SuggestionJobService {

    private static final Logger log = LoggerFactory.getLogger(SuggestionJobService.class);
    private static final String ELI_PATH_PREFIX = "/eli/cz/sb/";

    private final ClassSuggestionLlmService classSuggestionLlmService;
    private final PropertySuggestionLlmService propertySuggestionLlmService;
    private final RelationshipSuggestionLlmService relationshipSuggestionLlmService;
    private final LegalActSPARQLService legalActSPARQLService;
    private final TokenUsageService tokenUsageService;
    private final SuggestionJobRepository suggestionJobRepository;
    private final Map<UUID, SuggestionJob> jobs = new ConcurrentHashMap<>();

    @Autowired
    public SuggestionJobService(
            ClassSuggestionLlmService classSuggestionLlmService,
            PropertySuggestionLlmService propertySuggestionLlmService,
            RelationshipSuggestionLlmService relationshipSuggestionLlmService,
            LegalActSPARQLService legalActSPARQLService,
            TokenUsageService tokenUsageService,
            SuggestionJobRepository suggestionJobRepository
    ) {
        this.classSuggestionLlmService = classSuggestionLlmService;
        this.propertySuggestionLlmService = propertySuggestionLlmService;
        this.relationshipSuggestionLlmService = relationshipSuggestionLlmService;
        this.legalActSPARQLService = legalActSPARQLService;
        this.tokenUsageService = tokenUsageService;
        this.suggestionJobRepository = suggestionJobRepository;
    }

    SuggestionJobService(
            ClassSuggestionLlmService classSuggestionLlmService,
            PropertySuggestionLlmService propertySuggestionLlmService,
            RelationshipSuggestionLlmService relationshipSuggestionLlmService,
            LegalActSPARQLService legalActSPARQLService,
            TokenUsageService tokenUsageService
    ) {
        this(classSuggestionLlmService, propertySuggestionLlmService, relationshipSuggestionLlmService,
                legalActSPARQLService, tokenUsageService, null);
    }

    public SuggestionJob startClassJob(String userId, ClassSuggestionJobRequest request) {
        tokenUsageService.ensureRequestAllowed(userId);
        SuggestionJob job = createJob(JobKind.CLASS, null);
        CompletableFuture.runAsync(() -> {
            try {
                List<LegalActText> legalActTexts = retrieveLegalActTexts(job, request.structuralElementIds());
                StreamingSuggestions<ClassSuggestion> streamed =
                        classSuggestionLlmService.streamClasses(
                                userId, request, legalActTexts, suggestion -> {
                                    job.addClassSuggestion(suggestion);
                                    persist(job);
                                });
                // Allows existing custom/mock implementations that only implement the buffered method.
                List<ClassSuggestion> suggestions =
                        streamed == null
                                ? classSuggestionLlmService.suggestClasses(userId, request, legalActTexts)
                                : streamed.suggestions();
                job.completeClasses(suggestions);
                persist(job);
            } catch (RuntimeException exception) {
                failJob(job, exception);
            }
        });
        return job;
    }

    public SuggestionJob startPropertyJob(String userId, PropertySuggestionJobRequest request) {
        tokenUsageService.ensureRequestAllowed(userId);
        SuggestionJob job = createJob(JobKind.PROPERTY, request.selectedClassId());
        CompletableFuture.runAsync(() -> {
            try {
                List<LegalActText> legalActTexts = retrieveLegalActTexts(job, request.structuralElementIds());
                StreamingSuggestions<AttributeSuggestion> streamed =
                        propertySuggestionLlmService.streamProperties(
                                userId, request, legalActTexts, suggestion -> {
                                    job.addAttributeSuggestion(suggestion);
                                    persist(job);
                                });
                List<AttributeSuggestion> suggestions =
                        streamed == null
                                ? propertySuggestionLlmService.suggestProperties(userId, request, legalActTexts)
                                : streamed.suggestions();
                job.completeAttributes(suggestions);
                persist(job);
            } catch (RuntimeException exception) {
                failJob(job, exception);
            }
        });
        return job;
    }

    public SuggestionJob startRelationshipJob(String userId, RelationshipSuggestionJobRequest request) {
        tokenUsageService.ensureRequestAllowed(userId);
        SuggestionJob job = createJob(JobKind.RELATIONSHIP, request.selectedClassId());
        CompletableFuture.runAsync(() -> {
            try {
                List<LegalActText> legalActTexts = retrieveLegalActTexts(job, request.structuralElementIds());
                StreamingSuggestions<RelationshipSuggestion> streamed =
                        relationshipSuggestionLlmService.streamRelationships(
                                userId, request, legalActTexts, suggestion -> {
                                    job.addRelationshipSuggestion(suggestion);
                                    persist(job);
                                });
                List<RelationshipSuggestion> suggestions =
                        streamed == null
                                ? relationshipSuggestionLlmService.suggestRelationships(
                                        userId, request, legalActTexts)
                                : streamed.suggestions();
                job.completeRelationships(suggestions);
                persist(job);
            } catch (RuntimeException exception) {
                failJob(job, exception);
            }
        });
        return job;
    }

    public SuggestionJob get(UUID jobId) {
        SuggestionJob job = jobs.get(jobId);
        if (job == null && suggestionJobRepository != null) {
            job = suggestionJobRepository.findById(jobId).orElse(null);
            if (job != null) {
                SuggestionJob existing = jobs.putIfAbsent(jobId, job);
                if (existing != null) {
                    job = existing;
                }
            }
        }
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
        if (suggestionJobRepository != null) {
            suggestionJobRepository.insert(job);
        }
        jobs.put(jobId, job);
        return job;
    }

    private List<LegalActText> retrieveLegalActTexts(SuggestionJob job, List<String> structuralElementIds) {
        List<String> identifiers = structuralElementIds == null ? List.of() : structuralElementIds;
        List<LegalActText> texts = legalActSPARQLService.retrieveLegalActTexts(identifiers);

        Set<String> retrievedPaths = texts.stream()
                .map(LegalActText::path)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        List<String> missingIdentifiers = identifiers.stream()
                .distinct()
                .filter(identifier -> retrievedPaths.stream().noneMatch(path -> belongsTo(identifier, path)))
                .toList();

        missingIdentifiers.forEach(identifier -> log.warn(
                "Legal text was not found for structural element: jobId={}, structuralElementId={}",
                job.jobId(),
                identifier
        ));
        if (identifiers.isEmpty() || missingIdentifiers.size() == identifiers.stream().distinct().count()) {
            throw new IllegalStateException("No legal texts were found for the requested structural elements");
        }
        return texts;
    }

    private boolean belongsTo(String identifier, String retrievedPath) {
        String requestedPath = identifier;
        int prefixIndex = requestedPath.lastIndexOf(ELI_PATH_PREFIX);
        if (prefixIndex >= 0) {
            requestedPath = requestedPath.substring(prefixIndex + ELI_PATH_PREFIX.length());
        } else if (requestedPath.startsWith("/")) {
            requestedPath = requestedPath.substring(1);
        }
        return retrievedPath.equals(requestedPath) || retrievedPath.startsWith(requestedPath + "/");
    }

    private void failJob(SuggestionJob job, RuntimeException exception) {
        log.error(
                "Suggestion job failed: jobId={}, kind={}, selectedClassId={}",
                job.jobId(),
                job.kind(),
                job.selectedClassId(),
                exception
        );
        job.fail();
        persist(job);
    }

    private void persist(SuggestionJob job) {
        if (suggestionJobRepository != null) {
            suggestionJobRepository.update(job);
        }
    }

}
