from pydantic import AliasChoices, BaseModel, Field
from typing import Optional, List

class LocalSemanticModelClass(BaseModel):
  name: str = Field(description="the name of the class in the singular form")
  definition: Optional[str] = Field(description="the legally accurate definition of the class")
  explanation: Optional[str] = Field(description="the explanation of the class for lay users that is not in contradiction with the definition")
  introduction: List[str] = Field(description="the way the class is introduced in the text as a list consisting of 'defining', 'clarifying', 'property detailing', or 'referring'")

class LocalSemanticModel(BaseModel):
  classes: List[LocalSemanticModelClass] = Field(description="the list of extracted classes")

class ExtractedClass(BaseModel):
  kind: str = Field(description="subject | object | \"\"")
  name: str = Field(description="name")
  definition: Optional[str] = Field(description="definition")
  explanation: Optional[str] = Field(description="explanation")
  original: Optional[str] = Field(description="original definition text")
  references: Optional[List[str]] = Field(description="references")
  parent: Optional[str] = Field(description="parent")

class ClassExtractionResult(BaseModel):
  extracted_classes: List[ExtractedClass] = Field(description="a list of extracted classes")

class GlobalSemanticModelClassCategorized(BaseModel):
  name: str = Field(description="the name of the class in the singular form")
  kind: str = Field(description="the kind of the class, one of 'subject', 'object' or ''")

class GlobalSemanticModelCategorized(BaseModel):
  classes: List[GlobalSemanticModelClassCategorized] = Field(description="a list of classes with assigned kinds")

class ExtractedAttribute(BaseModel):
  name: str = Field(description="Singular Czech label, short, no abbreviations")
  reasoning: str = Field(description="Reasoning about the correctness of the property using a natural sentence speaking about a hyphotetical instance of <CLASS> and the property in a semantically meaningful way")
  definition: Optional[str] = Field(description="Exact quote from <SOURCE> or null")
  explanation: Optional[str] = Field(description="≤ 80-word Czech analytic note")
  dataType: Optional[str] = Field(description="string | number | date | bool | \"\"")
  references: Optional[List[str]] = Field(description="paragraph number(s)")

class AttributeExtractionResult(BaseModel):
  extracted_attributes: List[ExtractedAttribute] = Field(description="a list of extracted attributes")

class ExtractedRelationship(BaseModel):
  name: str = Field(description="Singular Czech label, short, no abbreviations")
  reasoning: str = Field(description="Reasoning about the correctness of the property using a natural sentence speaking about a hyphotetical instance of <CLASS> and the property in a semantically meaningful way.")
  definition: Optional[str] = Field(description="Exact quote from <SOURCE> or null")
  explanation: Optional[str] = Field(description="≤ 80-word Czech analytic note")
  source: str = Field(description="class name or \"\"")
  target: str = Field(description="class name or \"\"")
  relationshipType: str = Field(description="association | part-of | specialization | \"\"")
  references: Optional[List[str]] = Field(description="paragraph number(s)")

class RelationshipExtractionResult(BaseModel):
  extracted_relationships: List[ExtractedRelationship] = Field(description="a list of extracted relationships")

class ExtractedProperty(BaseModel):
  kind: str = Field(description="kind")
  name: str = Field(description="name")
  definition: Optional[str] = Field(description="definition")
  explanation: Optional[str] = Field(description="explanation")
  dataType: Optional[str] = Field(description="dataType")
  source: str = Field(description="source class name")
  target: str = Field(description="target class name")
  references: Optional[List[str]] = Field(description="references")

class PropertyExtractionResult(BaseModel):
  extracted_properties: List[ExtractedProperty] = Field(
    description="a list of extracted properties",
    validation_alias=AliasChoices("extracted_properties", "extracted-properties", "extracted_items")
  )
