from pydantic import BaseModel, Field
from typing import Optional, List
from .legal_model import LegalAct, LegalStructuralElement
from .lang_model import LangString

class UniversalSuggestion(BaseModel):
    id: str
    name: LangString
    definition: Optional[LangString] = None
    explanation: Optional[LangString] = None

class LocalUniversalSuggestion(UniversalSuggestion):
    isBasedOnLegalStructuralElement: LegalStructuralElement
    isIntroducedAs: Optional[List[str]] = None

class GlobalUniversalSuggestion(UniversalSuggestion):
    isBasedOnLegalAct: LegalAct
    createdFromLocalUniversalSuggestion: List[LocalUniversalSuggestion] = Field(default_factory=list)
    isBasedOnLegalStructuralElement: List[LegalStructuralElement] = Field(default_factory=list)

class AttributeSuggestion(UniversalSuggestion):
    isAttribute: bool = True

class ClassSuggestion(UniversalSuggestion):
    isClass: bool = True
    ownsAttribute: Optional[List[AttributeSuggestion]] = None

class RelationshipSuggestion(UniversalSuggestion):
    isRelationship: bool = True
    mediatesClass: Optional[List[ClassSuggestion]] = None

class GlobalClassSuggestion(ClassSuggestion, GlobalUniversalSuggestion):
    isClass: bool = True
    isSubjectOfLaw: bool = False
    isObjectOfLaw: bool = False
    isEvent: bool = False
    isDocument: bool = False
    specializes: Optional[List['GlobalClassSuggestion']] = None

class GlobalAttributeSuggestion(AttributeSuggestion, GlobalUniversalSuggestion):
    isAttribute: bool = True

class GlobalRelationshipSuggestion(RelationshipSuggestion, GlobalUniversalSuggestion):
    isRelationship: bool = True

class LocalClassSuggestion(ClassSuggestion, LocalUniversalSuggestion):
    isClass: bool = True

class LocalAttributeSuggestion(AttributeSuggestion, LocalUniversalSuggestion):
    isAttribute: bool = True

class LocalRelationshipSuggestion(RelationshipSuggestion, LocalUniversalSuggestion):
    isRelationship: bool = True
