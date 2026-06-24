package cz.dia.ismd.assistant.dto.propertysuggestion;

import cz.dia.ismd.assistant.domain.JobStatus;

import java.util.UUID;

public record SelectedClassJobStartResponse(UUID jobId, String selectedClassId, JobStatus status) {
}
