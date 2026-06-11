from fastapi import APIRouter, HTTPException, Request, status
from services.AcceptedSuggestionService import AcceptedSuggestionService
from services.AuthenticationService import get_authenticated_user_id
from model.api.accepted_suggestion import AcceptSuggestionRequest


def get_accepted_suggestion_router(service: AcceptedSuggestionService) -> APIRouter:
    router = APIRouter()

    @router.post(
            "/accepted-suggestions",
            status_code=status.HTTP_204_NO_CONTENT,
            summary="Record accepted suggestion",
            description="Records the acceptance of a suggestion for a specific job. This is used to track which suggestions have been accepted by the user.")
    async def record_acceptance(request: AcceptSuggestionRequest, http_request: Request):
        try:
            service.accept(
                request.job_id,
                request.suggestion_id,
                get_authenticated_user_id(http_request),
            )
        except LookupError as exc:
            raise HTTPException(status_code=404, detail=str(exc))
        return None

    return router
