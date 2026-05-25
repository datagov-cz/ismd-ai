import os
import json
from typing import AsyncGenerator, List, Optional
from uuid import uuid4
from openai import AsyncOpenAI
from dotenv import load_dotenv

#inputs and outputs
from model.domain.conceptual_model import (
  ConceptualModel as DomainConceptualModel,
  Class as DomainClass
)
from model.domain.suggestion_model import GlobalClassSuggestion, LocalClassSuggestion, LangString, GlobalAttributeSuggestion, GlobalRelationshipSuggestion, LegalAct, LegalStructuralElement

#internals
from infrastructure.llm.SuggestionGeneratorPort import SuggestionGeneratorPort
from infrastructure.llm.prompt_constructors import PromptConstructorPort
from model.llm.generated_suggestions_model import (
  LocalSemanticModel,
  ClassExtractionResult,
  GlobalSemanticModelCategorized,
  AttributeExtractionResult,
  RelationshipExtractionResult,
  PropertyExtractionResult
)
from model.llm.conceptual_model import (
    ConceptualModel as LLMConceptualModel,
    Class as LLMClass,
    Attribute as LLMAttribute,
    Relationship as LLMRelationship
)

load_dotenv()

class SuggestionGenerator_OpenAI(SuggestionGeneratorPort):

    def __init__(
            self,
            prompt_constructor: PromptConstructorPort,
            model: str,
            language: str = "cs"):
        self.prompt_constructor = prompt_constructor
        self.model = model
        self.language = language
        self.openai_api_key = os.getenv("OPENAI_API_KEY")
        if not self.openai_api_key:
            raise ValueError("OPENAI_API_KEY is not set in the environment variables.")

    async def generate_top_k_class_suggestions(
            self,
            legal_act: LegalAct,
            k: int,
            structural_elements: List[LegalStructuralElement],
            context_text: Optional[str] = None,
            known_conceptual_model: Optional[DomainConceptualModel] = None,
            user_id: Optional[str] = None) -> AsyncGenerator[GlobalClassSuggestion, None]:
        
        client = AsyncOpenAI(api_key=self.openai_api_key)

        legal_text = ''
        if len(structural_elements) == 0:
            legal_text = ''.join([element.textContent for element in legal_act.consistsOf])
        else:
            for structural_element in structural_elements:
                legal_text += structural_element.textContent

        if known_conceptual_model is not None:
            known_conceptual_model_text = self._translate_domain_conceptual_model_to_llm_conceptual_model(known_conceptual_model)

        messages = self.prompt_constructor.construct_top_k_global_class_suggestion_prompt(legal_text, k, context_text if context_text else None, known_conceptual_model_text if known_conceptual_model else None)

        response = await client.beta.chat.completions.parse(
            model=self.model,
            messages=messages,
            response_format=ClassExtractionResult
        )

        class_extraction_result = response.choices[0].message.parsed
        global_class_suggestions = []
        parents = []
        for extracted_class in class_extraction_result.extracted_classes:

            global_class_suggestion = next((suggestion for suggestion in parents if suggestion.name.value == extracted_class.name), None)

            if global_class_suggestion is None:
                global_class_suggestion = GlobalClassSuggestion(
                    id=str(uuid4()),
                    name=LangString(**{"@value": extracted_class.name, "@language": self.language}),
                    isBasedOnLegalAct=legal_act,
                    createdFromLocalUniversalSuggestion=[]
                )

            if extracted_class.definition is not None:
                global_class_suggestion.definition = LangString(**{"@value": extracted_class.definition, "@language": self.language})
            else:
                global_class_suggestion.definition = None

            if extracted_class.explanation is not None:
                global_class_suggestion.explanation = LangString(**{"@value": extracted_class.explanation, "@language": self.language})
            else:
                global_class_suggestion.explanation = None

            if extracted_class.kind is not None:
                if extracted_class.kind == "subject":
                    global_class_suggestion.isSubjectOfLaw = True
                elif extracted_class.kind == "object":
                    global_class_suggestion.isObjectOfLaw = True
                elif extracted_class.kind == "event":
                    global_class_suggestion.isEvent = True
                elif extracted_class.kind == "document":
                    global_class_suggestion.isDocument = True

            if extracted_class.parent is not None:
                parent_name = extracted_class.parent
                parent_suggestion = None

                # Check if suggested parent already in global_class_suggestions
                for existing in global_class_suggestions:
                    if existing.name.value == parent_name:
                        parent_suggestion = existing
                        break

                # Check if suggested parent already in parents
                if parent_suggestion is None:
                    for existing in parents:
                        if existing.name.value == parent_name:
                            parent_suggestion = existing
                            break

                # Check if suggested parent in known_conceptual_model
                if parent_suggestion is None and known_conceptual_model is not None:
                    for domain_class in known_conceptual_model.classes:
                        if getattr(domain_class.name, 'value', None) == parent_name:
                            parent_suggestion = GlobalClassSuggestion(
                                id=domain_class.id,
                                name=LangString(**{"@value": parent_name, "@language": self.language}),
                                isBasedOnLegalAct=legal_act,
                                createdFromLocalUniversalSuggestion=[]
                            )
                            parents.append(parent_suggestion)
                            break

                # Otherwise, create new
                if parent_suggestion is None:
                    parent_suggestion = GlobalClassSuggestion(
                        id=str(uuid4()),
                        name=LangString(**{"@value": parent_name, "@language": self.language}),
                        isBasedOnLegalAct=legal_act,
                        createdFromLocalUniversalSuggestion=[]
                    )
                    parents.append(parent_suggestion)

                global_class_suggestion.specializes = [parent_suggestion]

            global_class_suggestions.append(global_class_suggestion)

            print(f"generate_top_k_class_suggestions: Identified class suggestion {global_class_suggestion.name.value} in the legal act {legal_act.officialNumber}")

            yield global_class_suggestion

        #check if there is any class suggestion in parents that is not in global_class_suggestions. For each of them, yield it as well
        for parent in parents:
            if parent not in global_class_suggestions and not any(
                getattr(domain_class.name, 'value', None) == parent.name.value for domain_class in known_conceptual_model.classes
            ):
                global_class_suggestions.append(parent)
                print(f"generate_top_k_class_suggestions: Identified parent class suggestion {parent.name.value} in the legal act {legal_act.officialNumber} which was not extracted as a primary class suggestion.")

        print(f"generate_top_k_class_suggestions: Identification of top K class in the legal act {legal_act.officialNumber} completed.")


    async def generate_top_k_property_suggestions_for_class(
            self,
            legal_act: LegalAct,
            k: int,
            structural_elements: List[LegalStructuralElement],
            selected_class: DomainClass,
            context_text: Optional[str] = None,
            known_conceptual_model: Optional[DomainConceptualModel] = None,
            user_id: Optional[str] = None) -> AsyncGenerator[tuple[str, GlobalAttributeSuggestion | GlobalRelationshipSuggestion], None]:
        # Gather legal text
        legal_text = ''
        if len(structural_elements) == 0:
            legal_text = ''.join([element.textContent for element in legal_act.consistsOf])
        else:
            for structural_element in structural_elements:
                legal_text += structural_element.textContent

        known_conceptual_model_text = None
        if known_conceptual_model is not None:
            known_conceptual_model_text = self._translate_domain_conceptual_model_to_llm_conceptual_model(known_conceptual_model, selected_class=selected_class)

        # Prepare prompt
        messages = self.prompt_constructor.construct_top_k_global_property_suggestion_prompt(
            legal_text, k, selected_class.name.value, context_text, known_conceptual_model_text
        )

        client = AsyncOpenAI(api_key=self.openai_api_key)
        response = await client.beta.chat.completions.parse(
            model=self.model,
            messages=messages,
            response_format=PropertyExtractionResult  # Assume JSON output
        )
        property_extraction_result = response.choices[0].message.parsed
        for extracted_property in property_extraction_result.extracted_properties:
            kind = extracted_property.kind
            if kind == 'attribute':
                global_attribute_suggestion = GlobalAttributeSuggestion(
                    id=str(uuid4()),
                    name=LangString(**{"@value": extracted_property.name, "@language": self.language}),
                    isBasedOnLegalAct=legal_act,
                    createdFromLocalUniversalSuggestion=[]
                )
                if extracted_property.definition is not None:
                    global_attribute_suggestion.definition = LangString(**{"@value": extracted_property.definition, "@language": self.language})
                else:
                    global_attribute_suggestion.definition = None

                if extracted_property.explanation is not None:
                    global_attribute_suggestion.explanation = LangString(**{"@value": extracted_property.explanation, "@language": self.language})
                else:
                    global_attribute_suggestion.explanation = None

                yield ("attribute", global_attribute_suggestion)

                print(f"generate_top_k_property_suggestions_for_class: Identified atttibute suggestion {global_attribute_suggestion.name.value} for the class {selected_class.name.value} in the legal act {legal_act.officialNumber}")
            elif kind == 'relationship':
                global_relationship_suggestion = GlobalRelationshipSuggestion(
                    id=str(uuid4()),
                    name=LangString(**{"@value": extracted_property.name, "@language": self.language}),
                    isBasedOnLegalAct=legal_act,
                    createdFromLocalUniversalSuggestion=[]
                )
                if extracted_property.definition is not None:
                    global_relationship_suggestion.definition = LangString(**{"@value": extracted_property.definition, "@language": self.language})
                else:
                    global_relationship_suggestion.definition = None

                if extracted_property.explanation is not None:
                    global_relationship_suggestion.explanation = LangString(**{"@value": extracted_property.explanation, "@language": self.language})
                else:
                    global_relationship_suggestion.explanation = None

                # Handle source and target classes for mediatesClass
                mediates_classes = []

                # Process source class
                source_domain_class = next(
                    (domain_class for domain_class in known_conceptual_model.classes if domain_class.name.value == extracted_property.source),
                    None
                )
                source_class = None
                if source_domain_class is None:
                    source_class = GlobalClassSuggestion(
                        id=str(uuid4()),
                        name=LangString(**{"@value": extracted_property.source, "@language": self.language}),
                        isBasedOnLegalAct=legal_act,
                        createdFromLocalUniversalSuggestion=[]
                    )
                else:
                    source_class = GlobalClassSuggestion(
                        id=source_domain_class.id,
                        name=LangString(**{"@value": source_domain_class.name.value, "@language": self.language}),
                        isBasedOnLegalAct=legal_act,
                        createdFromLocalUniversalSuggestion=[]
                    )
                mediates_classes.append(source_class)

                target_domain_class = next(
                    (domain_class for domain_class in known_conceptual_model.classes if domain_class.name.value == extracted_property.target),
                    None
                )
                target_class = None
                if target_domain_class is None:
                    target_class = GlobalClassSuggestion(
                        id=str(uuid4()),
                        name=LangString(**{"@value": extracted_property.target, "@language": self.language}),
                        isBasedOnLegalAct=legal_act,
                        createdFromLocalUniversalSuggestion=[]
                    )
                else:
                    target_class = GlobalClassSuggestion(
                        id=target_domain_class.id,
                        name=LangString(**{"@value": target_domain_class.name.value, "@language": self.language}),
                        isBasedOnLegalAct=legal_act,
                        createdFromLocalUniversalSuggestion=[]
                    )
                mediates_classes.append(target_class)

                global_relationship_suggestion.mediatesClass = mediates_classes
                yield ("relationship", global_relationship_suggestion)

                print(f"generate_top_k_property_suggestions_for_class: Identified relationship suggestion {global_relationship_suggestion.name.value} for the class {selected_class.name.value} in the legal act {legal_act.officialNumber}")

        print(f"generate_top_k_property_suggestions_for_class: Identification of top K properties for the class {selected_class.name.value} in the legal act {legal_act.officialNumber} completed.")
