from fastapi import APIRouter, status
from services.AcceptedSuggestionService import AcceptedSuggestionService
from model.api.accepted_suggestion import AcceptSuggestionRequest


def get_accepted_suggestion_router(service: AcceptedSuggestionService) -> APIRouter:
    router = APIRouter()

    @router.post(
            "/accepted-suggestions",
            status_code=status.HTTP_204_NO_CONTENT,
            summary="Record accepted suggestion",
            description="Records the acceptance of a suggestion for a specific job. This is used to track which suggestions have been accepted by the user.")
    async def record_acceptance(request: AcceptSuggestionRequest):
        service.accept(request.job_id, request.suggestion_id)
        return None

    return router
