package cz.dia.ismd.assistant.dto.relationshipsuggestion;

import cz.dia.ismd.assistant.domain.JobStatus;
import cz.dia.ismd.assistant.records.relationshipsuggestion.RelationshipSuggestion;
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
        @Schema(description = "New relationship terms suggested for the selected source class.")
        List<RelationshipSuggestion> newRelationshipSuggestions
) {
}
