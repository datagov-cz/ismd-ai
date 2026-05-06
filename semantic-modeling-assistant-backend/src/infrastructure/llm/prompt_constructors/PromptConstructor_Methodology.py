from abc import ABC, abstractmethod
from typing import Optional
from infrastructure.llm.prompt_constructors.PromptConstructorPort import PromptConstructorPort

class PromptConstructor_Methodology(PromptConstructorPort):
    def __init__(self):
        self.system_prompt_top_k_global_class_suggestions = None
        self.system_prompt_top_k_global_attribute_suggestions = None
        self.system_prompt_top_k_global_relationship_suggestions = None
        self.system_prompt_top_k_global_property_suggestions = None
        self.system_prompt_update_existing_global_class_suggestions = None
        self.system_prompt_update_existing_global_attribute_suggestions = None
        self.system_prompt_update_existing_global_relationship_suggestions = None
        self.system_prompt_update_existing_global_property_suggestions = None
    
    def construct_top_k_global_class_suggestion_prompt(
            self,
            legal_text: str,
            k: int,
            context_text: Optional[str] = None,
            known_conceptual_model_text : Optional[str] = None) -> list[dict]:
        if context_text is None:
            context_text = ""
        user_prompt = f"""<SOURCE>{legal_text}</SOURCE><MODEL>{known_conceptual_model_text}</MODEL>\n<FOCUS>{context_text}</FOCUS>\n<K>{k}</K>"""
        return [
            {"role": "system", "content": self.system_prompt_top_k_global_class_suggestions},
            {"role": "user", "content": user_prompt}
        ]
    
    def construct_top_k_global_property_suggestion_prompt(
            self,
            legal_text: str,
            k: int,
            selected_class_name: str,
            context_text: Optional[str] = None,
            known_conceptual_model_text : Optional[str] = None) -> list[dict]:
        user_prompt = f"""<SOURCE>{legal_text}</SOURCE>\n<MODEL>{known_conceptual_model_text}</MODEL>\n<FOCUS>{context_text}</FOCUS>\n<K>{k}</K>\n<CLASS>{selected_class_name}</CLASS>\n"""
        return [
            {"role": "system", "content": self.system_prompt_top_k_global_property_suggestions},
            {"role": "user", "content": user_prompt}
        ]