package cz.cvut.ismd.assistant.controller.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.util.List;

public record ClassSuggestionJobRequest(
        @Min(1) @Max(50) Integer k,
        List<String> structuralElementIds,
        String contextText,
        JsonNode knownConceptualModel
) {
    public int effectiveK() {
        return k == null ? 10 : k;
    }
}
