package cz.cvut.ismd.assistant.controller.dto;

import cz.cvut.ismd.assistant.domain.JobStatus;
import cz.cvut.ismd.assistant.domain.RelationshipSuggestion;

import java.util.List;
import java.util.UUID;

public record RelationshipSuggestionsJobResponse(
        UUID jobId,
        String selectedClassId,
        JobStatus status,
        List<RelationshipSuggestion> newRelationshipSuggestions
) {
}
