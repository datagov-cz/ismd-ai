package cz.cvut.ismd.assistant.controller.dto;

import cz.cvut.ismd.assistant.domain.AttributeSuggestion;
import cz.cvut.ismd.assistant.domain.JobStatus;

import java.util.List;
import java.util.UUID;

public record PropertySuggestionsJobResponse(
        UUID jobId,
        String selectedClassId,
        JobStatus status,
        List<AttributeSuggestion> newAttributeSuggestions
) {
}
