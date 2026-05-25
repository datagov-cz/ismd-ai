import os
import json
import re
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

load_dotenv()

class SuggestionGenerator_OpenAI_Streamed(SuggestionGeneratorPort):

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
        if len(structural_elements) > 0:
            for structural_element in structural_elements:
                text = structural_element.textContent
                text = re.sub(r'(§\s*[0-9]+)', r'<REFERENCE>\1</REFERENCE>', text)
                legal_text += text + "\n\n"
        else:
            raise Exception("No structural elements provided and cannot generate legal_text.")

        if known_conceptual_model is not None:
            known_conceptual_model_text = self._translate_domain_conceptual_model_classes_to_llm_conceptual_model_classes(known_conceptual_model)

        input = self.prompt_constructor.construct_top_k_global_class_suggestion_prompt(legal_text, k, context_text if context_text else None, known_conceptual_model_text if known_conceptual_model else None)

        buffer = ''
        yielded_class_names = set()
        global_class_suggestions = []
        parents = []
        import json as _json
        from model.llm.generated_suggestions_model import ClassExtractionResult

        async with client.responses.stream(
            model=self.model,
            input=input,
            text_format=ClassExtractionResult,
        ) as stream:
            async for event in stream:
                if event.type == "response.refusal.delta":
                    print(event.delta, end="")
                elif event.type == "response.output_text.delta":
                    buffer += event.delta
                    pattern = re.compile(r'}\s*,\s*{|}\s*]')
                    while True:
                        match = pattern.search(buffer)
                        if not match:
                            break
                        # Find the start of the last object before the split
                        obj_start = buffer.rfind('{', 0, match.start() + 1)
                        obj_end = match.start() + 1  # include the closing brace
                        if obj_start == -1:
                            break
                        json_text = buffer[obj_start:obj_end]
                        try:
                            parsed_obj = _json.loads(json_text)
                            # Wrap in a fake extracted_classes array for compatibility
                            parsed = {"extracted_classes": [parsed_obj]}
                            for extracted_class in parsed["extracted_classes"]:
                                class_name = extracted_class.get("name")
                                
                                if class_name and class_name not in yielded_class_names:
                                    # check if class_name exists in parents
                                    if any(parent.name.value == class_name for parent in parents):
                                        # assign the parent from parents to global_class_suggestion
                                        parent_suggestion = next(parent for parent in parents if parent.name.value == class_name)
                                        global_class_suggestion = parent_suggestion
                                    else:
                                        global_class_suggestion = GlobalClassSuggestion(
                                            id=str(uuid4()),
                                            name=LangString(**{"@value": extracted_class["name"], "@language": self.language}),
                                            isBasedOnLegalAct=legal_act,
                                            createdFromLocalUniversalSuggestion=[]
                                        )

                                    if extracted_class.get("definition") is not None:
                                        global_class_suggestion.definition = LangString(**{"@value": extracted_class["definition"], "@language": self.language})
                                    else:
                                        global_class_suggestion.definition = None
                                    if extracted_class.get("explanation") is not None:
                                        global_class_suggestion.explanation = LangString(**{"@value": extracted_class["explanation"], "@language": self.language})
                                    else:
                                        global_class_suggestion.explanation = None
                                    if extracted_class.get("kind") is not None:
                                        if extracted_class["kind"] == "subject":
                                            global_class_suggestion.isSubjectOfLaw = True
                                        elif extracted_class["kind"] == "object":
                                            global_class_suggestion.isObjectOfLaw = True
                                        elif extracted_class["kind"] == "event":
                                            global_class_suggestion.isEvent = True
                                        elif extracted_class["kind"] == "document":
                                            global_class_suggestion.isDocument = True

                                    if extracted_class.get("parent") is not None and isinstance(extracted_class.get("parent"), str) and extracted_class.get("parent").strip() != "":
                                        parent_name = extracted_class["parent"]
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

                                    # Map "references" to createdFromLocalUniversalSuggestion as LocalClassSuggestion
                                    references = extracted_class.get("references", [])
                                    for reference in references:
                                        legal_structural_element = LegalStructuralElement(
                                            id=reference,
                                            officialIdentifier=reference
                                        )
                                        global_class_suggestion.isBasedOnLegalStructuralElement.append(legal_structural_element)


                                    global_class_suggestions.append(global_class_suggestion)
                                    yield global_class_suggestion
                                    yielded_class_names.add(class_name)
                                    print(f"generate_top_k_class_suggestions (streamed): Identified class suggestion {global_class_suggestion.name.value} in the legal act {legal_act.officialNumber}")
                        except _json.JSONDecodeError:
                            pass
                        except Exception as e:
                            print(f"Error in generate_top_k_class_suggestions: {e}")
                            raise
                        # Remove the processed object from the buffer
                        buffer = buffer[obj_end:]
                elif event.type == "response.error":
                    print(event.error, end="")
                elif event.type == "response.completed":
                    for parent in parents:
                        if parent not in global_class_suggestions and not any(
                            getattr(domain_class.name, 'value', None) == parent.name.value for domain_class in known_conceptual_model.classes
                        ):
                            global_class_suggestions.append(parent)
                            yield parent
                            print(f"generate_top_k_class_suggestions: Identified parent class suggestion {parent.name.value} in the legal act {legal_act.officialNumber} which was not extracted as a primary class suggestion.")

                    print(f"generate_top_k_class_suggestions: Identification of top K class in the legal act {legal_act.officialNumber} completed.")
                    print("Completed")

    async def generate_top_k_property_suggestions_for_class(
            self,
            legal_act: LegalAct,
            k: int,
            structural_elements: List[LegalStructuralElement],
            selected_class: DomainClass,
            context_text: Optional[str] = None,
            known_conceptual_model: Optional[DomainConceptualModel] = None,
            user_id: Optional[str] = None
        ) -> AsyncGenerator[tuple[str, GlobalAttributeSuggestion | GlobalRelationshipSuggestion], None]:
        # Gather legal text
        legal_text = ''
        if len(structural_elements) > 0:
            for structural_element in structural_elements:
                text = structural_element.textContent
                text = re.sub(r'(§\s*[0-9]+)', r'<REFERENCE>\1</REFERENCE>', text)
                legal_text += text + "\n\n"
        else:
            raise Exception("No structural elements provided and cannot generate legal_text.")

        known_conceptual_model_text = None
        if known_conceptual_model is not None:
            known_conceptual_model_text = self._translate_domain_conceptual_model_to_llm_conceptual_model(known_conceptual_model, selected_class)

        # Prepare prompt
        input = self.prompt_constructor.construct_top_k_global_property_suggestion_prompt(
            legal_text, k, selected_class.name.value, context_text, known_conceptual_model_text
        )

        client = AsyncOpenAI(api_key=self.openai_api_key)
        buffer = ''
        import json as _json
        from model.llm.generated_suggestions_model import PropertyExtractionResult
        pattern = re.compile(r'}\s*,\s*\{|}\s*]')

        # Track already created class suggestions by name
        created_class_suggestions = {}

        async with client.responses.stream(
            model=self.model,
            input=input,
            text_format=PropertyExtractionResult,
            reasoning={},
            tools=[],
            temperature=0,
            max_output_tokens=2048,
            top_p=1,
        ) as stream:
            async for event in stream:
                if event.type == "response.refusal.delta":
                    print(event.delta, end="")
                elif event.type == "response.output_text.delta":
                    buffer += event.delta
                    # Streamed JSON object extraction algorithm
                    while True:
                        match = pattern.search(buffer)
                        if not match:
                            break
                        obj_start = buffer.rfind('{', 0, match.start() + 1)
                        obj_end = match.start() + 1
                        if obj_start == -1:
                            break
                        json_text = buffer[obj_start:obj_end]
                        try:
                            parsed_obj = _json.loads(json_text)
                            # Wrap in a fake extracted_properties array for compatibility
                            parsed = {"extracted_properties": [parsed_obj]}
                            for extracted_property in parsed["extracted_properties"]:
                                kind = extracted_property.get('kind')
                                if kind == 'attribute':
                                    global_attribute_suggestion = GlobalAttributeSuggestion(
                                        id=str(uuid4()),
                                        name=LangString(**{"@value": extracted_property["name"], "@language": self.language}),
                                        isBasedOnLegalAct=legal_act,
                                        createdFromLocalUniversalSuggestion=[]
                                    )
                                    if extracted_property.get("definition") is not None:
                                        global_attribute_suggestion.definition = LangString(**{"@value": extracted_property["definition"], "@language": self.language})
                                    else:
                                        global_attribute_suggestion.definition = None
                                    if extracted_property.get("explanation") is not None:
                                        global_attribute_suggestion.explanation = LangString(**{"@value": extracted_property["explanation"], "@language": self.language})
                                    else:
                                        global_attribute_suggestion.explanation = None
                                    references = extracted_property.get("references", [])
                                    for reference in references:
                                        legal_structural_element = LegalStructuralElement(
                                            id=reference,
                                            officialIdentifier=reference
                                        )
                                        global_attribute_suggestion.isBasedOnLegalStructuralElement.append(legal_structural_element)
                                    yield ("attribute", global_attribute_suggestion)
                                    print(f"generate_top_k_property_suggestions_for_class (streamed): Identified attribute suggestion {global_attribute_suggestion.name.value} for the class {selected_class.name.value} in the legal act {legal_act.officialNumber}")
                                elif kind == 'relationship':
                                    global_relationship_suggestion = GlobalRelationshipSuggestion(
                                        id=str(uuid4()),
                                        name=LangString(**{"@value": extracted_property["name"], "@language": self.language}),
                                        isBasedOnLegalAct=legal_act,
                                        createdFromLocalUniversalSuggestion=[]
                                    )
                                    if extracted_property.get("definition") is not None:
                                        global_relationship_suggestion.definition = LangString(**{"@value": extracted_property["definition"], "@language": self.language})
                                    else:
                                        global_relationship_suggestion.definition = None
                                    if extracted_property.get("explanation") is not None:
                                        global_relationship_suggestion.explanation = LangString(**{"@value": extracted_property["explanation"], "@language": self.language})
                                    else:
                                        global_relationship_suggestion.explanation = None
                                    # Handle source and target classes for mediatesClass
                                    mediates_classes = []
                                    # Process source class
                                    source_name = extracted_property.get("source")
                                    source_domain_class = next(
                                        (domain_class for domain_class in known_conceptual_model.classes if domain_class.name.value == source_name),
                                        None
                                    ) if known_conceptual_model is not None else None
                                    if source_domain_class is not None:
                                        source_class = GlobalClassSuggestion(
                                            id=source_domain_class.id,
                                            name=LangString(**{"@value": source_domain_class.name.value, "@language": self.language}),
                                            isBasedOnLegalAct=legal_act,
                                            createdFromLocalUniversalSuggestion=[]
                                        )
                                    elif source_name in created_class_suggestions:
                                        source_class = created_class_suggestions[source_name]
                                    else:
                                        source_class = GlobalClassSuggestion(
                                            id=str(uuid4()),
                                            name=LangString(**{"@value": source_name, "@language": self.language}),
                                            isBasedOnLegalAct=legal_act,
                                            createdFromLocalUniversalSuggestion=[]
                                        )
                                        created_class_suggestions[source_name] = source_class
                                    mediates_classes.append(source_class)
                                    # Process target class
                                    target_name = extracted_property.get("target")
                                    target_domain_class = next(
                                        (domain_class for domain_class in known_conceptual_model.classes if domain_class.name.value == target_name),
                                        None
                                    ) if known_conceptual_model is not None else None
                                    if target_domain_class is not None:
                                        target_class = GlobalClassSuggestion(
                                            id=target_domain_class.id,
                                            name=LangString(**{"@value": target_domain_class.name.value, "@language": self.language}),
                                            isBasedOnLegalAct=legal_act,
                                            createdFromLocalUniversalSuggestion=[]
                                        )
                                    elif target_name in created_class_suggestions:
                                        target_class = created_class_suggestions[target_name]
                                    else:
                                        target_class = GlobalClassSuggestion(
                                            id=str(uuid4()),
                                            name=LangString(**{"@value": target_name, "@language": self.language}),
                                            isBasedOnLegalAct=legal_act,
                                            createdFromLocalUniversalSuggestion=[]
                                        )
                                        created_class_suggestions[target_name] = target_class
                                    mediates_classes.append(target_class)
                                    references = extracted_property.get("references", [])
                                    for reference in references:
                                        legal_structural_element = LegalStructuralElement(
                                            id=reference,
                                            officialIdentifier=reference
                                        )
                                        global_relationship_suggestion.isBasedOnLegalStructuralElement.append(legal_structural_element)
                                    global_relationship_suggestion.mediatesClass = mediates_classes
                                    yield ("relationship", global_relationship_suggestion)
                                    print(f"generate_top_k_property_suggestions_for_class (streamed): Identified relationship suggestion {global_relationship_suggestion.name.value} for the class {selected_class.name.value} in the legal act {legal_act.officialNumber}")
                                    # Print also isBasedOnLegalStructuralElement list
                                    print(
                                        f"with isBasedOnLegalStructuralElement: {[lse.officialIdentifier for lse in global_relationship_suggestion.isBasedOnLegalStructuralElement]}"
                                    )

                        except _json.JSONDecodeError:
                            pass
                        except Exception as e:
                            print(f"Error in generate_top_k_class_suggestions: {e}")
                            raise
                        buffer = buffer[obj_end:]
                    # Try to parse the full buffer as JSON (for the last object or if only one object)
                    try:
                        parsed = _json.loads(buffer)
                        if "extracted_properties" in parsed:
                            for extracted_property in parsed["extracted_properties"]:
                                kind = extracted_property.get('kind')
                                if kind == 'attribute':
                                    global_attribute_suggestion = GlobalAttributeSuggestion(
                                        id=str(uuid4()),
                                        name=LangString(**{"@value": extracted_property["name"], "@language": self.language}),
                                        isBasedOnLegalAct=legal_act,
                                        createdFromLocalUniversalSuggestion=[]
                                    )
                                    if extracted_property.get("definition") is not None:
                                        global_attribute_suggestion.definition = LangString(**{"@value": extracted_property["definition"], "@language": self.language})
                                    else:
                                        global_attribute_suggestion.definition = None
                                    if extracted_property.get("explanation") is not None:
                                        global_attribute_suggestion.explanation = LangString(**{"@value": extracted_property["explanation"], "@language": self.language})
                                    else:
                                        global_attribute_suggestion.explanation = None
                                    yield ("attribute", global_attribute_suggestion)
                                    print(f"generate_top_k_property_suggestions_for_class (streamed): Identified attribute suggestion {global_attribute_suggestion.name.value} for the class {selected_class.name.value} in the legal act {legal_act.officialNumber}")
                                elif kind == 'relationship':
                                    global_relationship_suggestion = GlobalRelationshipSuggestion(
                                        id=str(uuid4()),
                                        name=LangString(**{"@value": extracted_property["name"], "@language": self.language}),
                                        isBasedOnLegalAct=legal_act,
                                        createdFromLocalUniversalSuggestion=[]
                                    )
                                    if extracted_property.get("definition") is not None:
                                        global_relationship_suggestion.definition = LangString(**{"@value": extracted_property["definition"], "@language": self.language})
                                    else:
                                        global_relationship_suggestion.definition = None
                                    if extracted_property.get("explanation") is not None:
                                        global_relationship_suggestion.explanation = LangString(**{"@value": extracted_property["explanation"], "@language": self.language})
                                    else:
                                        global_relationship_suggestion.explanation = None
                                    mediates_classes = []
                                    source_name = extracted_property.get("source")
                                    source_domain_class = next(
                                        (domain_class for domain_class in known_conceptual_model.classes if domain_class.name.value == source_name),
                                        None
                                    ) if known_conceptual_model is not None else None
                                    if source_domain_class is not None:
                                        source_class = GlobalClassSuggestion(
                                            id=source_domain_class.id,
                                            name=LangString(**{"@value": source_domain_class.name.value, "@language": self.language}),
                                            isBasedOnLegalAct=legal_act,
                                            createdFromLocalUniversalSuggestion=[]
                                        )
                                    elif source_name in created_class_suggestions:
                                        source_class = created_class_suggestions[source_name]
                                    else:
                                        source_class = GlobalClassSuggestion(
                                            id=str(uuid4()),
                                            name=LangString(**{"@value": source_name, "@language": self.language}),
                                            isBasedOnLegalAct=legal_act,
                                            createdFromLocalUniversalSuggestion=[]
                                        )
                                        created_class_suggestions[source_name] = source_class
                                    mediates_classes.append(source_class)
                                    target_name = extracted_property.get("target")
                                    target_domain_class = next(
                                        (domain_class for domain_class in known_conceptual_model.classes if domain_class.name.value == target_name),
                                        None
                                    ) if known_conceptual_model is not None else None
                                    if target_domain_class is not None:
                                        target_class = GlobalClassSuggestion(
                                            id=target_domain_class.id,
                                            name=LangString(**{"@value": target_domain_class.name.value, "@language": self.language}),
                                            isBasedOnLegalAct=legal_act,
                                            createdFromLocalUniversalSuggestion=[]
                                        )
                                    elif target_name in created_class_suggestions:
                                        target_class = created_class_suggestions[target_name]
                                    else:
                                        target_class = GlobalClassSuggestion(
                                            id=str(uuid4()),
                                            name=LangString(**{"@value": target_name, "@language": self.language}),
                                            isBasedOnLegalAct=legal_act,
                                            createdFromLocalUniversalSuggestion=[]
                                        )
                                        created_class_suggestions[target_name] = target_class
                                    mediates_classes.append(target_class)
                                    global_relationship_suggestion.mediatesClass = mediates_classes
                                    yield ("relationship", global_relationship_suggestion)
                                    print(f"generate_top_k_property_suggestions_for_class (streamed): Identified relationship suggestion {global_relationship_suggestion.name.value} for the class {selected_class.name.value} in the legal act {legal_act.officialNumber}")
                    except _json.JSONDecodeError:
                        pass
                    except Exception as e:
                        print(f"Error in generate_top_k_class_suggestions: {e}")
                        raise
                elif event.type == "response.error":
                    print(event.error, end="")
                elif event.type == "response.completed":
                    print(f"generate_top_k_property_suggestions_for_class: Identification of top K properties for the class {selected_class.name.value} in the legal act {legal_act.officialNumber} completed.")
                    print("Completed")
