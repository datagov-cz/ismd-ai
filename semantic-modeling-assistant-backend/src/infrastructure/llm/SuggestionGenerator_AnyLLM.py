import os
import json
import re
import math
from typing import Any, AsyncGenerator, List, Optional
from uuid import uuid4

from any_llm import acompletion
from dotenv import load_dotenv

from infrastructure.llm.SuggestionGeneratorPort import SuggestionGeneratorPort
from infrastructure.llm.prompt_constructors.PromptConstructorPort import PromptConstructorPort
from services.TokenRateLimiter import DailyTokenRateLimiter
from model.domain.conceptual_model import (
    Class as DomainClass,
    ConceptualModel as DomainConceptualModel,
)
from model.domain.suggestion_model import (
    GlobalAttributeSuggestion,
    GlobalClassSuggestion,
    GlobalRelationshipSuggestion,
    LangString,
    LegalAct,
    LegalStructuralElement,
)
from model.llm.generated_suggestions_model import (
    ClassExtractionResult,
    ExtractedProperty,
    PropertyExtractionResult,
)

load_dotenv()


class SuggestionGenerator_AnyLLM(SuggestionGeneratorPort):
    def __init__(
        self,
        prompt_constructor: PromptConstructorPort,
        model: Optional[str] = None,
        provider: Optional[str] = None,
        language: str = "cs",
        token_rate_limiter: Optional[DailyTokenRateLimiter] = None,
    ):
        self.prompt_constructor = prompt_constructor
        self.model = model or os.getenv("LLM_MODEL", "gpt-4.1")
        self.provider = provider or os.getenv("LLM_PROVIDER", "openai")
        self.language = language
        self.api_key = os.getenv("LLM_API_KEY")
        self.api_base = os.getenv("LLM_API_BASE")
        self.token_rate_limiter = token_rate_limiter

    async def generate_top_k_class_suggestions(
        self,
        legal_act: LegalAct,
        k: int,
        structural_elements: List[LegalStructuralElement],
        context_text: Optional[str] = None,
        known_conceptual_model: Optional[DomainConceptualModel] = None,
        user_id: Optional[str] = None,
    ) -> AsyncGenerator[GlobalClassSuggestion, None]:
        legal_text = self._build_legal_text(structural_elements)
        known_conceptual_model_text = None
        if known_conceptual_model is not None:
            known_conceptual_model_text = self._translate_domain_conceptual_model_classes_to_llm_conceptual_model_classes(
                known_conceptual_model
            )

        messages = self.prompt_constructor.construct_top_k_global_class_suggestion_prompt(
            legal_text,
            k,
            context_text if context_text else None,
            known_conceptual_model_text,
        )

        response = await self._completion(messages, ClassExtractionResult, user_id=user_id)
        class_extraction_result = response.choices[0].message.parsed

        global_class_suggestions: list[GlobalClassSuggestion] = []
        parents: list[GlobalClassSuggestion] = []
        for extracted_class in class_extraction_result.extracted_classes:
            global_class_suggestion = next(
                (
                    suggestion
                    for suggestion in parents
                    if suggestion.name.value == extracted_class.name
                ),
                None,
            )

            if global_class_suggestion is None:
                global_class_suggestion = GlobalClassSuggestion(
                    id=str(uuid4()),
                    name=LangString(
                        **{"@value": extracted_class.name, "@language": self.language}
                    ),
                    isBasedOnLegalAct=legal_act,
                    createdFromLocalUniversalSuggestion=[],
                )

            global_class_suggestion.definition = self._lang_string_or_none(
                extracted_class.definition
            )
            global_class_suggestion.explanation = self._lang_string_or_none(
                extracted_class.explanation
            )
            self._apply_class_kind(global_class_suggestion, extracted_class.kind)

            if extracted_class.parent is not None and extracted_class.parent.strip() != "":
                parent_suggestion = self._find_or_create_parent_suggestion(
                    extracted_class.parent,
                    legal_act,
                    global_class_suggestions,
                    parents,
                    known_conceptual_model,
                )
                global_class_suggestion.specializes = [parent_suggestion]

            for reference in extracted_class.references or []:
                global_class_suggestion.isBasedOnLegalStructuralElement.append(
                    LegalStructuralElement(id=reference, officialIdentifier=reference)
                )

            global_class_suggestions.append(global_class_suggestion)
            print(
                f"generate_top_k_class_suggestions: Identified class suggestion {global_class_suggestion.name.value} "
                f"in the legal act {legal_act.officialNumber}"
            )
            yield global_class_suggestion

        for parent in parents:
            known_parent = known_conceptual_model is not None and any(
                getattr(domain_class.name, "value", None) == parent.name.value
                for domain_class in known_conceptual_model.classes
            )
            if parent not in global_class_suggestions and not known_parent:
                global_class_suggestions.append(parent)
                print(
                    f"generate_top_k_class_suggestions: Identified parent class suggestion {parent.name.value} "
                    f"in the legal act {legal_act.officialNumber} which was not extracted as a primary class suggestion."
                )
                yield parent

        print(
            f"generate_top_k_class_suggestions: Identification of top K class in the legal act "
            f"{legal_act.officialNumber} completed."
        )

    async def generate_top_k_property_suggestions_for_class(
        self,
        legal_act: LegalAct,
        k: int,
        structural_elements: List[LegalStructuralElement],
        selected_class: DomainClass,
        context_text: Optional[str] = None,
        known_conceptual_model: Optional[DomainConceptualModel] = None,
        user_id: Optional[str] = None,
    ) -> AsyncGenerator[
        tuple[str, GlobalAttributeSuggestion | GlobalRelationshipSuggestion], None
    ]:
        legal_text = self._build_legal_text(structural_elements)
        known_conceptual_model_text = None
        if known_conceptual_model is not None:
            known_conceptual_model_text = (
                self._translate_domain_conceptual_model_to_llm_conceptual_model(
                    known_conceptual_model,
                    selected_class,
                )
            )

        messages = self.prompt_constructor.construct_top_k_global_property_suggestion_prompt(
            legal_text,
            k,
            selected_class.name.value,
            context_text,
            known_conceptual_model_text,
        )

        response = await self._completion(
            messages,
            PropertyExtractionResult,
            user_id=user_id,
            temperature=0,
            top_p=1,
            max_tokens=int(os.getenv("LLM_MAX_TOKENS", "2048")),
        )
        property_extraction_result = response.choices[0].message.parsed
        created_class_suggestions: dict[str, GlobalClassSuggestion] = {}

        for extracted_property in property_extraction_result.extracted_properties:
            if extracted_property.kind == "attribute":
                attribute_suggestion = self._to_global_attribute_suggestion(
                    extracted_property,
                    legal_act,
                )
                print(
                    f"generate_top_k_property_suggestions_for_class: Identified attribute suggestion "
                    f"{attribute_suggestion.name.value} for the class {selected_class.name.value} in the legal act "
                    f"{legal_act.officialNumber}"
                )
                yield ("attribute", attribute_suggestion)
            elif extracted_property.kind == "relationship":
                relationship_suggestion = self._to_global_relationship_suggestion(
                    extracted_property,
                    legal_act,
                    known_conceptual_model,
                    created_class_suggestions,
                )
                print(
                    f"generate_top_k_property_suggestions_for_class: Identified relationship suggestion "
                    f"{relationship_suggestion.name.value} for the class {selected_class.name.value} in the legal act "
                    f"{legal_act.officialNumber}"
                )
                yield ("relationship", relationship_suggestion)

        print(
            f"generate_top_k_property_suggestions_for_class: Identification of top K properties for the class "
            f"{selected_class.name.value} in the legal act {legal_act.officialNumber} completed."
        )

    async def _completion(self, messages, response_format, user_id: Optional[str] = None, **kwargs) -> Any:
        params = {
            "model": self.model,
            "provider": self.provider,
            "messages": messages,
            "response_format": response_format,
            **kwargs,
        }
        if self.api_key:
            params["api_key"] = self.api_key
        if self.api_base:
            params["api_base"] = self.api_base

        reservation = None
        if self.token_rate_limiter is not None:
            reservation = self.token_rate_limiter.reserve(
                user_id,
                self._estimate_completion_tokens(messages, kwargs),
            )
        try:
            response = await acompletion(**params)
        except Exception:
            if self.token_rate_limiter is not None:
                self.token_rate_limiter.refund(reservation)
            raise

        if self.token_rate_limiter is not None:
            self.token_rate_limiter.adjust(reservation, self._extract_total_tokens(response))
        return response

    def _estimate_completion_tokens(self, messages, kwargs) -> int:
        prompt_text = json.dumps(messages, ensure_ascii=False, default=str)
        prompt_tokens = max(math.ceil(len(prompt_text) / 4), 1)
        max_output_tokens = (
            kwargs.get("max_tokens")
            or kwargs.get("max_completion_tokens")
            or int(os.getenv("LLM_MAX_TOKENS", "2048"))
        )
        return prompt_tokens + int(max_output_tokens)

    def _extract_total_tokens(self, response) -> Optional[int]:
        usage = self._get_value(response, "usage")
        if usage is None:
            return None

        total_tokens = self._first_int_value(
            usage,
            ["total_tokens", "totalTokens", "totalTokenCount"],
        )
        if total_tokens is not None:
            return total_tokens

        prompt_tokens = self._first_int_value(
            usage,
            ["prompt_tokens", "input_tokens", "promptTokens", "inputTokens"],
        )
        completion_tokens = self._first_int_value(
            usage,
            ["completion_tokens", "output_tokens", "completionTokens", "outputTokens"],
        )
        if prompt_tokens is not None and completion_tokens is not None:
            return prompt_tokens + completion_tokens
        return None

    def _first_int_value(self, source, keys: list[str]) -> Optional[int]:
        for key in keys:
            value = self._get_value(source, key)
            if value is not None:
                return int(value)
        return None

    def _get_value(self, source, key: str):
        if isinstance(source, dict):
            return source.get(key)
        return getattr(source, key, None)

    def _build_legal_text(self, structural_elements: List[LegalStructuralElement]) -> str:
        if len(structural_elements) == 0:
            raise Exception("No structural elements provided and cannot generate legal_text.")

        legal_text = ""
        for structural_element in structural_elements:
            text = re.sub(
                r"(§\s*[0-9]+)",
                r"<REFERENCE>\1</REFERENCE>",
                structural_element.textContent,
            )
            legal_text += text + "\n\n"
        return legal_text

    def _lang_string_or_none(self, value: Optional[str]) -> Optional[LangString]:
        if value is None:
            return None
        return LangString(**{"@value": value, "@language": self.language})

    def _apply_class_kind(self, suggestion: GlobalClassSuggestion, kind: Optional[str]):
        if kind == "subject":
            suggestion.isSubjectOfLaw = True
        elif kind == "object":
            suggestion.isObjectOfLaw = True
        elif kind == "event":
            suggestion.isEvent = True
        elif kind == "document":
            suggestion.isDocument = True

    def _find_or_create_parent_suggestion(
        self,
        parent_name: str,
        legal_act: LegalAct,
        global_class_suggestions: list[GlobalClassSuggestion],
        parents: list[GlobalClassSuggestion],
        known_conceptual_model: Optional[DomainConceptualModel],
    ) -> GlobalClassSuggestion:
        for existing in global_class_suggestions + parents:
            if existing.name.value == parent_name:
                return existing

        if known_conceptual_model is not None:
            for domain_class in known_conceptual_model.classes:
                if getattr(domain_class.name, "value", None) == parent_name:
                    parent = GlobalClassSuggestion(
                        id=domain_class.id,
                        name=LangString(
                            **{"@value": parent_name, "@language": self.language}
                        ),
                        isBasedOnLegalAct=legal_act,
                        createdFromLocalUniversalSuggestion=[],
                    )
                    parents.append(parent)
                    return parent

        parent = GlobalClassSuggestion(
            id=str(uuid4()),
            name=LangString(**{"@value": parent_name, "@language": self.language}),
            isBasedOnLegalAct=legal_act,
            createdFromLocalUniversalSuggestion=[],
        )
        parents.append(parent)
        return parent

    def _to_global_attribute_suggestion(
        self,
        extracted_property: ExtractedProperty,
        legal_act: LegalAct,
    ) -> GlobalAttributeSuggestion:
        suggestion = GlobalAttributeSuggestion(
            id=str(uuid4()),
            name=LangString(
                **{"@value": extracted_property.name, "@language": self.language}
            ),
            isBasedOnLegalAct=legal_act,
            createdFromLocalUniversalSuggestion=[],
        )
        suggestion.definition = self._lang_string_or_none(extracted_property.definition)
        suggestion.explanation = self._lang_string_or_none(extracted_property.explanation)
        self._append_references(suggestion, extracted_property.references)
        return suggestion

    def _to_global_relationship_suggestion(
        self,
        extracted_property: ExtractedProperty,
        legal_act: LegalAct,
        known_conceptual_model: Optional[DomainConceptualModel],
        created_class_suggestions: dict[str, GlobalClassSuggestion],
    ) -> GlobalRelationshipSuggestion:
        suggestion = GlobalRelationshipSuggestion(
            id=str(uuid4()),
            name=LangString(
                **{"@value": extracted_property.name, "@language": self.language}
            ),
            isBasedOnLegalAct=legal_act,
            createdFromLocalUniversalSuggestion=[],
        )
        suggestion.definition = self._lang_string_or_none(extracted_property.definition)
        suggestion.explanation = self._lang_string_or_none(extracted_property.explanation)
        suggestion.mediatesClass = [
            self._class_suggestion_for_name(
                extracted_property.source,
                legal_act,
                known_conceptual_model,
                created_class_suggestions,
            ),
            self._class_suggestion_for_name(
                extracted_property.target,
                legal_act,
                known_conceptual_model,
                created_class_suggestions,
            ),
        ]
        self._append_references(suggestion, extracted_property.references)
        return suggestion

    def _class_suggestion_for_name(
        self,
        class_name: str,
        legal_act: LegalAct,
        known_conceptual_model: Optional[DomainConceptualModel],
        created_class_suggestions: dict[str, GlobalClassSuggestion],
    ) -> GlobalClassSuggestion:
        if known_conceptual_model is not None:
            domain_class = next(
                (
                    item
                    for item in known_conceptual_model.classes
                    if item.name.value == class_name
                ),
                None,
            )
            if domain_class is not None:
                return GlobalClassSuggestion(
                    id=domain_class.id,
                    name=LangString(
                        **{
                            "@value": domain_class.name.value,
                            "@language": self.language,
                        }
                    ),
                    isBasedOnLegalAct=legal_act,
                    createdFromLocalUniversalSuggestion=[],
                )

        if class_name not in created_class_suggestions:
            created_class_suggestions[class_name] = GlobalClassSuggestion(
                id=str(uuid4()),
                name=LangString(**{"@value": class_name, "@language": self.language}),
                isBasedOnLegalAct=legal_act,
                createdFromLocalUniversalSuggestion=[],
            )
        return created_class_suggestions[class_name]

    def _append_references(self, suggestion, references: Optional[list[str]]):
        for reference in references or []:
            suggestion.isBasedOnLegalStructuralElement.append(
                LegalStructuralElement(id=reference, officialIdentifier=reference)
            )
