package cz.dia.ismd.assistant.api.suggestion.vocabulary;

import cz.dia.ismd.assistant.model.job.JobStatus;
import cz.dia.ismd.assistant.model.suggestion.vocabulary.VocabularyDraft;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

public record VocabularySuggestionsJobResponse(
        UUID jobId,
        JobStatus status,
        @Schema(description = "Validated proposal so far. A failed job retains completed batches; phase identifies the last attempted stage.")
        VocabularyDraft draft
) {}
