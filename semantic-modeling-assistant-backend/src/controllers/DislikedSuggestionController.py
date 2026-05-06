from fastapi import APIRouter, status
from services.DislikedSuggestionService import DislikedSuggestionService
from model.api.accepted_suggestion import AcceptSuggestionRequest


def get_disliked_suggestion_router(service: DislikedSuggestionService) -> APIRouter:
    router = APIRouter()

    @router.post(
            "/disliked-suggestions",
            status_code=status.HTTP_204_NO_CONTENT,
            summary="Record disliked suggestion",
            description="Records the dislike of a suggestion for a specific job. This is used to track which suggestions have been disliked by the user.")
    async def record_dislike(request: AcceptSuggestionRequest):
        service.dislike(request.job_id, request.suggestion_id)
        return None

    return router
