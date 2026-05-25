from typing import Optional

from pydantic import BaseModel


class TokenUsageResponse(BaseModel):
    user_id: str
    daily_token_limit: int
    used_tokens: int
    remaining_tokens: Optional[int]
    rate_limit_enabled: bool
