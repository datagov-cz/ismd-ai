package cz.dia.ismd.assistant.records.suggestion;

import io.swagger.v3.oas.annotations.media.Schema;

public record IdReference(
        @Schema(description = "Identifier of the referenced conceptual model term.")
        String id
) {
}
