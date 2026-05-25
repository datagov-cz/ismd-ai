from fastapi import APIRouter, Request

from model.api.token_usage import TokenUsageResponse
from services.AuthenticationService import get_authenticated_user_id
from services.TokenRateLimiter import DailyTokenRateLimiter


def get_token_usage_router(token_rate_limiter: DailyTokenRateLimiter) -> APIRouter:
    router = APIRouter()

    @router.get(
        "/token-usage",
        response_model=TokenUsageResponse,
        summary="Get remaining daily tokens",
        description="Returns the remaining daily LLM token budget for the authenticated user.",
    )
    def get_token_usage(request: Request) -> TokenUsageResponse:
        user_id = get_authenticated_user_id(request)
        return TokenUsageResponse(
            user_id=user_id,
            daily_token_limit=token_rate_limiter.daily_limit,
            used_tokens=token_rate_limiter.get_used_tokens(user_id),
            remaining_tokens=token_rate_limiter.get_remaining_tokens(user_id),
            rate_limit_enabled=token_rate_limiter.enabled,
        )

    return router
