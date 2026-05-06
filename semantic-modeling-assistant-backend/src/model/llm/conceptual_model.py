from pydantic import BaseModel, Field
from typing import Optional, List

class Attribute(BaseModel):
    name: str
    definition: Optional[str]
    explanation: Optional[str]

class Class(BaseModel):
    name: str
    type: Optional[str]
    definition: Optional[str]
    explanation: Optional[str]
    ownsAttribute: Optional[List[Attribute]]
    parent: Optional[str]

class Relationship(BaseModel):
    name: str
    definition: Optional[str]
    explanation: Optional[str]
    source: Optional[str]
    target: Optional[str]

class ConceptualModel(BaseModel):
    classes: List[Class] = Field(default_factory=list)
    relationships: List[Relationship] = Field(default_factory=list)