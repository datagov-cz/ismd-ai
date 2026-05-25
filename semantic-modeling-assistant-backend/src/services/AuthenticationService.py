import base64
import json
from typing import Optional

from fastapi import HTTPException, Request, status


class AuthenticationService:
    def __init__(self, user_keys: list[dict], oidc_user_id_claims: list[str]):
        self.user_keys = user_keys
        self.oidc_user_id_claims = oidc_user_id_claims

    def authenticate(
        self,
        request: Request,
        user_id: Optional[str],
        password: Optional[str],
        authorization: Optional[str],
    ) -> str:
        legacy_user_id = self._authenticate_legacy_credentials(user_id, password)
        if legacy_user_id:
            request.state.authenticated_user_id = legacy_user_id
            return legacy_user_id

        oidc_user_id = self._extract_oidc_user_id(authorization)
        if oidc_user_id:
            request.state.authenticated_user_id = oidc_user_id
            return oidc_user_id

        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Invalid credentials.")

    def _authenticate_legacy_credentials(
        self,
        user_id: Optional[str],
        password: Optional[str],
    ) -> Optional[str]:
        if not user_id or not password:
            return None

        for user in self.user_keys:
            if user.get("user_id") == user_id and (
                user.get("password") == password or user.get("key") == password
            ):
                return user_id
        return None

    def _extract_oidc_user_id(self, authorization: Optional[str]) -> Optional[str]:
        if not authorization:
            return None

        scheme, _, token = authorization.partition(" ")
        if scheme.lower() != "bearer" or not token:
            return None

        claims = self._decode_unverified_jwt_claims(token)
        for claim_name in self.oidc_user_id_claims:
            claim_value = claims.get(claim_name)
            if isinstance(claim_value, str) and claim_value.strip():
                return claim_value
        return None

    def _decode_unverified_jwt_claims(self, token: str) -> dict:
        parts = token.split(".")
        if len(parts) < 2:
            return {}

        payload = parts[1]
        payload += "=" * (-len(payload) % 4)
        try:
            decoded = base64.urlsafe_b64decode(payload.encode("ascii"))
            claims = json.loads(decoded.decode("utf-8"))
        except Exception:
            return {}

        return claims if isinstance(claims, dict) else {}


def get_authenticated_user_id(request: Request) -> str:
    user_id = getattr(request.state, "authenticated_user_id", None)
    if not user_id:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Invalid credentials.")
    return user_id
