from model.api.class_suggestion import (
    ClassSuggestion,
    ClassSuggestionReference,
    LocalClassSuggestion,
    LegalAct,
    LegalStructuralElement
)
from model.api.attribute_suggestion import (
    AttributeSuggestion,
    LegalAct
)
from model.api.relationship_suggestion import (
    RelationshipSuggestion,
    MediatedClassSuggestion as ApiClassSuggestion,
    LegalAct
)
from model.domain.suggestion_model import (
  GlobalClassSuggestion,
  GlobalAttributeSuggestion,
  GlobalRelationshipSuggestion
)
from typing import Any, Optional, cast

from model.api.conceptual_model import (
    ConceptualModel as ApiConceptualModel,
    Class as ApiClass,
    Relationship as ApiRelationship,
    Attribute as ApiAttribute
)
from model.domain.conceptual_model import (
    ConceptualModel as DomainConceptualModel,
    Class as DomainClass,
    Relationship as DomainRelationship,
    Attribute as DomainAttribute
)
from model.api.lang_model import LangString as ApiLangString
from model.domain.lang_model import LangString as DomainLangString

def _translate_api_langstring_to_domain_langstring(api_langstring: Optional[ApiLangString]) -> Optional[DomainLangString]:
    if api_langstring is None:
        return None
    # Handle dict input (from parsed JSON)
    if isinstance(api_langstring, dict):
        value = api_langstring.get("@value") or api_langstring.get("value")
        language = api_langstring.get("@language") or api_langstring.get("language")
        if not isinstance(value, str) or not isinstance(language, str):
            raise ValueError("Invalid language string payload.")
        return DomainLangString(**{"@value": value, "@language": language})
    # Handle pydantic model input
    return DomainLangString(**{"@value": api_langstring.value, "@language": api_langstring.language})

def _translate_api_conceptual_model_to_domain_conceptual_model(api_conceptual_model: Optional[ApiConceptualModel]) -> DomainConceptualModel:
    # Create a mapping of class IDs to DomainClass instances for reuse
    if not api_conceptual_model:
        return DomainConceptualModel(classes=[], relationships=[])
    class_id_to_domain_class = {
        cls.id: DomainClass(
            id=cls.id,
            name=cast(DomainLangString, _translate_api_langstring_to_domain_langstring(cls.name)),
            definition=_translate_api_langstring_to_domain_langstring(cls.definition) if cls.definition else None,
            explanation=_translate_api_langstring_to_domain_langstring(cls.explanation) if cls.explanation else None,
            isSubjectOfLaw=cls.isSubjectOfLaw,
            isObjectOfLaw=cls.isObjectOfLaw,
            isEvent=cls.isEvent,
            isDocument=cls.isDocument,
            ownsAttribute=[
                DomainAttribute(
                    id=attr.id,
                    name=cast(DomainLangString, _translate_api_langstring_to_domain_langstring(attr.name)),
                    definition=_translate_api_langstring_to_domain_langstring(attr.definition) if attr.definition else None,
                    explanation=_translate_api_langstring_to_domain_langstring(attr.explanation) if attr.explanation else None,
                ) for attr in cls.ownsAttribute or []
            ] if cls.ownsAttribute else None
        ) for cls in api_conceptual_model.classes or []
    }

    return DomainConceptualModel(
        classes=list(class_id_to_domain_class.values()),
        relationships=[
            DomainRelationship(
                id=rel.id,
                name=cast(DomainLangString, _translate_api_langstring_to_domain_langstring(rel.name)),
                definition=_translate_api_langstring_to_domain_langstring(rel.definition) if rel.definition else None,
                mediatesClass=[
                    class_id_to_domain_class[mediated_cls.id]
                    for mediated_cls in rel.mediatesClass or []
                    if mediated_cls.id in class_id_to_domain_class
                ] if rel.mediatesClass else None
            ) for rel in api_conceptual_model.relationships or []
        ]
    )

def _translate_domain_to_api_class_suggestion(domain_suggestion: GlobalClassSuggestion) -> ClassSuggestion:

    occurences = [
        LocalClassSuggestion(
            id=local_universal_suggestion.id,
            type="Local Class Suggestion",
            legal_act_part=LegalStructuralElement(
                id=local_universal_suggestion.isBasedOnLegalStructuralElement.id,
                type="Legal Structural Element",
                official_identifier=local_universal_suggestion.isBasedOnLegalStructuralElement.officialIdentifier
            )
        ) for local_universal_suggestion in getattr(domain_suggestion, "createdFromLocalUniversalSuggestion", []) or []
    ]
    return ClassSuggestion(
        id=domain_suggestion.id,
        type="Class Suggestion",
        name=cast(ApiLangString, to_dict_or_none(domain_suggestion.name)),
        definition=to_dict_or_none(getattr(domain_suggestion, "definition", None)),
        explanation=to_dict_or_none(getattr(domain_suggestion, "explanation", None)),
        is_subject_of_law=domain_suggestion.isSubjectOfLaw,
        is_object_of_law=domain_suggestion.isObjectOfLaw,
        is_event=domain_suggestion.isEvent,
        is_document=domain_suggestion.isDocument,
        specializes=[
            ClassSuggestionReference(id=specialization.id) for specialization in (domain_suggestion.specializes or [])
        ] if domain_suggestion.specializes else None,
        legal_act=LegalAct(
            id=domain_suggestion.isBasedOnLegalAct.id,
            type="Legal Act",
            official_number=domain_suggestion.isBasedOnLegalAct.officialNumber
        ),
        occurrences=occurences if occurences else None
        ,
        references=[
            LegalStructuralElement(
                id=reference.id,
                type="Legal Structural Element",
                official_identifier=reference.officialIdentifier
            ) for reference in (domain_suggestion.isBasedOnLegalStructuralElement or [])
        ] if domain_suggestion.isBasedOnLegalStructuralElement else None
    )

def _translate_domain_to_api_attribute_suggestion(domain_suggestion: GlobalAttributeSuggestion) -> AttributeSuggestion:

  return AttributeSuggestion(
        id=domain_suggestion.id,
        type="Attribute Suggestion",
        name=cast(ApiLangString, to_dict_or_none(domain_suggestion.name)),
        definition=to_dict_or_none(getattr(domain_suggestion, "definition", None)),
        explanation=to_dict_or_none(getattr(domain_suggestion, "explanation", None)),
        legal_act=LegalAct(
            id=domain_suggestion.isBasedOnLegalAct.id,
            type="Legal Act",
            official_number=domain_suggestion.isBasedOnLegalAct.officialNumber
        ),
        references=[
            LegalStructuralElement(
                id=reference.id,
                type="Legal Structural Element",
                official_identifier=reference.officialIdentifier
            ) for reference in (domain_suggestion.isBasedOnLegalStructuralElement or [])
        ] if domain_suggestion.isBasedOnLegalStructuralElement else None
  )

def _translate_domain_to_api_relationship_suggestion(domain_suggestion: GlobalRelationshipSuggestion) -> RelationshipSuggestion:
    # Convert domain ClassSuggestion list to API ClassSuggestion list
    api_mediated_classes = []
    if domain_suggestion.mediatesClass:
        for domain_class in domain_suggestion.mediatesClass:
            # Ensure domain_class is a valid ClassSuggestion instance before accessing attributes
            if isinstance(domain_class, GlobalClassSuggestion) and hasattr(domain_class, 'id') and hasattr(domain_class, 'name'):
                api_mediated_classes.append(
                    ApiClassSuggestion(
                        id=domain_class.id,
                        type="Class Suggestion", # Assuming a fixed type string for the API model
                        name=cast(ApiLangString, to_dict_or_none(domain_class.name)) # Reuse the conversion helper
                    )
                )
            # Optional: Add logging here if a mediated class is skipped due to type/attribute issues

    return RelationshipSuggestion(
        id=domain_suggestion.id,
        type="Relationship Suggestion",
        name=cast(ApiLangString, to_dict_or_none(domain_suggestion.name)),
        definition=to_dict_or_none(getattr(domain_suggestion, "definition", None)),
        explanation=to_dict_or_none(getattr(domain_suggestion, "explanation", None)),
        legal_act=LegalAct(
            id=domain_suggestion.isBasedOnLegalAct.id,
            type="Legal Act",
            official_number=domain_suggestion.isBasedOnLegalAct.officialNumber
        ),
        mediatesClass=api_mediated_classes if api_mediated_classes else None,
        references=[
            LegalStructuralElement(
                id=reference.id,
                type="Legal Structural Element",
                official_identifier=reference.officialIdentifier
            ) for reference in (domain_suggestion.isBasedOnLegalStructuralElement or [])
        ] if domain_suggestion.isBasedOnLegalStructuralElement else None
    )

def to_dict_or_none(lang_string: Any) -> Optional[ApiLangString]:
        if lang_string is None:
            return None
        value = None
        language = None
        if isinstance(lang_string, dict):
            if "@value" in lang_string and "@language" in lang_string:
                value = lang_string["@value"]
                language = lang_string["@language"]
            else:
                value = lang_string.get("value")
                language = lang_string.get("language")
        elif hasattr(lang_string, "model_dump"):
            d = lang_string.model_dump()
            value = d.get("@value") or d.get("value")
            language = d.get("@language") or d.get("language")
        elif hasattr(lang_string, "__dict__"):
            d = lang_string.__dict__
            value = d.get("@value") or d.get("value")
            language = d.get("@language") or d.get("language")
        if isinstance(value, str) and isinstance(language, str):
            return ApiLangString(**{"@value": value, "@language": language})
        return None
