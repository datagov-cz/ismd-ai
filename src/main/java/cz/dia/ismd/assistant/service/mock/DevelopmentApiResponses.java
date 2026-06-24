package cz.dia.ismd.assistant.service.mock;

import cz.dia.ismd.assistant.domain.JobStatus;
import cz.dia.ismd.assistant.domain.TermType;
import cz.dia.ismd.assistant.dto.classsuggestion.ClassSuggestionsJobResponse;
import cz.dia.ismd.assistant.dto.classsuggestion.JobStartResponse;
import cz.dia.ismd.assistant.dto.propertysuggestion.PropertySuggestionsJobResponse;
import cz.dia.ismd.assistant.dto.propertysuggestion.SelectedClassJobStartResponse;
import cz.dia.ismd.assistant.dto.relationshipsuggestion.RelationshipSuggestionsJobResponse;
import cz.dia.ismd.assistant.records.classsuggestion.ClassSuggestion;
import cz.dia.ismd.assistant.records.propertysuggestion.AttributeSuggestion;
import cz.dia.ismd.assistant.records.relationshipsuggestion.RelationshipSuggestion;
import cz.dia.ismd.assistant.records.suggestion.IdReference;
import cz.dia.ismd.assistant.records.suggestion.LangString;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class DevelopmentApiResponses {

    private static final UUID CLASS_JOB_ID = UUID.fromString("00000000-0000-0000-0000-000000000101");
    private static final UUID PROPERTY_JOB_ID = UUID.fromString("00000000-0000-0000-0000-000000000201");
    private static final UUID RELATIONSHIP_JOB_ID = UUID.fromString("00000000-0000-0000-0000-000000000301");
    private static final String CLASS_ID = "mock-class-001";
    private static final String LEGAL_ACT = "/eli/cz/sb/2024/1";

    public JobStartResponse startClassSuggestions() {
        return new JobStartResponse(CLASS_JOB_ID, JobStatus.IN_PROGRESS);
    }

    public List<ClassSuggestionsJobResponse> classSuggestions() {
        return List.of(new ClassSuggestionsJobResponse(
                CLASS_JOB_ID,
                JobStatus.COMPLETED,
                List.of(
                        new ClassSuggestion(
                                "mock-class-suggestion-001",
                                LangString.en("Development Class"),
                                LangString.en("A deterministic class suggestion returned in development."),
                                LangString.en("This response is independent of the request body."),
                                TermType.CLASS,
                                List.of(),
                                LEGAL_ACT
                        ),
                        new ClassSuggestion(
                                "mock-class-suggestion-002",
                                LangString.en("Development Specialization"),
                                LangString.en("A deterministic specialized class suggestion returned in development."),
                                LangString.en("This response is independent of the request body."),
                                TermType.CLASS,
                                List.of(new IdReference("mock-class-suggestion-001")),
                                LEGAL_ACT
                        )
                )
        ));
    }

    public SelectedClassJobStartResponse startPropertySuggestions() {
        return new SelectedClassJobStartResponse(PROPERTY_JOB_ID, CLASS_ID, JobStatus.IN_PROGRESS);
    }

    public List<PropertySuggestionsJobResponse> propertySuggestions() {
        return List.of(new PropertySuggestionsJobResponse(
                PROPERTY_JOB_ID,
                CLASS_ID,
                JobStatus.COMPLETED,
                List.of(new AttributeSuggestion(
                        "mock-attribute-suggestion-001",
                        new IdReference(CLASS_ID),
                        LangString.en("development_attribute"),
                        LangString.en("A deterministic attribute suggestion returned in development."),
                        LangString.en("This response is independent of the request body."),
                        LEGAL_ACT
                ))
        ));
    }

    public SelectedClassJobStartResponse startRelationshipSuggestions() {
        return new SelectedClassJobStartResponse(RELATIONSHIP_JOB_ID, CLASS_ID, JobStatus.IN_PROGRESS);
    }

    public List<RelationshipSuggestionsJobResponse> relationshipSuggestions() {
        return List.of(new RelationshipSuggestionsJobResponse(
                RELATIONSHIP_JOB_ID,
                CLASS_ID,
                JobStatus.COMPLETED,
                List.of(new RelationshipSuggestion(
                        "mock-relationship-suggestion-001",
                        new IdReference(CLASS_ID),
                        new IdReference("mock-target-class-001"),
                        LangString.en("development_relationship"),
                        LangString.en("A deterministic relationship suggestion returned in development."),
                        LangString.en("This response is independent of the request body."),
                        LEGAL_ACT
                ))
        ));
    }

    public Map<String, String> acceptFeedback() {
        return Map.of("status", "accepted", "type", "accepted");
    }

    public Map<String, String> likeFeedback() {
        return Map.of("status", "accepted", "type", "liked");
    }

    public Map<String, String> dislikeFeedback() {
        return Map.of("status", "accepted", "type", "disliked");
    }
}
