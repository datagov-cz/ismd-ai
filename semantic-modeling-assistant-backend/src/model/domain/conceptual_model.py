from pydantic import BaseModel, Field
from typing import Optional, List
from .legal_model import LegalAct, LegalStructuralElement
from .lang_model import LangString

class Universal(BaseModel):
    id: str
    name: LangString
    definition: Optional[LangString] = None
    explanation: Optional[LangString] = None
    introducedInLegalAct: Optional[LegalAct] = None
    occursInStructuralElement: Optional[List[LegalStructuralElement]] = []

class Attribute(Universal):
    isAttribute: bool = True

class Class(Universal):
    isClass: bool = True
    isSubjectOfLaw: bool = False
    isObjectOfLaw: bool = False
    isEvent: bool = False
    isDocument: bool = False
    ownsAttribute: Optional[List[Attribute]] = None
    specializesClass: Optional[List['Class']] = None

class Relationship(Universal):
    isRelationship: bool = True
    mediatesClass: Optional[List[Class]] = None

class ConceptualModel(BaseModel):
    classes: List[Class] = Field(default_factory=list)
    relationships: List[Relationship] = Field(default_factory=list)