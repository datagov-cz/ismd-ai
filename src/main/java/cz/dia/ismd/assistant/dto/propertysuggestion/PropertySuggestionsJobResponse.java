package cz.dia.ismd.assistant.dto.propertysuggestion;

import cz.dia.ismd.assistant.records.propertysuggestion.AttributeSuggestion;
import cz.dia.ismd.assistant.domain.JobStatus;

import java.util.List;
import java.util.UUID;

public record PropertySuggestionsJobResponse(
        UUID jobId,
        String selectedClassId,
        JobStatus status,
        List<AttributeSuggestion> newAttributeSuggestions
) {
}
