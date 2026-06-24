package cz.cvut.ismd.assistant.service;

import cz.cvut.ismd.assistant.domain.AttributeSuggestion;
import cz.cvut.ismd.assistant.domain.ClassSuggestion;
import cz.cvut.ismd.assistant.domain.DocumentContext;
import cz.cvut.ismd.assistant.domain.IdReference;
import cz.cvut.ismd.assistant.domain.LangString;
import cz.cvut.ismd.assistant.domain.LegalAct;
import cz.cvut.ismd.assistant.domain.LegalStructuralElement;
import cz.cvut.ismd.assistant.domain.LocalSuggestionOccurrence;
import cz.cvut.ismd.assistant.domain.RelationshipSuggestion;
import cz.cvut.ismd.assistant.controller.dto.ClassSuggestionJobRequest;
import cz.cvut.ismd.assistant.controller.dto.PropertySuggestionJobRequest;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Component
public class SuggestionGenerator {

    private static final String LEGAL_ACT_TYPE = "http://example.org/LegalAct";
    private static final String PARAGRAPH_TYPE = "http://example.org/Paragraph";
    private static final String LOCAL_SUGGESTION_TYPE = "http://example.org/LocalClassSuggestion";
    private static final String CLASS_TYPE = "http://example.org/Class";
    private static final String ATTRIBUTE_TYPE = "http://example.org/Attribute";
    private static final String RELATIONSHIP_TYPE = "http://example.org/Relationship";

    public List<ClassSuggestion> classSuggestions(DocumentContext context, ClassSuggestionJobRequest request) {
        int count = Math.min(request.effectiveK(), 5);
        LegalAct legalAct = legalAct(context);
        List<LegalStructuralElement> references = structuralElements(request.structuralElementIds());
        List<ClassSuggestion> suggestions = new ArrayList<>();
        for (int index = 1; index <= count; index++) {
            String subject = classSubject(context, request.contextText(), index);
            suggestions.add(new ClassSuggestion(
                    "class_%03d".formatted(index),
                    CLASS_TYPE,
                    LangString.en(subject),
                    LangString.en("A concept identified from the provided document context."),
                    LangString.en("Generated from the selected legal text and modeling context."),
                    index % 2 == 1,
                    index % 2 == 0,
                    index == 1 ? List.of() : List.of(new IdReference("class_%03d".formatted(index - 1))),
                    legalAct,
                    occurrences(index, references),
                    references
            ));
        }
        return suggestions;
    }

    public List<AttributeSuggestion> attributeSuggestions(DocumentContext context, PropertySuggestionJobRequest request) {
        int count = Math.min(request.effectiveK(), 5);
        LegalAct legalAct = legalAct(context);
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
                    ATTRIBUTE_TYPE,
                    LangString.en(name),
                    LangString.en("A property of class " + request.selectedClassId() + "."),
                    LangString.en("Suggested from the selected passages and contextual model."),
                    legalAct,
                    occurrences(index, references),
                    references
            ));
        }
        return suggestions;
    }

    public List<RelationshipSuggestion> relationshipSuggestions(DocumentContext context, PropertySuggestionJobRequest request) {
        int count = Math.min(request.effectiveK(), 5);
        LegalAct legalAct = legalAct(context);
        List<LegalStructuralElement> references = structuralElements(request.structuralElementIds());
        List<RelationshipSuggestion> suggestions = new ArrayList<>();
        for (int index = 1; index <= count; index++) {
            String targetClassId = "related_class_%03d".formatted(index);
            suggestions.add(new RelationshipSuggestion(
                    "rel_%03d".formatted(index),
                    RELATIONSHIP_TYPE,
                    LangString.en(relationshipName(index)),
                    LangString.en("A relationship involving class " + request.selectedClassId() + "."),
                    LangString.en("Suggested from co-occurring obligations, rights, or references in the text."),
                    true,
                    List.of(new IdReference(request.selectedClassId()), new IdReference(targetClassId)),
                    legalAct,
                    occurrences(index, references),
                    references
            ));
        }
        return suggestions;
    }

    private LegalAct legalAct(DocumentContext context) {
        if (context.year().isPresent() && context.number().isPresent()) {
            int year = context.year().orElseThrow();
            int number = context.number().orElseThrow();
            return new LegalAct("act_%d_%d".formatted(year, number), LEGAL_ACT_TYPE, "%d/%d".formatted(number, year));
        }
        return new LegalAct("nonlegal_text", "http://example.org/Document", "non-legal-text");
    }

    private List<LegalStructuralElement> structuralElements(List<String> ids) {
        List<String> source = Optional.ofNullable(ids).filter(items -> !items.isEmpty()).orElse(List.of("§1"));
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
                ? new LegalStructuralElement("paragraph_001", PARAGRAPH_TYPE, "§1")
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
