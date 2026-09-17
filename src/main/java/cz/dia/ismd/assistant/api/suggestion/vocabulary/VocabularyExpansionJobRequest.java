package cz.dia.ismd.assistant.api.suggestion.vocabulary;

import com.fasterxml.jackson.annotation.JsonProperty;
import cz.dia.ismd.assistant.model.suggestion.KnownConceptualModel;
import cz.dia.ismd.assistant.model.suggestion.ValidationPatterns;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.time.LocalDate;
import java.util.List;

/** Caller sends its current, potentially edited working model. The result is a delta. */
public record VocabularyExpansionJobRequest(
        @NotNull Kind kind,
        @Min(1) @Max(10) Integer count,
        String selectedClassId,
        @Size(max = 100) List<@NotBlank @Pattern(regexp = ValidationPatterns.ELI_URI) String> structuralElementIds,
        @Size(max = 10000) String contextText,
        @NotNull @Valid KnownConceptualModel knownConceptualModel
) {
    public enum Kind {
        @JsonProperty("classes") CLASSES,
        @JsonProperty("properties") PROPERTIES,
        @JsonProperty("relationships") RELATIONSHIPS
    }
    public int effectiveCount() { return count == null ? 3 : count; }
    public VocabularyExpansionJobRequest forLegalAct(int year, int number, LocalDate date) {
        return new VocabularyExpansionJobRequest(kind, count, selectedClassId, LegalActSourceResolver.resolve(year, number, date, structuralElementIds),
                contextText, knownConceptualModel);
    }
}
