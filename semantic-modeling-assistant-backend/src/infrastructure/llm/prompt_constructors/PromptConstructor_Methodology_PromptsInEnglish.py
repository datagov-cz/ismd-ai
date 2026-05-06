from abc import ABC, abstractmethod
from typing import Optional
from infrastructure.llm.prompt_constructors.PromptConstructor_Methodology import PromptConstructor_Methodology
from infrastructure.llm.prompt_constructors.SystemPrompts_Methodology_English import (
    SYSTEM_PROMPT_NEW_TOP_K_GLOBAL_CLASS_SUGGESTIONS,
    SYSTEM_PROMPT_NEW_TOP_K_GLOBAL_PROPERTY_SUGGESTIONS)

class PromptConstructor_Methodology_PromptsInEnglish(PromptConstructor_Methodology):
    def __init__(self):
        self.system_prompt_top_k_global_class_suggestions = SYSTEM_PROMPT_NEW_TOP_K_GLOBAL_CLASS_SUGGESTIONS
        self.system_prompt_top_k_global_property_suggestions = SYSTEM_PROMPT_NEW_TOP_K_GLOBAL_PROPERTY_SUGGESTIONS