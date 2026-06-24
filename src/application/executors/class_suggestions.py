from typing import List, AsyncGenerator, Optional
from domain.models.suggestion_model import GlobalClassSuggestion
from infrastructure.llm.ports.suggestion_generator import SuggestionGeneratorPort
from domain.models.suggestion_model import LegalAct, LegalStructuralElement
from domain.models.conceptual_model import ConceptualModel

class ClassSuggestionExecutor:
    def __init__(self,
                 generator: SuggestionGeneratorPort):
        self.generator = generator

    async def stream_top_k_global_class_suggestions(
            self,
            legal_act: LegalAct,
            k: int,
            structural_elements: List[LegalStructuralElement],
            context_text: Optional[str] = None,
            known_conceptual_model: Optional[ConceptualModel] = None,
            user_id: Optional[str] = None
        ) -> AsyncGenerator[GlobalClassSuggestion, None]:
        async for suggestion in self.generator.generate_top_k_class_suggestions(
            legal_act,
            k, 
            structural_elements,
            context_text,
            known_conceptual_model,
            user_id
        ):
            yield suggestion
