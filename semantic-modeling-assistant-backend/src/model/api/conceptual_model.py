from pydantic import BaseModel, Field
from typing import Optional, List
from .lang_model import LangString

class Universal(BaseModel):
    """Base class for all entities in the conceptual model."""
    id: str = Field(max_length=256, description="Unique application identifier for the entity")
    name: LangString = Field(description="Multilingual name of the entity")
    definition: Optional[LangString] = Field(default=None, description="Multilingual definition of the entity")
    explanation: Optional[LangString] = Field(default=None, description="Multilingual explanation of the entity semantics for human readers")

class Attribute(Universal):
    """Represents an attribute of a class in the conceptual model."""
    isAttribute: bool = Field(default=True, description="Indicates that this is an attribute")

class ClassReference(BaseModel):
    """Represents a reference to a class in the conceptual model."""
    id: str = Field(max_length=256, description="Unique identifier of the referenced class")

class Class(Universal):
    """Represents a class in the conceptual model."""
    isClass: bool = Field(default=True, description="Indicates that this is a class")
    isSubjectOfLaw: bool = Field(default=False, description="Whether this class represents a subject of law (e.g., person, organization)")
    isObjectOfLaw: bool = Field(default=False, description="Whether this class represents an object of law (e.g., property, asset)")
    isEvent: bool = Field(default=False, description="Whether this class represents an event or process")
    isDocument: bool = Field(default=False, description="Whether this class represents a document")
    ownsAttribute: Optional[List[Attribute]] = Field(default=None, max_length=200, description="List of attributes owned by this class")
    specializes: Optional[List[ClassReference]] = Field(default=None, max_length=50, description="List of references to the parent classes that this class specializes")

class Relationship(Universal):
    """Represents a relationship mediating classes in the conceptual model. Only binary relationships mediating two classes are considered in the current implementation, but this can be extended to support n-ary relationships in the future."""
    isRelationship: bool = Field(default=True, description="Indicates that this is a relationship")
    mediatesClass: Optional[List[ClassReference]] = Field(default=None, max_length=10, description="List of classes that this relationship mediates between. Only binary relationships mediating two classes are considered in the current implementation, but this can be extended to support n-ary relationships in the future.")

class ConceptualModel(BaseModel):
    """Represents a conceptual model consisting of classes and relationships."""
    classes: List[Class] = Field(default_factory=list, max_length=500, description="List of classes in the conceptual model")
    relationships: List[Relationship] = Field(default_factory=list, max_length=500, description="List of relationships in the conceptual model")
