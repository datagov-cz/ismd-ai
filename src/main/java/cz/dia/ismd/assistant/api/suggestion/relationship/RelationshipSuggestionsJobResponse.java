package cz.dia.ismd.assistant.api.suggestion.relationship;

import cz.dia.ismd.assistant.model.job.JobStatus;
import cz.dia.ismd.assistant.model.suggestion.relationship.RelationshipSuggestion;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.UUID;

public record RelationshipSuggestionsJobResponse(
        @Schema(description = "Identifier of the relationship suggestion job.")
        UUID jobId,
        @Schema(description = "Identifier of the source class for which relationships were suggested.")
        String selectedClassId,
        @Schema(description = "Current processing status of the relationship suggestion job.")
        JobStatus status,
        @Schema(description = "All relationship terms generated for the selected source class so far.")
        List<RelationshipSuggestion> newRelationshipSuggestions
) {
}
