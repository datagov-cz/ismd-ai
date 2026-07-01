package cz.dia.ismd.assistant.records.suggestion;

import com.fasterxml.jackson.annotation.JsonProperty;
import cz.dia.ismd.assistant.domain.TermType;
import cz.dia.ismd.assistant.validation.ValidationPatterns;
import jakarta.validation.constraints.Pattern;

import java.util.List;

public record KnownClassTerm(
        @JsonProperty("termID") String termID,
        LangString name,
        LangString definition,
        LangString explanation,
        TermType type,
        List<IdReference> specializes,
        @Pattern(regexp = ValidationPatterns.ELI_URI) String legalAct
) {
}
