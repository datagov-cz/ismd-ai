from uuid import UUID
from typing import List, Optional
from model.domain.suggestion_model import GlobalUniversalSuggestion, GlobalAttributeSuggestion, GlobalRelationshipSuggestion
from model.domain.conceptual_model import ConceptualModel
from pydantic import BaseModel, Field

from datetime import datetime

class SuggestionJob(BaseModel):
    def start(self):
        self.started_at = datetime.now()

    def end(self):
        self.ended_at = datetime.now()
    job_id: UUID
    owner_user_id: str
    legal_act_key: str
    k: int
    structural_element_ids: Optional[List[str]] = None
    context_text: Optional[str] = None
    known_conceptual_model: Optional[ConceptualModel] = None
    status: str  # e.g., "pending", "in_progress", "completed"
    suggestions: List[GlobalUniversalSuggestion] = Field(default_factory=list)
    started_at: Optional[datetime] = None  # Timestamp when the job started
    ended_at: Optional[datetime] = None    # Timestamp when the job ended

class ClassSuggestionsJob(SuggestionJob):
    pass

class AttributeSuggestionJob(SuggestionJob):
    selected_class_id: UUID

class RelationshipSuggestionJob(SuggestionJob):
    selected_class_id: UUID

class PropertySuggestionJob(SuggestionJob):
    selected_class_id: str
    attribute_suggestions: List[GlobalAttributeSuggestion] = Field(default_factory=list)
    relationship_suggestions: List[GlobalRelationshipSuggestion] = Field(default_factory=list)
