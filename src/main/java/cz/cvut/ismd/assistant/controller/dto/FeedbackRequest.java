package cz.cvut.ismd.assistant.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record FeedbackRequest(
        @NotNull UUID jobId,
        @NotBlank String suggestionId
) {
}
