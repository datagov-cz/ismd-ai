package cz.dia.ismd.assistant.api;

import io.swagger.v3.oas.annotations.media.Schema;

public record ErrorResponse(
        @Schema(description = "Human-readable description of the API error.")
        String detail
) {
}
