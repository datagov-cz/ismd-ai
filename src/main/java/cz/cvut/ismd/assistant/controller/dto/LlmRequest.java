package cz.cvut.ismd.assistant.controller.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record LlmRequest(
        String systemPrompt,
        @NotBlank String prompt,
        @Min(1) @Max(8192) Integer maxTokens,
        @Min(0) @Max(2) Double temperature
) {
}
