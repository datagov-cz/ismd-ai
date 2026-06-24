package cz.cvut.ismd.assistant.controller.dto;

import cz.cvut.ismd.assistant.domain.JobStatus;

import java.util.UUID;

public record JobStartResponse(UUID jobId, JobStatus status) {
}
