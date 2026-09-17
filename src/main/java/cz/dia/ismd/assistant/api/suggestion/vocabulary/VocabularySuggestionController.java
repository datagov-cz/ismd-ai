package cz.dia.ismd.assistant.api.suggestion.vocabulary;

import cz.dia.ismd.assistant.api.DevelopmentApiResponses;
import cz.dia.ismd.assistant.api.job.JobStartResponse;
import cz.dia.ismd.assistant.config.ApiEnvironment;
import cz.dia.ismd.assistant.exception.JobNotFoundException;
import cz.dia.ismd.assistant.model.job.JobKind;
import cz.dia.ismd.assistant.model.job.JobStatus;
import cz.dia.ismd.assistant.model.job.SuggestionJob;
import cz.dia.ismd.assistant.service.JobQueryValidator;
import cz.dia.ismd.assistant.service.SuggestionJobService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
public class VocabularySuggestionController {
    private final SuggestionJobService jobs;
    private final JobQueryValidator queryValidator;
    private final ApiEnvironment environment;
    private final DevelopmentApiResponses development;

    public VocabularySuggestionController(SuggestionJobService jobs, JobQueryValidator queryValidator,
                                          ApiEnvironment environment, DevelopmentApiResponses development) {
        this.jobs = jobs;
        this.queryValidator = queryValidator;
        this.environment = environment;
        this.development = development;
    }

    @Operation(summary = "Generate a linked vocabulary proposal in one asynchronous job",
            description = "Directly generates classes, then properties and relationships. No vocabulary or final concept IRIs are created.")
    @ApiResponse(responseCode = "202", description = "Job accepted; poll vocabulary-suggestions-jobs for its result")
    @ApiResponse(responseCode = "422", description = "Invalid request or legal act context")
    @ApiResponse(responseCode = "429", description = "Daily token limit reached")
    @ApiResponse(responseCode = "503", description = "Background worker queue is full; retry later")
    @PostMapping("/legal-acts/{year}/{number}/{date}/vocabulary-suggestions-jobs")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public JobStartResponse start(
            @PathVariable int year, @PathVariable int number,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody VocabularySuggestionJobRequest request) {
        VocabularySuggestionJobRequest resolved = request.forLegalAct(year, number, date);
        if (environment.isDevelopment()) return development.startVocabularySuggestions();
        SuggestionJob job = jobs.startVocabularyJob(jwt.getSubject(), resolved);
        return new JobStartResponse(job.jobId(), JobStatus.IN_PROGRESS);
    }

    @Operation(summary = "Generate additional classes, properties or relationships for the supplied working model",
            description = "Returns only new concepts, not the supplied model. For properties/relationships select a known class termID. Poll the existing vocabulary GET.")
    @PostMapping("/legal-acts/{year}/{number}/{date}/vocabulary-suggestions-jobs/expand")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public JobStartResponse expand(
            @PathVariable int year, @PathVariable int number,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody VocabularyExpansionJobRequest request) {
        var resolved = request.forLegalAct(year, number, date);
        if (environment.isDevelopment()) return development.expandVocabulary(resolved);
        var job = jobs.expandVocabulary(jwt.getSubject(), resolved);
        return new JobStartResponse(job.jobId(), JobStatus.IN_PROGRESS);
    }

    @Operation(summary = "Regenerate metadata of one unsaved concept while preserving its ref and all graph edges",
            description = "Send the current model and concept_ref. The result contains zero or one replacement. Poll the existing vocabulary GET.")
    @PostMapping("/legal-acts/{year}/{number}/{date}/vocabulary-suggestions-jobs/regenerate")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public JobStartResponse regenerate(
            @PathVariable int year, @PathVariable int number,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody VocabularyRegenerationJobRequest request) {
        var resolved = request.forLegalAct(year, number, date);
        if (environment.isDevelopment()) return development.regenerateVocabulary(resolved);
        var job = jobs.regenerateVocabularyConcept(jwt.getSubject(), resolved);
        return new JobStartResponse(job.jobId(), JobStatus.IN_PROGRESS);
    }

    @Operation(summary = "Read vocabulary job progress and linked proposals",
            description = "Failed jobs retain validated completed batches. Only status=completed denotes a complete proposal. Use a concept's ref in the suggestionID array for feedback.")
    @ApiResponse(responseCode = "200", description = "Vocabulary jobs")
    @ApiResponse(responseCode = "400", description = "Too many job identifiers")
    @ApiResponse(responseCode = "404", description = "Job missing or not a vocabulary job")
    @GetMapping("/legal-acts/vocabulary-suggestions-jobs")
    public List<VocabularySuggestionsJobResponse> get(@RequestParam List<UUID> jobIds) {
        queryValidator.validate(jobIds);
        if (environment.isDevelopment()) return development.vocabularySuggestions(jobIds);
        return jobs.getAll(jobIds).stream().map(job -> {
            if (job.kind() != JobKind.VOCABULARY) throw new JobNotFoundException(job.jobId());
            synchronized (job) {
                return new VocabularySuggestionsJobResponse(job.jobId(), job.status(), job.vocabularyDraft());
            }
        }).toList();
    }
}
