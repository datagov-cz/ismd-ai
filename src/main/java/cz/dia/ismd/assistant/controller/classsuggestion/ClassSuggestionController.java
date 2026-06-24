package cz.dia.ismd.assistant.controller.classsuggestion;

import cz.dia.ismd.assistant.records.suggestion.DocumentContext;
import cz.dia.ismd.assistant.data.suggestion.SuggestionJob;
import cz.dia.ismd.assistant.dto.classsuggestion.ClassSuggestionJobRequest;
import cz.dia.ismd.assistant.dto.classsuggestion.ClassSuggestionsJobResponse;
import cz.dia.ismd.assistant.dto.classsuggestion.JobStartResponse;
import cz.dia.ismd.assistant.service.suggestion.SuggestionJobService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
public class ClassSuggestionController {

    private final SuggestionJobService suggestionJobService;

    public ClassSuggestionController(SuggestionJobService suggestionJobService) {
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

    @GetMapping("/legal-acts/class-suggestions-jobs/{jobId}")
    public ClassSuggestionsJobResponse getClassSuggestions(
            @PathVariable UUID jobId
    ) {
        SuggestionJob job = suggestionJobService.get(jobId);
        return toResponse(job);
    }

    @GetMapping("/legal-acts/class-suggestions-jobs")
    public List<ClassSuggestionsJobResponse> getClassSuggestions(
            @RequestParam List<UUID> jobIds
    ) {
        return suggestionJobService.getAll(jobIds).stream()
                .map(this::toResponse)
                .toList();
    }

    private ClassSuggestionsJobResponse toResponse(SuggestionJob job) {
        return new ClassSuggestionsJobResponse(job.jobId(), job.status(), job.classSuggestions());
    }
}
