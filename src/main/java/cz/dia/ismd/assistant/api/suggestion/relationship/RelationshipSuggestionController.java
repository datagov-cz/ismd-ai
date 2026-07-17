package cz.dia.ismd.assistant.api.suggestion.relationship;

import cz.dia.ismd.assistant.api.DevelopmentApiResponses;
import cz.dia.ismd.assistant.api.job.JobStartResponse;
import cz.dia.ismd.assistant.model.job.JobStatus;
import cz.dia.ismd.assistant.model.job.SuggestionJob;
import cz.dia.ismd.assistant.config.ApiEnvironment;
import cz.dia.ismd.assistant.service.SuggestionJobService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
public class RelationshipSuggestionController implements RelationshipSuggestionApi {

    private final SuggestionJobService suggestionJobService;
    private final ApiEnvironment apiEnvironment;
    private final DevelopmentApiResponses developmentApiResponses;

    public RelationshipSuggestionController(
            SuggestionJobService suggestionJobService,
            ApiEnvironment apiEnvironment,
            DevelopmentApiResponses developmentApiResponses
    ) {
        this.suggestionJobService = suggestionJobService;
        this.apiEnvironment = apiEnvironment;
        this.developmentApiResponses = developmentApiResponses;
    }

    @PostMapping("/legal-acts/{year}/{number}/{date}/relationship-suggestions-top-k-extraction-jobs")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Override
    public JobStartResponse startRelationshipSuggestions(
            @PathVariable int year,
            @PathVariable int number,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody RelationshipSuggestionJobRequest request
    ) {
        if (apiEnvironment.isDevelopment()) {
            return developmentApiResponses.startRelationshipSuggestions();
        }
        SuggestionJob job = suggestionJobService.startRelationshipJob(jwt.getSubject(), request);
        return new JobStartResponse(job.jobId(), JobStatus.IN_PROGRESS);
    }

    @GetMapping("/legal-acts/relationship-suggestions-jobs")
    @Override
    public List<RelationshipSuggestionsJobResponse> getRelationshipSuggestions(
            @RequestParam List<UUID> jobIds
    ) {
        if (apiEnvironment.isDevelopment()) {
            return developmentApiResponses.relationshipSuggestions();
        }
        return suggestionJobService.getAll(jobIds).stream()
                .map(this::toResponse)
                .toList();
    }

    private RelationshipSuggestionsJobResponse toResponse(SuggestionJob job) {
        return new RelationshipSuggestionsJobResponse(
                job.jobId(),
                job.selectedClassId(),
                job.status(),
                job.relationshipSuggestions()
        );
    }
}
