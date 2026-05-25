import json
from abc import ABC, abstractmethod
from typing import AsyncGenerator, List, Optional
from model.domain.suggestion_model import GlobalClassSuggestion, GlobalAttributeSuggestion, GlobalRelationshipSuggestion, LegalAct, LegalStructuralElement
from model.domain.conceptual_model import (
  ConceptualModel as DomainConceptualModel,
  Class as DomainClass
)
from infrastructure.llm.prompt_constructors import PromptConstructorPort

from model.llm.conceptual_model import (
    ConceptualModel as LLMConceptualModel,
    Class as LLMClass,
    Attribute as LLMAttribute,
    Relationship as LLMRelationship
)

class SuggestionGeneratorPort(ABC):
    def __init__(self, prompt_constructor: PromptConstructorPort, model: str, language: str):
        pass

    @abstractmethod
    async def generate_top_k_class_suggestions(
            self,
            legal_act: LegalAct,
            k: int,
            structural_elements: List[LegalStructuralElement],
            context_text: Optional[str] = None,
            known_conceptual_model: Optional[DomainConceptualModel] = None,
            user_id: Optional[str] = None) -> AsyncGenerator[GlobalClassSuggestion, None]:
        pass

    @abstractmethod
    async def generate_top_k_property_suggestions_for_class(
            self,
            legal_act: LegalAct,
            k: int,
            structural_elements: List[LegalStructuralElement],
            selected_class_id: str,
            context_text: Optional[str] = None,
            known_conceptual_model: Optional[DomainConceptualModel] = None,
            user_id: Optional[str] = None
    ) -> AsyncGenerator[tuple[str, GlobalAttributeSuggestion | GlobalRelationshipSuggestion], None]:
        pass

    def _translate_domain_conceptual_model_to_llm_conceptual_model(self, domain_conceptual_model: DomainConceptualModel, selected_class: Optional[DomainClass] = None) -> str:
        """
        Returns a JSON string with only names of all classes, attributes, and relationships.
        For the selected_class (if specified), also includes its definition and explanation.
        The output is as short as possible: no null/None/empty values are serialized.
        """

        classes = []
        for domain_class in domain_conceptual_model.classes:
            class_obj = {"name": domain_class.name.value}
            if selected_class and domain_class is selected_class:
                if getattr(domain_class, 'definition', None) and getattr(domain_class.definition, 'value', None):
                    if domain_class.definition.value:
                        class_obj["definition"] = domain_class.definition.value
                if getattr(domain_class, 'explanation', None) and getattr(domain_class.explanation, 'value', None):
                    if domain_class.explanation.value:
                        class_obj["explanation"] = domain_class.explanation.value
                if getattr(domain_class, 'ownsAttribute', None):
                    attrs = [
                        {"name": attr.name.value}
                        for attr in domain_class.ownsAttribute
                        if getattr(attr, 'name', None) and getattr(attr.name, 'value', None)
                    ]
                    if attrs:
                        class_obj["ownsAttribute"] = attrs
            classes.append(class_obj)

        relationships = []
        if selected_class is not None:
            relationships = [
                {
                    "name": rel.name.value,
                    "source": rel.mediatesClass[0].name.value if len(getattr(rel, "mediatesClass", [])) > 0 and getattr(rel.mediatesClass[0], "name", None) and getattr(rel.mediatesClass[0].name, "value", None) else None,
                    "target": rel.mediatesClass[1].name.value if len(getattr(rel, "mediatesClass", [])) > 1 and getattr(rel.mediatesClass[1], "name", None) and getattr(rel.mediatesClass[1].name, "value", None) else None,
                }
                for rel in getattr(domain_conceptual_model, 'relationships', [])
                if (
                    getattr(rel, 'name', None) and getattr(rel.name, 'value', None)
                    and selected_class in getattr(rel, 'mediatesClass', [])
                )
            ]

        result = {"classes": classes}
        if relationships:
            result["relationships"] = relationships

        return json.dumps(result, separators=(",", ":"), ensure_ascii=False)
    

    def _translate_domain_conceptual_model_classes_to_llm_conceptual_model_classes(self, domain_conceptual_model: DomainConceptualModel) -> str:
        classes = []
        for domain_class in domain_conceptual_model.classes:
            class_obj = {"name": domain_class.name.value}
            classes.append(class_obj)

        result = {"classes": classes}

        return json.dumps(result, separators=(",", ":"), ensure_ascii=False)
