from fastapi import APIRouter, HTTPException, Request, status
from application.services.liked_suggestions import LikedSuggestionService
from application.services.authentication import get_authenticated_user_id
from api.schemas.accepted_suggestion import AcceptSuggestionRequest


def get_liked_suggestion_router(service: LikedSuggestionService) -> APIRouter:
    router = APIRouter()

    @router.post(
            "/liked-suggestions",
            status_code=status.HTTP_204_NO_CONTENT,
            summary="Record liked suggestion",
            description="Records the like of a suggestion for a specific job. This is used to track which suggestions have been liked by the user.")
    async def record_like(request: AcceptSuggestionRequest, http_request: Request):
        try:
            service.like(
                request.job_id,
                request.suggestion_id,
                get_authenticated_user_id(http_request),
            )
        except LookupError as exc:
            raise HTTPException(status_code=404, detail=str(exc))
        return None

    return router
