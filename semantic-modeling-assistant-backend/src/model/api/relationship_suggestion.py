from pydantic import BaseModel, Field
from typing import List, Optional
from uuid import UUID
from .legal_model import LegalAct, LegalStructuralElement
from .lang_model import LangString
from .conceptual_model import ConceptualModel

class LocalRelationshipSuggestion(BaseModel):
    """Represents a local relationship suggestion extracted from a specific part of a legal act."""
    id: str = Field(description="Unique identifier for the local relationship suggestion for which a relationship suggestion was made")
    type: str = Field(description="Type of the local relationship suggestion. It is reserved for Linked Data publication, not used for any application logic.")
    legal_act_part: LegalStructuralElement = Field(description="The specific part of the legal act where this local relationship suggestion is localized and where it was identified")

class MediatedClassSuggestion(BaseModel):
    """Represents a suggested class that is mediated by a relationship extracted from the legal act. Compared to ClassSuggestion, this class is used to represent classes that are not directly suggested but are part of a relationship suggestion. For these mediated classes, only the name is extracted, leaving the definition and explanation empty."""
    id: str = Field(description="Unique identifier for the class suggestion")
    type: str = Field(description="Type of the suggested class. It is reserved for Linked Data publication, not used for any application logic.")
    name: LangString = Field(description="Multilingual name of the suggested class")

class RelationshipSuggestion(BaseModel):
    """Represents a suggested relationship extracted from the legal act based on one or more local occurrences in the act.

    This class encapsulates information about relationships that can be suggested for a conceptual model, including multilingual names and definitions, and local occurences from where the relationship suggestion was extracted."""
    id: str = Field(description="Unique identifier for the relationship suggestion")
    type: str = Field(description="Type of the suggested relationship. It is reserved for Linked Data publication, not used for any application logic.")
    name: LangString = Field(description="Multilingual name of the suggested relationship")
    definition: Optional[LangString] = Field(default=None, description="Multilingual definition of the suggested relationship")
    explanation: Optional[LangString] = Field(default=None, description="Multilingual explanation of the relationship semantics for human readers")
    mediatesClass: Optional[List[MediatedClassSuggestion]] = Field(default=None, description="List of classes that this relationship mediates between. Only binary relationships mediating two classes are considered in the current implementation, but this can be extended to support n-ary relationships in the future.")
    legal_act: LegalAct = Field(description="Legal act from which this suggestion was extracted")
    occurences: Optional[List[LocalRelationshipSuggestion]] = Field(default=[], description="Specific occurrences of this relationship within the legal act")
    references: Optional[List[LegalStructuralElement]] = Field(default=[], description="Paragraphs (structural elements) of the legal act that reference this relationship")

class StartRelationshipSuggestionsTopKExtractionJobRequest(BaseModel):
    """Represents a request to start a job for extracting top-K relationship suggestions from the given set of paragraphs (structural elements) of a legal act for the given class mediated by the extracted relationships. The extracted relationships mediate the given class and another class (only binary relationships are supported for now). The other class can be an existing class from the given conceptual model or a new class. If a new class is extracted as a class mediated by the relationship, the job extracts only its name, leaving the definition and extplanation of the class empty."""
    k: Optional[int] = Field(default=10, description="Maximal number of top relationship suggestions to extract in this job (default: 10)")
    selected_class_id: str = Field(description="Application ID of the class for which to extract relationship suggestions")
    structural_element_ids: Optional[List[str]] = Field(default=None, description="List of paragraph numbers (structural element IDs) to process by the relationship extraction job. If not provided, all elements will be processed. However, take into account that processing the whole legal act can exceed the tokens-per-minute limit given by the LLM API provider which would lead to job rejection.")
    context_text: Optional[str] = Field(default=None, description="Additional context text specified by the user to focus suggestion extraction. Be careful in using this as it is not guaranteed to be used in the extraction process. Also take into account that it can break the extraction process if specified maliciously. The job can also be rejected completely if the context is found harmful.")
    known_conceptual_model: Optional[ConceptualModel] = Field(default=None, description="Existing conceptual model to consider during the extraction job. The extraction job will try to generate new suggestions that are semantically close to the existing model and it will try to avoid generating suggestions that are already present in the existing model. If not provided, the job will generate suggestions without considering any existing model.")

class StartRelationshipSuggestionsJobResponse(BaseModel):
    """Represents a response to a request for starting a relationship suggestions extraction job."""
    job_id: UUID = Field(description="Unique identifier for the started job")
    selected_class_id: UUID = Field(description="Unique identifier of the class for which the relationship suggestions are being extracted")
    status: Optional[str] = Field(default=None, description="Current status of the job ('in_progress', 'completed', 'failed')")

class RelationshipSuggestionsJobStatusResponse(BaseModel):
    """Represents the status of a relationship suggestions extraction job with the list of already extracted relationship suggestions."""
    job_id: UUID = Field(description="Unique identifier of the job")
    selected_class_id: UUID = Field(description="Unique identifier of the class for which the relationship suggestions are being extracted")
    status: str = Field(description="Current status of the job ('in_progress', 'completed', 'failed')")
    new_suggestions: List[RelationshipSuggestion] = Field(default=[], description="List of extracted relationship suggestions since the start of the job. After 'completed' status is set, this list contains all suggestions generated by the job. No more suggestions will be added after the job is completed.")