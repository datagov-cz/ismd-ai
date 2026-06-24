from abc import ABC, abstractmethod
from typing import List, Optional
from domain.models.suggestion_model import GlobalClassSuggestion, GlobalRelationshipSuggestion, LegalAct

class SuggestionRepositoryPort(ABC):
    @abstractmethod
    def save_class_suggestion(self, legal_act: LegalAct, suggestion: GlobalClassSuggestion) -> None:
        pass

    @abstractmethod
    def get_all_class_suggestions(self, legal_act: LegalAct) -> List[GlobalClassSuggestion]:
        pass

    @abstractmethod
    def get_class_suggestion_by_id(self, legal_act: LegalAct, suggestion_id: str) -> Optional[GlobalClassSuggestion]:
        pass

    @abstractmethod
    def save_relationship_suggestion(self, legal_act: LegalAct, suggestion: GlobalRelationshipSuggestion) -> None:
        pass

    @abstractmethod
    def get_all_relationship_suggestions(self, legal_act: LegalAct) -> List[GlobalRelationshipSuggestion]:
        pass

    @abstractmethod
    def get_relationship_suggestion_by_id(self, legal_act: LegalAct, suggestion_id: str) -> Optional[GlobalRelationshipSuggestion]:
        pass