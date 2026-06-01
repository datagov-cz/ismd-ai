from pydantic import BaseModel, Field
from typing import Optional, List
from uuid import UUID
from .legal_model import LegalAct, LegalStructuralElement
from .lang_model import LangString
from .conceptual_model import ConceptualModel

class LocalClassSuggestion(BaseModel):
    """Represents a local class suggestion extracted from a specific part of a legal act."""
    id: str = Field(description="Unique identifier for the local class suggestion for which a class suggestion was made")
    type: str = Field(description="Type of the local class suggestion. It is reserved for Linked Data publication, not used for any application logic.")
    legal_act_part: LegalStructuralElement = Field(description="The specific part of the legal act where this local class suggestion is localized and where it was identified")

class ClassSuggestionReference(BaseModel):
    """Represents a reference to a class suggestion."""
    id: str = Field(description="Unique identifier of the referenced class suggestion")

class ClassSuggestion(BaseModel):
    """Represents a suggested class extracted from the legal act based on one or more local occurrences in the act.
    
    This class encapsulates information about classes that can be suggested for a conceptual model, including multilingual names and definitions, and local occurences from where the class suggestion was extracted."""
    id: str = Field(description="Unique identifier for the class suggestion")
    type: str = Field(description="Type of the suggested class. It is reserved for Linked Data publication, not used for any application logic.")
    name: LangString = Field(description="Multilingual name of the suggested class")
    definition: Optional[LangString] = Field(default=None, description="Multilingual definition of the suggested class")
    explanation: Optional[LangString] = Field(default=None, description="Multilingual explanation of the class semantics for human readers")
    is_subject_of_law: bool = Field(default=False, description="Whether this class represents a subject of law (e.g., person, organization)")
    is_object_of_law: bool = Field(default=False, description="Whether this class represents an object of law (e.g., property, asset)")
    is_event: bool = Field(default=False, description="Whether this class represents an event or process")
    is_document: bool = Field(default=False, description="Whether this class represents a document")
    specializes: Optional[List[ClassSuggestionReference]] = Field(default=None, description="List of references to the parent classes that this class specializes")
    legal_act: LegalAct = Field(description="Legal act from which this suggestion was extracted")
    occurrences: Optional[List[LocalClassSuggestion]] = Field(default=[], description="Specific occurrences of this class within the legal act")
    references: Optional[List[LegalStructuralElement]] = Field(default=[], description="Paragraphs (structural elements) of the legal act that reference this class")

class StartClassSuggestionsTopKExtractionJobRequest(BaseModel):
    """Represents a request to start a job for extracting top-K class suggestions from the given set of paragraphs (structural elements) of a legal act."""
    k: int = Field(default=10, description="Maximal number of top class suggestions to extract in this job (default: 10)")
    structural_element_ids: Optional[List[str]] = Field(default=None, description="List of paragraph numbers (structural element IDs) to process by the class extraction job. If not provided, the whole legal act will be processed. However, take into account that processing the whole legal act can exceed the tokens-per-minute limit given by the LLM API provider which would lead to job rejection.")
    context_text: Optional[str] = Field(default=None, description="Additional context text specified by the user to focus suggestion extraction. Be careful in using this as it is not guaranteed to be used in the extraction process. Also take into account that it can break the extraction process if specified maliciously. The job can also be rejected completely if the context is found harmful.")
    known_conceptual_model: Optional[ConceptualModel] = Field(default=None, description="Existing conceptual model to consider during the extraction job. The extraction job will try to generate new suggestions that are semantically close to the existing model and it will try to avoid generating suggestions that are already present in the existing model. If not provided, the job will generate suggestions without considering any existing model.")

class StartClassSuggestionsJobResponse(BaseModel):
    """Represents a response to a request for starting a class suggestions extraction job."""
    job_id: UUID = Field(description="Unique identifier for the started job")
    status: Optional[str] = Field(default=None, description="Current status of the job ('in_progress', 'completed', 'failed')")

class ClassSuggestionsJobStatusResponse(BaseModel):
    """Represents the status of a class suggestions extraction job with the list of already extracted class suggestions."""
    job_id: UUID = Field(description="Unique identifier of the job")
    status: str = Field(description="Current status of the job ('in_progress', 'completed', 'failed'")
    new_suggestions: List[ClassSuggestion] = Field(default=[], description="List of extracted class suggestions since the start of the job. After 'completed' status is set, this list contains all suggestions generated by the job. No more suggestions will be added after the job is completed.")
