package cz.dia.ismd.assistant.service;

import cz.dia.ismd.assistant.model.suggestion.TermType;
import cz.dia.ismd.assistant.model.suggestion.attribute.AttributeSuggestion;
import cz.dia.ismd.assistant.model.suggestion.classsuggestion.ClassSuggestion;
import cz.dia.ismd.assistant.model.suggestion.DocumentContext;
import cz.dia.ismd.assistant.model.suggestion.IdReference;
import cz.dia.ismd.assistant.model.suggestion.LangString;
import cz.dia.ismd.assistant.model.suggestion.LegalStructuralElement;
import cz.dia.ismd.assistant.model.suggestion.LocalSuggestionOccurrence;
import cz.dia.ismd.assistant.model.suggestion.relationship.RelationshipSuggestion;
import cz.dia.ismd.assistant.api.suggestion.classsuggestion.ClassSuggestionJobRequest;
import cz.dia.ismd.assistant.api.suggestion.attribute.PropertySuggestionJobRequest;
import cz.dia.ismd.assistant.api.suggestion.relationship.RelationshipSuggestionJobRequest;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Component
public class SuggestionGenerator {

    private static final String PARAGRAPH_TYPE = "http://example.org/Paragraph";
    private static final String LOCAL_SUGGESTION_TYPE = "http://example.org/LocalClassSuggestion";
    private static final String ATTRIBUTE_TYPE = "http://example.org/Attribute";

    public List<ClassSuggestion> classSuggestions(DocumentContext context, ClassSuggestionJobRequest request) {
        int count = Math.min(request.effectiveK(), 5);
        String legalAct = legalActUri(context);
        List<LegalStructuralElement> references = structuralElements(request.structuralElementIds());
        List<ClassSuggestion> suggestions = new ArrayList<>();
        for (int index = 1; index <= count; index++) {
            String subject = classSubject(context, request.contextText(), index);
            suggestions.add(new ClassSuggestion(
                    "class_%03d".formatted(index),
                    LangString.cs(subject),
                    LangString.cs("A concept identified from the provided document context."),
                    LangString.cs("Generated from the selected legal text and modeling context."),
                    TermType.CLASS,
                    index == 1 ? List.of() : List.of(new IdReference("class_%03d".formatted(index - 1))),
                    legalAct
            ));
        }
        return suggestions;
    }

    public List<AttributeSuggestion> attributeSuggestions(DocumentContext context, PropertySuggestionJobRequest request) {
        int count = Math.min(request.effectiveK(), 5);
        String legalAct = legalActUri(context);
        List<LegalStructuralElement> references = structuralElements(request.structuralElementIds());
        List<AttributeSuggestion> suggestions = new ArrayList<>();
        for (int index = 1; index <= count; index++) {
            String name = switch (index) {
                case 1 -> "name";
                case 2 -> "identifier";
                case 3 -> "effective_date";
                case 4 -> "status";
                default -> "description";
            };
            suggestions.add(new AttributeSuggestion(
                    "attr_%03d".formatted(index),
                    new IdReference(request.selectedClassId()),
                    LangString.cs(name),
                    LangString.cs("A property of class " + request.selectedClassId() + "."),
                    LangString.cs("Suggested from the selected passages and contextual model."),
                    legalAct
            ));
        }
        return suggestions;
    }

    public List<RelationshipSuggestion> relationshipSuggestions(DocumentContext context, RelationshipSuggestionJobRequest request) {
        int count = Math.min(request.effectiveK(), 5);
        String legalAct = legalActUri(context);
        List<LegalStructuralElement> references = structuralElements(request.structuralElementIds());
        List<RelationshipSuggestion> suggestions = new ArrayList<>();
        for (int index = 1; index <= count; index++) {
            String targetClassId = "related_class_%03d".formatted(index);
            suggestions.add(new RelationshipSuggestion(
                    "rel_%03d".formatted(index),
                    new IdReference(request.selectedClassId()),
                    new IdReference(targetClassId),
                    LangString.cs(relationshipName(index)),
                    LangString.cs("A relationship involving class " + request.selectedClassId() + "."),
                    LangString.cs("Suggested from co-occurring obligations, rights, or references in the text."),
                    legalAct
            ));
        }
        return suggestions;
    }

    private String legalActUri(DocumentContext context) {
        if (context.year().isPresent() && context.number().isPresent()) {
            int year = context.year().orElseThrow();
            int number = context.number().orElseThrow();
            return "/eli/cz/sb/%d/%d".formatted(year, number);
        }
        return "/eli/non-legal/text";
    }

    private List<LegalStructuralElement> structuralElements(List<String> ids) {
        List<String> source = Optional.ofNullable(ids).filter(items -> !items.isEmpty()).orElse(List.of("/eli/non-legal/text/section/1"));
        List<LegalStructuralElement> elements = new ArrayList<>();
        for (int index = 0; index < source.size(); index++) {
            String identifier = source.get(index);
            elements.add(new LegalStructuralElement(
                    "paragraph_%03d".formatted(index + 1),
                    PARAGRAPH_TYPE,
                    identifier
            ));
        }
        return elements;
    }

    private List<LocalSuggestionOccurrence> occurrences(int suggestionIndex, List<LegalStructuralElement> references) {
        LegalStructuralElement part = references.isEmpty()
                ? new LegalStructuralElement("paragraph_001", PARAGRAPH_TYPE, "/eli/non-legal/text/section/1")
                : references.get(Math.min(suggestionIndex - 1, references.size() - 1));
        return List.of(new LocalSuggestionOccurrence(
                "occurrence_%03d".formatted(suggestionIndex),
                LOCAL_SUGGESTION_TYPE,
                part
        ));
    }

    private String classSubject(DocumentContext context, String contextText, int index) {
        String focus = Optional.ofNullable(contextText)
                .map(String::trim)
                .filter(text -> !text.isBlank())
                .map(text -> text.split("\\s+")[0])
                .orElseGet(() -> context.text().map(text -> text.split("\\s+")[0]).orElse("Legal"));
        return switch (index) {
            case 1 -> focusTitle(focus) + " Actor";
            case 2 -> focusTitle(focus) + " Object";
            case 3 -> focusTitle(focus) + " Document";
            case 4 -> focusTitle(focus) + " Event";
            default -> focusTitle(focus) + " Obligation";
        };
    }

    private String focusTitle(String value) {
        String cleaned = value.replaceAll("[^\\p{Alnum}_-]", "");
        if (cleaned.isBlank()) {
            return "Legal";
        }
        return cleaned.substring(0, 1).toUpperCase(Locale.ROOT) + cleaned.substring(1).toLowerCase(Locale.ROOT);
    }

    private String relationshipName(int index) {
        return switch (index) {
            case 1 -> "relates_to";
            case 2 -> "owns";
            case 3 -> "requires";
            case 4 -> "documents";
            default -> "authorizes";
        };
    }
}
