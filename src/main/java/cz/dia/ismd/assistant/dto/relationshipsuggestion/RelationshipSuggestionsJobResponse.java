package cz.dia.ismd.assistant.dto.relationshipsuggestion;

import cz.dia.ismd.assistant.domain.JobStatus;
import cz.dia.ismd.assistant.records.relationshipsuggestion.RelationshipSuggestion;

import java.util.List;
import java.util.UUID;

public record RelationshipSuggestionsJobResponse(
        UUID jobId,
        JobStatus status,
        List<RelationshipSuggestion> newRelationshipSuggestions
) {
}
