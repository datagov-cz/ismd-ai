package cz.dia.ismd.assistant.dto.classsuggestion;

import cz.dia.ismd.assistant.domain.JobStatus;

import java.util.UUID;

public record JobStartResponse(UUID jobId, JobStatus status) {
}
