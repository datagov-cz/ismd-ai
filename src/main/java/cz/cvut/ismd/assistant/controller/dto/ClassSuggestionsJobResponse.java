package cz.cvut.ismd.assistant.controller.dto;

import cz.cvut.ismd.assistant.domain.ClassSuggestion;
import cz.cvut.ismd.assistant.domain.JobStatus;

import java.util.List;
import java.util.UUID;

public record ClassSuggestionsJobResponse(
        UUID jobId,
        JobStatus status,
        List<ClassSuggestion> newSuggestions
) {
}
