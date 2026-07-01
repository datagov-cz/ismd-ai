package cz.dia.ismd.assistant.records.suggestion;

import com.fasterxml.jackson.annotation.JsonProperty;
import cz.dia.ismd.assistant.validation.ValidationPatterns;
import jakarta.validation.constraints.Pattern;

public record KnownRelationshipTerm(
        @JsonProperty("termID") String termID,
        IdReference sourceClass,
        IdReference targetClass,
        LangString name,
        LangString definition,
        LangString explanation,
        @Pattern(regexp = ValidationPatterns.ELI_URI) String legalAct
) {
}
