from pydantic import BaseModel, Field
from typing import List

class LangString(BaseModel):
    """Represents a multilingual string with a value and language."""
    value: str = Field(..., alias="@value")
    language: str = Field(..., alias="@language")