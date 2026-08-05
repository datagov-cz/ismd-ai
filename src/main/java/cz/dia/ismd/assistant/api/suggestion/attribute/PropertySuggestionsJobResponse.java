package cz.dia.ismd.assistant.api.suggestion.attribute;

import cz.dia.ismd.assistant.model.suggestion.attribute.AttributeSuggestion;
import cz.dia.ismd.assistant.model.job.JobStatus;
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
        @Schema(description = "All attribute terms generated for the selected class so far.")
        List<AttributeSuggestion> newAttributeSuggestions
) {
}
