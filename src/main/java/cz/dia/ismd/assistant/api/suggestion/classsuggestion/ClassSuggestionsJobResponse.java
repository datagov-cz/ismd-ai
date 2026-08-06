package cz.dia.ismd.assistant.api.suggestion.classsuggestion;

import cz.dia.ismd.assistant.model.suggestion.classsuggestion.ClassSuggestion;
import cz.dia.ismd.assistant.model.job.JobStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.UUID;

public record ClassSuggestionsJobResponse(
        @Schema(description = "Identifier of the class suggestion job.")
        UUID jobId,
        @Schema(description = "Current processing status of the class suggestion job.")
        JobStatus status,
        @Schema(description = "All complete class terms generated so far, including partial results of a failed job.")
        List<ClassSuggestion> newSuggestions
) {
}
