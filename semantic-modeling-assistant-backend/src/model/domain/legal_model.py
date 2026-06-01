from pydantic import BaseModel, Field
from typing import Optional, List

class LegalStructuralElement(BaseModel):
    id: str
    officialIdentifier: str
    textContent: str = ""
    hasSuccessor: Optional[List["LegalStructuralElement"]] = None

class LegalAct(BaseModel):
    id: str
    officialTitle: str
    officialNumber: str
    consistsOf: List[LegalStructuralElement] = Field(default_factory=list)
