package cz.dia.ismd.assistant.dto.propertysuggestion;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record PropertySuggestionJobRequest(
        @Min(1) @Max(50) Integer k,
        @NotBlank String selectedClassId,
        List<String> structuralElementIds,
        String contextText,
        JsonNode knownConceptualModel
) {
    public int effectiveK() {
        return k == null ? 5 : k;
    }
}
