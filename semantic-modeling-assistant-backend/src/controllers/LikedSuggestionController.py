from fastapi import APIRouter, status
from services.LikedSuggestionService import LikedSuggestionService
from model.api.accepted_suggestion import AcceptSuggestionRequest


def get_liked_suggestion_router(service: LikedSuggestionService) -> APIRouter:
    router = APIRouter()

    @router.post(
            "/liked-suggestions",
            status_code=status.HTTP_204_NO_CONTENT,
            summary="Record liked suggestion",
            description="Records the like of a suggestion for a specific job. This is used to track which suggestions have been liked by the user.")
    async def record_like(request: AcceptSuggestionRequest):
        service.like(request.job_id, request.suggestion_id)
        return None

    return router
