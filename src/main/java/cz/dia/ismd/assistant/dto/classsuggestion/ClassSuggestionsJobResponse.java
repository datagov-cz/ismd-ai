package cz.dia.ismd.assistant.dto.classsuggestion;

import cz.dia.ismd.assistant.records.classsuggestion.ClassSuggestion;
import cz.dia.ismd.assistant.domain.JobStatus;

import java.util.List;
import java.util.UUID;

public record ClassSuggestionsJobResponse(
        UUID jobId,
        JobStatus status,
        List<ClassSuggestion> newSuggestions
) {
}
