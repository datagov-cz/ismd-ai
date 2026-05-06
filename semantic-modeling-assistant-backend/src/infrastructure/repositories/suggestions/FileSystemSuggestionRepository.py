import os
import json
from typing import Dict, List, Optional
from model.domain.suggestion_model import GlobalClassSuggestion, GlobalRelationshipSuggestion, LocalUniversalSuggestion, LegalStructuralElement, LegalAct, LangString
from infrastructure.repositories.suggestions.SuggestionRepositoryPort import SuggestionRepositoryPort

data_directory = os.getenv("DATA_DIRECTORY", "data")

class FileSystemSuggestionRepository(SuggestionRepositoryPort):
    def __init__(self, storage_folder: str = f"{data_directory}/global_class_suggestions"):
        self.class_suggestions_by_act: Dict[str, Dict[str, GlobalClassSuggestion]] = {}
        self.relationship_suggestions_by_act: Dict[str, Dict[str, GlobalRelationshipSuggestion]] = {}
        self.storage_folder = storage_folder
        os.makedirs(self.storage_folder, exist_ok=True)

    def _get_file_path(self, legal_act_id: str) -> str:
        # Extract year, number, and date from the key
        parts = legal_act_id.split("/")
        year = parts[-3]
        number = parts[-2]
        date = parts[-1]
        file_name = f"{number}-{year}-{date}.json"
        return os.path.join(self.storage_folder, file_name)

    def save_class_suggestion(self, legal_act: LegalAct, suggestion: GlobalClassSuggestion) -> None:
        if legal_act.id not in self.class_suggestions_by_act:
            self.class_suggestions_by_act[legal_act.id] = {}
        self.class_suggestions_by_act[legal_act.id][suggestion.id] = suggestion

        file_path = self._get_file_path(legal_act.id)

        # Serialize and save to file
        with open(file_path, "w", encoding="utf-8") as file:
            exclude_keys = {
                'isBasedOnLegalAct': {
                    'officialTitle',
                    'officialNumber',
                    'consistsOf'
                },
                'createdFromLocalUniversalSuggestion': {
                    '__all__': {
                        'isBasedOnLegalStructuralElement' : {
                            'officialIdentifier',
                            'textContent',
                            'hasSuccessor'
                        }
                    }
                }
            }

            # Build the JSON array in memory
            suggestions_list = [
                s.model_dump_json(indent=2, by_alias=True, exclude=exclude_keys)
                for s in self.class_suggestions_by_act[legal_act.id].values()
            ]
            json_array = "[\n" + ",\n".join(suggestions_list) + "\n]"

            # Write the JSON array to the file
            file.write(json_array)

    def get_all_class_suggestions(self, legal_act: LegalAct) -> List[GlobalClassSuggestion]:
        suggestions = list(self.class_suggestions_by_act.get(legal_act.id, {}).values())
        if suggestions and len(suggestions) > 0:
            return suggestions

        file_path = self._get_file_path(legal_act.id)
        if not os.path.exists(file_path):
            return []

        self.class_suggestions_by_act[legal_act.id] = {}

        with open(file_path, "r", encoding="utf-8") as file:
            data = json.load(file)
            for item in data:
                # Validate isBasedOnLegalAct
                is_based_on_legal_act = None
                if "isBasedOnLegalAct" in item and item["isBasedOnLegalAct"].get("id") == legal_act.id:
                    is_based_on_legal_act = legal_act

                # Construct GlobalClassSuggestion instance manually
                suggestion = GlobalClassSuggestion(
                    id=item.get("id"),
                    name=LangString(**item["name"]) if "name" in item else None,
                    definition=LangString(**item["definition"]) if "definition" in item and item["definition"] is not None else None,
                    explanation=LangString(**item["explanation"]) if "explanation" in item and item["explanation"] is not None else None,
                    isBasedOnLegalAct=is_based_on_legal_act,
                    createdFromLocalUniversalSuggestion=[
                        LocalUniversalSuggestion(
                            id=sub_item.get("id"),
                            name=LangString(**sub_item["name"]) if "name" in sub_item else None,
                            definition=LangString(**sub_item["definition"]) if "definition" in sub_item and sub_item["definition"] is not None else None,
                            explanation=LangString(**sub_item["explanation"]) if "explanation" in sub_item and sub_item["explanation"] is not None else None,
                            isBasedOnLegalStructuralElement=next(
                                (element for element in (legal_act.consistsOf or [])
                                if element.id == sub_item.get("isBasedOnLegalStructuralElement", {}).get("id")),
                                None
                            ),
                            isIntroducedAs=sub_item.get("isIntroducedAs", [])
                        )
                        for sub_item in item.get("createdFromLocalUniversalSuggestion", [])
                    ],
                    isClass=item.get("isClass", False),
                    ownsAttribute=item.get("ownsAttribute")
                )

                self.class_suggestions_by_act[legal_act.id][suggestion.id] = suggestion

        return list(self.class_suggestions_by_act.get(legal_act.id, {}).values())

    def get_class_suggestion_by_id(self, legal_act: LegalAct, suggestion_id: str) -> Optional[GlobalClassSuggestion]:
        if legal_act.id not in self.class_suggestions_by_act:
            if self.get_all_class_suggestions(legal_act.id) == []:
                return None
        act_dict = self.class_suggestions_by_act[legal_act.id]
        if suggestion_id in act_dict:
            return act_dict[suggestion_id]
        return None

    def save_relationship_suggestion(self, legal_act: LegalAct, suggestion: GlobalRelationshipSuggestion) -> None:
        if legal_act.id not in self.relationship_suggestions_by_act:
            self.relationship_suggestions_by_act[legal_act.id] = {}
        self.relationship_suggestions_by_act[legal_act.id][suggestion.id] = suggestion

        file_path = self._get_file_path(legal_act.id)

        # Serialize and save to file
        with open(file_path, "w", encoding="utf-8") as file:
            exclude_keys = {
                'isBasedOnLegalAct': {
                    'officialTitle',
                    'officialNumber',
                    'consistsOf'
                },
                'createdFromLocalUniversalSuggestion': {
                    '__all__': {
                        'isBasedOnLegalStructuralElement': {
                            'officialIdentifier',
                            'textContent',
                            'hasSuccessor'
                        }
                    }
                },
                'mediatesClass': {
                    '__all__': {
                        '__exclude__': True,
                        'id': False
                    }
                }
            }

            # Build the JSON array in memory
            suggestions_list = [
                s.model_dump_json(indent=2, by_alias=True, exclude=exclude_keys)
                for s in self.relationship_suggestions_by_act[legal_act.id].values()
            ]
            json_array = "[\n" + ",\n".join(suggestions_list) + "\n]"

            # Write the JSON array to the file
            file.write(json_array)

    def get_all_relationship_suggestions(self, legal_act: LegalAct) -> List[GlobalRelationshipSuggestion]:
        # Check cache first
        suggestions = list(self.relationship_suggestions_by_act.get(legal_act.id, {}).values())
        if suggestions and len(suggestions) > 0:
            return suggestions

        file_path = self._get_file_path(legal_act.id)
        if not os.path.exists(file_path):
            return []

        # Ensure class suggestions for this act are loaded/available for lookup
        # This populates self.class_suggestions_by_act[legal_act.id]
        self.get_all_class_suggestions(legal_act)
        loaded_class_suggestions = self.class_suggestions_by_act.get(legal_act.id, {})

        self.relationship_suggestions_by_act[legal_act.id] = {}

        with open(file_path, "r", encoding="utf-8") as file:
            try:
                data = json.load(file)
            except json.JSONDecodeError:
                # Handle potential empty or invalid JSON file
                return []

            if not isinstance(data, list):
                 # Handle case where JSON is not a list as expected
                 return []

            for item in data:
                if not isinstance(item, dict):
                    # Skip items that are not dictionaries
                    continue

                # Validate isBasedOnLegalAct
                is_based_on_legal_act = None
                # Check if 'isBasedOnLegalAct' exists and is a dictionary before accessing 'get'
                if "isBasedOnLegalAct" in item and isinstance(item["isBasedOnLegalAct"], dict) and item["isBasedOnLegalAct"].get("id") == legal_act.id:
                    is_based_on_legal_act = legal_act

                # Construct mediatesClass list
                mediated_classes = []
                # Use "mediatesClass" as the key, consistent with saving logic
                related_class_refs = item.get("mediatesClass", [])
                if isinstance(related_class_refs, list):
                    for ref in related_class_refs:
                        # Ensure ref is a dictionary and has an 'id'
                        if isinstance(ref, dict):
                            class_id = ref.get("id")
                            if class_id:
                                # Look up the class suggestion using the pre-loaded dictionary
                                class_suggestion = loaded_class_suggestions.get(class_id)
                                if class_suggestion:
                                    mediated_classes.append(class_suggestion)
                                # else: Optional: log warning if class_id not found

                # Construct GlobalRelationshipSuggestion instance manually
                try:
                    suggestion = GlobalRelationshipSuggestion(
                        # Use .get() for safety, provide default None or raise error if ID is mandatory
                        id=item.get("id"),
                        # Add isinstance checks for LangString safety
                        name=LangString(**item["name"]) if "name" in item and isinstance(item["name"], dict) else None,
                        definition=LangString(**item["definition"]) if "definition" in item and isinstance(item["definition"], dict) else None,
                        explanation=LangString(**item["explanation"]) if "explanation" in item and isinstance(item["explanation"], dict) else None,
                        isBasedOnLegalAct=is_based_on_legal_act,
                        createdFromLocalUniversalSuggestion=[
                            LocalUniversalSuggestion(
                                id=sub_item.get("id"),
                                name=LangString(**sub_item["name"]) if "name" in sub_item and isinstance(sub_item["name"], dict) else None,
                                definition=LangString(**sub_item["definition"]) if "definition" in sub_item and isinstance(sub_item["definition"], dict) else None,
                                explanation=LangString(**sub_item["explanation"]) if "explanation" in sub_item and isinstance(sub_item["explanation"], dict) else None,
                                isBasedOnLegalStructuralElement=next(
                                    (element for element in (legal_act.consistsOf or [])
                                    # Add check for sub_item dict structure
                                    if isinstance(sub_item.get("isBasedOnLegalStructuralElement"), dict) and element.id == sub_item.get("isBasedOnLegalStructuralElement", {}).get("id")),
                                    None
                                ),
                                isIntroducedAs=sub_item.get("isIntroducedAs", [])
                            )
                            # Add check if sub_item is a dict
                            for sub_item in item.get("createdFromLocalUniversalSuggestion", []) if isinstance(sub_item, dict)
                        ],
                        isRelationship=item.get("isRelationship", False),
                        mediatesClass=mediated_classes # Assign the correctly constructed list
                    )
                    # Only add valid suggestions
                    if suggestion.id:
                         self.relationship_suggestions_by_act[legal_act.id][suggestion.id] = suggestion
                except Exception as e:
                    # Optional: Log the error and the item causing it for debugging
                    print(f"Error constructing suggestion from item: {item}. Error: {e}")
                    continue # Skip this item and proceed with the next

        return list(self.relationship_suggestions_by_act.get(legal_act.id, {}).values())

    def get_relationship_suggestion_by_id(self, legal_act: LegalAct, suggestion_id: str) -> Optional[GlobalRelationshipSuggestion]:
        if legal_act.id not in self.relationship_suggestions_by_act:
            if self.get_all_relationship_suggestions(legal_act) == []:
                return None
        act_dict = self.relationship_suggestions_by_act[legal_act.id]
        if suggestion_id in act_dict:
            return act_dict[suggestion_id]
        return None