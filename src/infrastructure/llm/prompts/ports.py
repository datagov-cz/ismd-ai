from abc import ABC, abstractmethod
from typing import Optional

class PromptConstructorPort(ABC):
    """
    An interface for constructing prompts for various LLM tasks.
    """
    @abstractmethod
    def construct_top_k_global_class_suggestion_prompt(
            self,
            legal_text: str,
            k: int,
            context_text: Optional[str] = None,
            known_conceptual_model_text : Optional[str] = None) -> list[dict]:
        pass

    @abstractmethod
    def construct_top_k_global_property_suggestion_prompt(
            self,
            legal_text: str,
            k: int,
            selected_class_name: str,
            context_text: Optional[str] = None,
            known_conceptual_model_text : Optional[str] = None) -> list[dict]:
        pass