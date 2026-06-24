from fastapi import APIRouter, HTTPException, Request, status, Path
from application.services.property_suggestions import PropertySuggestionService
from application.services.authentication import get_authenticated_user_id
from application.services.token_rate_limiter import DailyTokenLimitExceeded
from uuid import UUID

from api.schemas.property_suggestion import (
    StartPropertySuggestionsTopKExtractionJobRequest,
    StartPropertySuggestionsJobResponse,
    PropertySuggestionsJobStatusResponse
)
from api.schemas.relationship_suggestion import (
    StartRelationshipSuggestionsTopKExtractionJobRequest,
    StartRelationshipSuggestionsJobResponse,
    RelationshipSuggestionsJobStatusResponse,
)
from api.mappers.suggestions import (
    _translate_api_conceptual_model_to_domain_conceptual_model,
    _translate_domain_to_api_attribute_suggestion,
    _translate_domain_to_api_relationship_suggestion
)

def get_property_suggestion_router(service: PropertySuggestionService) -> APIRouter:
    router = APIRouter()

    @router.post(
        "/legal-acts/{year}/{number}/{date}/property-suggestions-top-k-extraction-jobs",
        status_code=status.HTTP_202_ACCEPTED,
        response_model=StartPropertySuggestionsJobResponse,
        summary="Start a top-K property (attribute and relationship) suggestions extraction job",
        description="Starts a job to extract the top-K property (attribute and relationships) suggestions for specific structural elements of a legal act and for a specific class. After the job is started, it will run asynchronously and can be checked for status using the job ID."
    )
    async def start_property_suggestions_top_k_extraction_job(
        request: StartPropertySuggestionsTopKExtractionJobRequest,
        http_request: Request,
        number: int = Path(..., description="Official number of the legal act"),
        year: int = Path(..., description="Year of the legal act"),
        date: str = Path(..., description="Date identifying the version of the legal act (YYYY-MM-DD)")
    ) -> StartPropertySuggestionsJobResponse:
        try:
            job = await service.start_property_suggestions_top_k_extraction_job(
                number, year, date,
                request.k or 10,
                request.structural_element_ids,
                str(request.selected_class_id),
                request.context_text,
                _translate_api_conceptual_model_to_domain_conceptual_model(request.known_conceptual_model),
                user_id=get_authenticated_user_id(http_request)
            )
        except DailyTokenLimitExceeded as exc:
            raise HTTPException(status_code=status.HTTP_429_TOO_MANY_REQUESTS, detail=str(exc))
        return StartPropertySuggestionsJobResponse(
            job_id=job.job_id,
            selected_class_id=job.selected_class_id,
            status=job.status
        )

    @router.get(
        "/legal-acts/{year}/{number}/{date}/property-suggestions-jobs/{job_id}",
        response_model=PropertySuggestionsJobStatusResponse,
        summary="Get property (attribute and relationship) suggestion job status",
        description="Get the status of a property (attribute and relationship) suggestions job by job ID received when starting the job. The status contains the status information about the job and the list of new property (attribute and relationship) suggestions extracted since the job was started. After the job is completed, no more suggestions will be added to this list."
    )
    def get_property_suggestion_job_status(
        http_request: Request,
        number: int = Path(..., description="Official number of the legal act"),
        year: int = Path(..., description="Year of the legal act"),
        date: str = Path(..., description="Date identifying the version of the legal act (YYYY-MM-DD)"),
        job_id: UUID = Path(..., description="Job ID")
    ) -> PropertySuggestionsJobStatusResponse:
        """
        Get the status of the given property (attribute and relationship) suggestions job.
        """
        legal_act_key = f"https://opendata.eselpoint.cz/esel-esb/eli/cz/sb/{year}/{number}/{date}"
        job = service.get_job_status(
            job_id,
            user_id=get_authenticated_user_id(http_request),
            legal_act_key=legal_act_key,
        )
        if job is None:
            raise HTTPException(status_code=404, detail="Job not found")
        attr_suggestions = [
            _translate_domain_to_api_attribute_suggestion(s) for s in (job.attribute_suggestions or [])
        ]
        rel_suggestions = [
            _translate_domain_to_api_relationship_suggestion(s) for s in (job.relationship_suggestions or [])
        ]
        return PropertySuggestionsJobStatusResponse(
            job_id=job.job_id,
            selected_class_id=job.selected_class_id,
            status=job.status,
            new_attribute_suggestions=attr_suggestions,
            new_relationship_suggestions=rel_suggestions
        )

    @router.post(
        "/legal-acts/{year}/{number}/{date}/relationship-suggestions-top-k-extraction-jobs",
        status_code=status.HTTP_202_ACCEPTED,
        response_model=StartRelationshipSuggestionsJobResponse,
        summary="Start a top-K relationship suggestions extraction job",
        description="Starts a job to extract top-K relationship suggestions for specific structural elements of a legal act and for a specific class. After the job is started, it will run asynchronously and can be checked for status using the job ID."
    )
    async def start_relationship_suggestions_top_k_extraction_job(
        request: StartRelationshipSuggestionsTopKExtractionJobRequest,
        http_request: Request,
        number: int = Path(..., description="Official number of the legal act"),
        year: int = Path(..., description="Year of the legal act"),
        date: str = Path(..., description="Date identifying the version of the legal act (YYYY-MM-DD)")
    ) -> StartRelationshipSuggestionsJobResponse:
        try:
            job = await service.start_property_suggestions_top_k_extraction_job(
                number, year, date,
                request.k or 10,
                request.structural_element_ids,
                str(request.selected_class_id),
                request.context_text,
                _translate_api_conceptual_model_to_domain_conceptual_model(request.known_conceptual_model),
                user_id=get_authenticated_user_id(http_request)
            )
        except DailyTokenLimitExceeded as exc:
            raise HTTPException(status_code=status.HTTP_429_TOO_MANY_REQUESTS, detail=str(exc))
        return StartRelationshipSuggestionsJobResponse(
            job_id=job.job_id,
            selected_class_id=job.selected_class_id,
            status=job.status
        )

    @router.get(
        "/legal-acts/{year}/{number}/{date}/relationship-suggestions-jobs/{job_id}",
        response_model=RelationshipSuggestionsJobStatusResponse,
        summary="Get relationship suggestion job status",
        description="Get the status of a relationship suggestions job by job ID received when starting the job. The status contains the status information about the job and the list of new relationship suggestions extracted since the job was started. After the job is completed, no more suggestions will be added to this list."
    )
    def get_relationship_suggestion_job_status(
        http_request: Request,
        number: int = Path(..., description="Official number of the legal act"),
        year: int = Path(..., description="Year of the legal act"),
        date: str = Path(..., description="Date identifying the version of the legal act (YYYY-MM-DD)"),
        job_id: UUID = Path(..., description="Job ID")
    ) -> RelationshipSuggestionsJobStatusResponse:
        legal_act_key = f"https://opendata.eselpoint.cz/esel-esb/eli/cz/sb/{year}/{number}/{date}"
        job = service.get_job_status(
            job_id,
            user_id=get_authenticated_user_id(http_request),
            legal_act_key=legal_act_key,
        )
        if job is None:
            raise HTTPException(status_code=404, detail="Job not found")
        rel_suggestions = [
            _translate_domain_to_api_relationship_suggestion(s) for s in (job.relationship_suggestions or [])
        ]
        return RelationshipSuggestionsJobStatusResponse(
            job_id=job.job_id,
            selected_class_id=job.selected_class_id,
            status=job.status,
            new_relationship_suggestions=rel_suggestions
        )

    return router
