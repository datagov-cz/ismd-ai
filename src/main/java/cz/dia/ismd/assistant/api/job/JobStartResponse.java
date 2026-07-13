package cz.dia.ismd.assistant.api.job;

import cz.dia.ismd.assistant.model.job.JobStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

public record JobStartResponse(
        @Schema(description = "Identifier of the newly started suggestion job.")
        UUID jobId,
        @Schema(description = "Initial processing status of the suggestion job.")
        JobStatus status
) {
}
