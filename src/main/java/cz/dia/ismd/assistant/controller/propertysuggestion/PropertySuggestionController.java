package cz.dia.ismd.assistant.controller.propertysuggestion;

import cz.dia.ismd.assistant.dto.classsuggestion.JobStartResponse;
import cz.dia.ismd.assistant.dto.propertysuggestion.PropertySuggestionJobRequest;
import cz.dia.ismd.assistant.dto.propertysuggestion.PropertySuggestionsJobResponse;
import cz.dia.ismd.assistant.data.suggestion.SuggestionJob;
import cz.dia.ismd.assistant.records.suggestion.DocumentContext;
import cz.dia.ismd.assistant.service.environment.ApiEnvironment;
import cz.dia.ismd.assistant.service.mock.DevelopmentApiResponses;
import cz.dia.ismd.assistant.service.suggestion.SuggestionJobService;
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
public class PropertySuggestionController implements PropertySuggestionApi {

    private final SuggestionJobService suggestionJobService;
    private final ApiEnvironment apiEnvironment;
    private final DevelopmentApiResponses developmentApiResponses;

    public PropertySuggestionController(
            SuggestionJobService suggestionJobService,
            ApiEnvironment apiEnvironment,
            DevelopmentApiResponses developmentApiResponses
    ) {
        this.suggestionJobService = suggestionJobService;
        this.apiEnvironment = apiEnvironment;
        this.developmentApiResponses = developmentApiResponses;
    }

    @PostMapping("/legal-acts/{year}/{number}/{date}/property-suggestions-top-k-extraction-jobs")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Override
    public JobStartResponse startPropertySuggestions(
            @PathVariable int year,
            @PathVariable int number,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody PropertySuggestionJobRequest request
    ) {
        if (apiEnvironment.isDevelopment()) {
            return developmentApiResponses.startPropertySuggestions();
        }
        SuggestionJob job = suggestionJobService.startPropertyJob(jwt.getSubject(), DocumentContext.legal(year, number, date), request);
        return new JobStartResponse(job.jobId(), job.status());
    }

    @GetMapping("/legal-acts/property-suggestions-jobs")
    @Override
    public List<PropertySuggestionsJobResponse> getPropertySuggestions(
            @RequestParam List<UUID> jobIds
    ) {
        if (apiEnvironment.isDevelopment()) {
            return developmentApiResponses.propertySuggestions();
        }
        return suggestionJobService.getAll(jobIds).stream()
                .map(this::toResponse)
                .toList();
    }

    private PropertySuggestionsJobResponse toResponse(SuggestionJob job) {
        return new PropertySuggestionsJobResponse(
                job.jobId(),
                job.selectedClassId(),
                job.status(),
                job.attributeSuggestions()
        );
    }
}
