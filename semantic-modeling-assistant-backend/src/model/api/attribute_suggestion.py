from pydantic import BaseModel, Field
from typing import List, Optional
from uuid import UUID
from .legal_model import LegalAct, LegalStructuralElement
from .lang_model import LangString
from .conceptual_model import ConceptualModel

class LocalAttributeSuggestion(BaseModel):
    """Represents a local attribute suggestion extracted from a specific part of a legal act."""
    id: str = Field(description="Unique identifier for the local attribute suggestion for which an attribute suggestion was made")
    type: str = Field(description="Type of the local attribute suggestion. It is reserved for Linked Data publication, not used for any application logic.")
    legal_act_part: LegalStructuralElement = Field(description="The specific part of the legal act where this local attribute suggestion is localized and where it was identified")

class AttributeSuggestion(BaseModel):
    """
    Represents a suggested attribute extracted from the legal act based on one or more local occurences in the act.

    This class encapsulates information about attributes that can be suggested for a class in a conceptual model, including multilingual names and definitions, and local occurences from where the attribute suggestion was extracted."""
    id: str = Field(description="Unique identifier for the attribute suggestion")
    type: str = Field(description="Type of the suggested attribute. It is reserved for Linked Data publication, not used for any application logic.")
    name: LangString = Field(description="Multilingual name of the suggested attribute")
    definition: Optional[LangString] = Field(default=None, description="Multilingual definition of the suggested attribute")
    explanation: Optional[LangString] = Field(default=None, description="Multilingual explanation of the attribute semantics for human readers")
    legal_act: LegalAct = Field(description="Legal act from which this suggestion was extracted")
    occurences: Optional[List[LocalAttributeSuggestion]] = Field(default=[], description="Specific occurrences of this attribute within the legal act")
    references: Optional[List[LegalStructuralElement]] = Field(default=[], description="Paragraphs (structural elements) of the legal act that reference this attribute")

class StartAttributeSuggestionsTopKExtractionJobRequest(BaseModel):
    """Represents a request to start a job for extracting top-K attribute suggestions from the given set of paragraphs (structural elements) of a legal act for the given class."""
    k: Optional[int] = Field(default=10, description="Maximal number of top attribute suggestions to extract in this job (default: 10)")
    selected_class_id: str = Field(description="Applicaiton ID of the class for which to extract attribute suggestions")
    structural_element_ids: Optional[List[str]] = Field(default=None, description="List of paragraph numbers (structural element IDs) to process by the attribute extraction job. If not provided, all elements will be processed. However, take into account that processing the whole legal act can exceed the tokens-per-minute limit given by the LLM API provider which would lead to job rejection.")
    context_text: Optional[str] = Field(default=None, description="Additional context text specified by the user to focus suggestion extraction. Be careful in using this as it is not guaranteed to be used in the extraction process. Also take into account that it can break the extraction process if specified maliciously. The job can also be rejected completely if the context is found harmful.")
    known_conceptual_model: Optional[ConceptualModel] = Field(default=None, description="Existing conceptual model to consider during the extraction job. The extraction job will try to generate new suggestions that are semantically close to the existing model and it will try to avoid generating suggestions that are already present in the existing model. If not provided, the job will generate suggestions without considering any existing model.")

class StartAttributeSuggestionsJobResponse(BaseModel):
    """Represents a response to a request for starting an attribute suggestions extraction job."""
    job_id: UUID = Field(description="Unique identifier for the started job")
    selected_class_id: UUID = Field(description="Unique identifier of the class for which the attribute suggestions are being extracted")
    status: Optional[str] = Field(default=None, description="Current status of the job ('in_progress', 'completed', 'failed')")

class AttributeSuggestionsJobStatusResponse(BaseModel):
    """Represents the status of an attribute suggestions extraction job with the list of already extracted attribute suggestions."""
    job_id: UUID = Field(description="Unique identifier of the job")
    selected_class_id: UUID = Field(description="Unique identifier of the class for which the attribute suggestions are being extracted")
    status: UUID = Field(description="Current status of the job ('in_progress', 'completed', 'failed')")
    new_suggestions: List[AttributeSuggestion] = Field(default=[], description="List of extracted attribute suggestions since the start of the job. After 'completed' status is set, this list contains all suggestions generated by the job. No more suggestions will be added after the job is completed.")