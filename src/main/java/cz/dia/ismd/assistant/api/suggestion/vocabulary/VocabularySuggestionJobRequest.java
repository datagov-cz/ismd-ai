package cz.dia.ismd.assistant.api.suggestion.vocabulary;

import cz.dia.ismd.assistant.model.suggestion.KnownConceptualModel;
import cz.dia.ismd.assistant.model.suggestion.ValidationPatterns;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.time.LocalDate;
import java.util.List;

public record VocabularySuggestionJobRequest(
        @Schema(description = "Maximum classes, default 5.") @Min(1) @Max(10) Integer classCount,
        @Schema(description = "Maximum properties per new class, default 3; 0 disables this stage.")
        @Min(0) @Max(10) Integer propertiesPerClass,
        @Schema(description = "Maximum relationships per new class, default 3; 0 disables this stage.")
        @Min(0) @Max(10) Integer relationshipsPerClass,
        @Schema(description = "Optional ELI elements within the selected legal act version. Omit to use the entire act.")
        @Size(max = 100) List<@NotBlank @Pattern(regexp = ValidationPatterns.ELI_URI) String> structuralElementIds,
        @Size(max = 10000) String contextText,
        @Schema(description = "Optional context. termID values must be unique: absolute IRIs for existing terms, or opaque refs without a URI scheme for unsaved terms.")
        @Valid KnownConceptualModel knownConceptualModel
) {
    public int effectiveClassCount() { return classCount == null ? 5 : classCount; }
    public int effectivePropertiesPerClass() { return propertiesPerClass == null ? 3 : propertiesPerClass; }
    public int effectiveRelationshipsPerClass() { return relationshipsPerClass == null ? 3 : relationshipsPerClass; }

    public VocabularySuggestionJobRequest forLegalAct(int year, int number, LocalDate date) {
        return new VocabularySuggestionJobRequest(classCount, propertiesPerClass, relationshipsPerClass,
                LegalActSourceResolver.resolve(year, number, date, structuralElementIds), contextText, knownConceptualModel);
    }
}
