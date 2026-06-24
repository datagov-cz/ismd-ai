import os
from typing import Optional

from fastapi import HTTPException, Request, status

try:
    import jwt
    from jwt import PyJWKClient
except ImportError:
    jwt = None
    PyJWKClient = None


class AuthenticationService:
    def __init__(
        self,
        user_keys: list[dict],
        oidc_user_id_claims: list[str],
        oidc_issuer_url: Optional[str] = None,
        oidc_audience: Optional[str] = None,
        oidc_jwks_url: Optional[str] = None,
        oidc_algorithms: Optional[list[str]] = None,
    ):
        self.user_keys = user_keys
        self.oidc_user_id_claims = oidc_user_id_claims
        self.oidc_issuer_url = oidc_issuer_url.rstrip("/") if oidc_issuer_url else None
        self.oidc_audience = oidc_audience
        self.oidc_algorithms = oidc_algorithms or ["RS256"]
        self.jwks_client = None

        if self.oidc_issuer_url and self.oidc_audience and oidc_jwks_url and PyJWKClient:
            self.jwks_client = PyJWKClient(oidc_jwks_url)

    @classmethod
    def from_environment(
        cls,
        user_keys: list[dict],
        oidc_user_id_claims: list[str],
    ) -> "AuthenticationService":
        algorithms = [
            algorithm.strip()
            for algorithm in os.getenv("OIDC_ALGORITHMS", "RS256").split(",")
            if algorithm.strip()
        ]
        return cls(
            user_keys=user_keys,
            oidc_user_id_claims=oidc_user_id_claims,
            oidc_issuer_url=os.getenv("OIDC_ISSUER_URL"),
            oidc_audience=os.getenv("OIDC_AUDIENCE"),
            oidc_jwks_url=os.getenv("OIDC_JWKS_URL"),
            oidc_algorithms=algorithms,
        )

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

        claims = self._decode_verified_jwt_claims(token)
        for claim_name in self.oidc_user_id_claims:
            claim_value = claims.get(claim_name)
            if isinstance(claim_value, str) and claim_value.strip():
                return claim_value
        return None

    def _decode_verified_jwt_claims(self, token: str) -> dict:
        if not self.jwks_client or jwt is None:
            return {}

        try:
            signing_key = self.jwks_client.get_signing_key_from_jwt(token)
            claims = jwt.decode(
                token,
                signing_key.key,
                algorithms=self.oidc_algorithms,
                audience=self.oidc_audience,
                issuer=self.oidc_issuer_url,
                options={"require": ["exp", "iat", "iss", "aud"]},
            )
        except Exception:
            return {}

        return claims if isinstance(claims, dict) else {}

def get_authenticated_user_id(request: Request) -> str:
    user_id = getattr(request.state, "authenticated_user_id", None)
    if not user_id:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Invalid credentials.")
    return user_id
