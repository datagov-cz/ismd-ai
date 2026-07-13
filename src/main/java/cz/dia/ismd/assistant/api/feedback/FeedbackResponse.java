package cz.dia.ismd.assistant.api.feedback;

import io.swagger.v3.oas.annotations.media.Schema;

public record FeedbackResponse(
        @Schema(description = "Outcome of the feedback request.")
        String status,
        @Schema(description = "Feedback action that was recorded for the suggestions.")
        String type
) {
}
