from pydantic import BaseModel, Field
from uuid import UUID

class AcceptSuggestionRequest(BaseModel):
    job_id: UUID = Field(description="Unique identifier of the job in which the suggestion was generated")
    suggestion_id: str = Field(description="Unique identifier of the suggestion to accept")
