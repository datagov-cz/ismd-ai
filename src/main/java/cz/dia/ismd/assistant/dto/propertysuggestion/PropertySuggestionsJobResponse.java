package cz.dia.ismd.assistant.dto.propertysuggestion;

import cz.dia.ismd.assistant.records.propertysuggestion.AttributeSuggestion;
import cz.dia.ismd.assistant.domain.JobStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.UUID;

public record PropertySuggestionsJobResponse(
        @Schema(description = "Identifier of the property suggestion job.")
        UUID jobId,
        @Schema(description = "Identifier of the class for which attributes were suggested.")
        String selectedClassId,
        @Schema(description = "Current processing status of the property suggestion job.")
        JobStatus status,
        @Schema(description = "New attribute terms suggested for the selected class.")
        List<AttributeSuggestion> newAttributeSuggestions
) {
}
