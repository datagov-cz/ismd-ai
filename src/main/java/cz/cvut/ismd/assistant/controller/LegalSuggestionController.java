package cz.cvut.ismd.assistant.controller;

import cz.cvut.ismd.assistant.domain.DocumentContext;
import cz.cvut.ismd.assistant.data.SuggestionJob;
import cz.cvut.ismd.assistant.controller.dto.ClassSuggestionJobRequest;
import cz.cvut.ismd.assistant.controller.dto.ClassSuggestionsJobResponse;
import cz.cvut.ismd.assistant.controller.dto.JobStartResponse;
import cz.cvut.ismd.assistant.controller.dto.PropertySuggestionJobRequest;
import cz.cvut.ismd.assistant.controller.dto.PropertySuggestionsJobResponse;
import cz.cvut.ismd.assistant.controller.dto.SelectedClassJobStartResponse;
import cz.cvut.ismd.assistant.service.SuggestionJobService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

@RestController
public class LegalSuggestionController {

    private final SuggestionJobService suggestionJobService;

    public LegalSuggestionController(SuggestionJobService suggestionJobService) {
        this.suggestionJobService = suggestionJobService;
    }

    @PostMapping("/legal-acts/{year}/{number}/{date}/class-suggestions-top-k-extraction-jobs")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public JobStartResponse startClassSuggestions(
            @PathVariable int year,
            @PathVariable int number,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ClassSuggestionJobRequest request
    ) {
        SuggestionJob job = suggestionJobService.startClassJob(jwt.getSubject(), DocumentContext.legal(year, number, date), request);
        return new JobStartResponse(job.jobId(), job.status());
    }

    @GetMapping("/legal-acts/{year}/{number}/{date}/class-suggestions-jobs/{jobId}")
    public ClassSuggestionsJobResponse getClassSuggestions(@PathVariable UUID jobId) {
        SuggestionJob job = suggestionJobService.get(jobId);
        return new ClassSuggestionsJobResponse(job.jobId(), job.status(), job.classSuggestions());
    }

    @PostMapping("/legal-acts/{year}/{number}/{date}/property-suggestions-top-k-extraction-jobs")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public SelectedClassJobStartResponse startPropertySuggestions(
            @PathVariable int year,
            @PathVariable int number,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody PropertySuggestionJobRequest request
    ) {
        SuggestionJob job = suggestionJobService.startPropertyJob(jwt.getSubject(), DocumentContext.legal(year, number, date), request);
        return new SelectedClassJobStartResponse(job.jobId(), job.selectedClassId(), job.status());
    }

    @GetMapping("/legal-acts/{year}/{number}/{date}/property-suggestions-jobs/{jobId}")
    public PropertySuggestionsJobResponse getPropertySuggestions(@PathVariable UUID jobId) {
        SuggestionJob job = suggestionJobService.get(jobId);
        return new PropertySuggestionsJobResponse(
                job.jobId(),
                job.selectedClassId(),
                job.status(),
                job.attributeSuggestions()
        );
    }
}
