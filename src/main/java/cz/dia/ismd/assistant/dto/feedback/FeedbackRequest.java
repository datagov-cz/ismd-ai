package cz.dia.ismd.assistant.dto.feedback;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record FeedbackRequest(
        @NotNull UUID jobId,
        @NotBlank UUID suggestionId
) {
}
