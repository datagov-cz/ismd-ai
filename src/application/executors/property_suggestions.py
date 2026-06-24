from typing import List, AsyncGenerator, Optional
from domain.models.suggestion_model import GlobalAttributeSuggestion, GlobalRelationshipSuggestion
from infrastructure.llm.ports.suggestion_generator import SuggestionGeneratorPort
from domain.models.suggestion_model import LegalAct, LegalStructuralElement
from domain.models.conceptual_model import ConceptualModel, Class

class PropertySuggestionExecutor:
    def __init__(
            self,
            generator: SuggestionGeneratorPort):
        self.generator = generator

    async def stream_top_k_global_property_suggestions(
            self,
            legal_act: LegalAct,
            k: int,
            structural_elements: List[LegalStructuralElement],
            selected_class: Class,
            context_text: Optional[str] = None,
            known_conceptual_model: Optional[ConceptualModel] = None,
            user_id: Optional[str] = None
        ) -> AsyncGenerator[tuple[str, GlobalAttributeSuggestion | GlobalRelationshipSuggestion], None]:
        async for item in self.generator.generate_top_k_property_suggestions_for_class(
            legal_act,
            k,
            structural_elements,
            selected_class,
            context_text,
            known_conceptual_model,
            user_id):
            yield item
