package cz.dia.ismd.assistant.dto.classsuggestion;

import cz.dia.ismd.assistant.records.suggestion.KnownConceptualModel;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.util.List;

public record ClassSuggestionJobRequest(
        @Min(1) @Max(50) Integer k,
        List<String> structuralElementIds,
        String contextText,
        KnownConceptualModel knownConceptualModel
) {
    public int effectiveK() {
        return k == null ? 5 : k;
    }
}
