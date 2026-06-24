from pydantic import BaseModel, Field
class LangString(BaseModel):
    """Represents a multilingual string with a value and language."""
    value: str = Field(..., max_length=4000, alias="@value")
    language: str = Field(..., max_length=32, alias="@language")
