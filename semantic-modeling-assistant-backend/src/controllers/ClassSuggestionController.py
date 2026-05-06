from fastapi import APIRouter, HTTPException, status, Path
from services.ClassSuggestionService import ClassSuggestionService
from uuid import UUID
from typing import List

from model.api.class_suggestion import (
    StartClassSuggestionsJobResponse,
    StartClassSuggestionsTopKExtractionJobRequest,
    ClassSuggestionsJobStatusResponse
)

from controllers._utils import (
    _translate_api_conceptual_model_to_domain_conceptual_model,
    _translate_domain_to_api_class_suggestion
)


def get_class_suggestion_router(service: ClassSuggestionService) -> APIRouter:
    router = APIRouter()
    
    @router.post(
        "/legal-acts/{year}/{number}/{date}/class-suggestions-top-k-extraction-jobs",
        status_code=status.HTTP_202_ACCEPTED,
        response_model=StartClassSuggestionsJobResponse,
        summary="Start a new top-K class suggestions extraction job",
        description="Starts a new job to extract the top-K class suggestions for specific paragraphs (structural elements) of the given legal act. After the job is started, it will run asynchronously and can be checked for status using the job ID."
    )
    async def start_class_suggestions_top_k_extraction_job(
        request: StartClassSuggestionsTopKExtractionJobRequest,
        number: int = Path(..., description="Official number of the legal act"),
        year: int = Path(..., description="Year of the legal act"),
        date: str = Path(..., description="Date identifying the version of the legal act (YYYY-MM-DD)")
    ) -> StartClassSuggestionsJobResponse:
        job = await service.start_class_suggestions_top_k_extraction_job(
            number, year, date,
            request.k,
            request.structural_element_ids,
            request.context_text,
            _translate_api_conceptual_model_to_domain_conceptual_model(request.known_conceptual_model)
        )
        return StartClassSuggestionsJobResponse(
            job_id=job.job_id,
            status=job.status
        )

    @router.get(
        "/legal-acts/{year}/{number}/{date}/class-suggestions-jobs/{job_id}",
        response_model=ClassSuggestionsJobStatusResponse,
        summary="Get job status",
        description="Get the status of a class suggestions job by job ID received when starting the job.The status contains the status information about the job and the list of new class suggestions extracted since the job was started. After the job is completed, no more suggestions will be added to this list."
    )
    def get_class_suggestion_job_status(
        number: int = Path(..., description="Official number of the legal act"),
        year: int = Path(..., description="Year of the legal act"),
        date: str = Path(..., description="Date identifying the version of the legal act (YYYY-MM-DD)"),
        job_id: UUID = Path(..., description="Unique identifier of the job")
    ) -> ClassSuggestionsJobStatusResponse:
        """
        Get the status of the given class suggestions job.
        """
        job = service.get_job_status(job_id)
        if job is None:
            raise HTTPException(status_code=404, detail="Job not found")
        suggestions = [_translate_domain_to_api_class_suggestion(s) for s in (job.suggestions or [])]
        return ClassSuggestionsJobStatusResponse(
            job_id=job.job_id,
            status=job.status,
            new_suggestions=suggestions
        )

    return router