from pydantic import BaseModel, Field
from typing import List

class LangString(BaseModel):
    value: str = Field(..., alias="@value")
    language: str = Field(..., alias="@language")