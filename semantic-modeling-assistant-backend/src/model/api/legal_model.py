from pydantic import BaseModel, Field

class LegalStructuralElement(BaseModel):
    id: str = Field(description="Unique application identifier of the paragraph (structural element) in the legal act")
    type: str = Field(description="Type of the structural element. It is reserved for Linked Data publication, not used for any application logic.")
    official_identifier: str = Field(description="Official identifier of the paragraph (structural element) in the legal act, e.g., '§ 1'")

class LegalAct(BaseModel):
    id: str = Field(description="Unique application identifier of the legal act")
    type: str = Field(description="Type of the legal act. It is reserved for Linked Data publication, not used for any application logic.")
    official_number: str = Field(description="Official number of the legal act, e.g., '123/2023'")