package cz.dia.ismd.assistant.controller.relationshipsuggestion;

import cz.dia.ismd.assistant.data.suggestion.SuggestionJob;
import cz.dia.ismd.assistant.dto.propertysuggestion.PropertySuggestionJobRequest;
import cz.dia.ismd.assistant.dto.propertysuggestion.SelectedClassJobStartResponse;
import cz.dia.ismd.assistant.dto.relationshipsuggestion.RelationshipSuggestionsJobResponse;
import cz.dia.ismd.assistant.records.suggestion.DocumentContext;
import cz.dia.ismd.assistant.service.suggestion.SuggestionJobService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

@RestController
public class RelationshipSuggestionController {

    private final SuggestionJobService suggestionJobService;

    public RelationshipSuggestionController(SuggestionJobService suggestionJobService) {
        this.suggestionJobService = suggestionJobService;
    }

    @PostMapping("/legal-acts/{year}/{number}/{date}/relationship-suggestions-top-k-extraction-jobs")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public SelectedClassJobStartResponse startRelationshipSuggestions(
            @PathVariable int year,
            @PathVariable int number,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody PropertySuggestionJobRequest request
    ) {
        SuggestionJob job = suggestionJobService.startRelationshipJob(jwt.getSubject(), DocumentContext.legal(year, number, date), request);
        return new SelectedClassJobStartResponse(job.jobId(), job.status());
    }

    @GetMapping("/legal-acts/relationship-suggestions-jobs/{jobId}")
    public RelationshipSuggestionsJobResponse getRelationshipSuggestions(
            @PathVariable UUID jobId
    ) {
        SuggestionJob job = suggestionJobService.get(jobId);
        return new RelationshipSuggestionsJobResponse(
                job.jobId(),
                job.status(),
                job.relationshipSuggestions()
        );
    }
}
