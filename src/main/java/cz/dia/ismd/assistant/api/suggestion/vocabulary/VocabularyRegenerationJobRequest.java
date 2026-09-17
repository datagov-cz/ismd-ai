package cz.dia.ismd.assistant.api.suggestion.vocabulary;

import cz.dia.ismd.assistant.model.suggestion.KnownConceptualModel;
import cz.dia.ismd.assistant.model.suggestion.ValidationPatterns;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.time.LocalDate;
import java.util.List;

/** Regenerates metadata of one unsaved term, keeping its ref, kind and graph edges. */
public record VocabularyRegenerationJobRequest(
        @NotBlank String conceptRef,
        @Size(max = 100) List<@NotBlank @Pattern(regexp = ValidationPatterns.ELI_URI) String> structuralElementIds,
        @Size(max = 10000) String contextText,
        @NotNull @Valid KnownConceptualModel knownConceptualModel
) {
    public VocabularyRegenerationJobRequest forLegalAct(int year, int number, LocalDate date) {
        return new VocabularyRegenerationJobRequest(conceptRef, LegalActSourceResolver.resolve(year, number, date, structuralElementIds), contextText,
                knownConceptualModel);
    }
}
