package cz.dia.ismd.assistant.model.suggestion.vocabulary;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Exactly one of ref (an unsaved concept) or iri (an existing concept).")
public record ConceptReference(String ref, String iri) {
    public ConceptReference {
        if ((ref == null) == (iri == null)
                || (ref != null && ref.isBlank()) || (iri != null && iri.isBlank())) {
            throw new IllegalArgumentException("A concept reference must have exactly one non-blank ref or iri");
        }
    }
}
