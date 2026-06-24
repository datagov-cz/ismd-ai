import json
import os
from typing import Optional

from dotenv import load_dotenv
from fastapi import Depends, FastAPI, Header, Request
from fastapi.openapi.utils import get_openapi
from fastapi.staticfiles import StaticFiles

from api.routers.accepted_suggestions import get_accepted_suggestion_router
from api.routers.class_suggestions import get_class_suggestion_router
from api.routers.disliked_suggestions import get_disliked_suggestion_router
from api.routers.liked_suggestions import get_liked_suggestion_router
from api.routers.property_suggestions import get_property_suggestion_router
from api.routers.token_usage import get_token_usage_router
from application.executors.class_suggestions import ClassSuggestionExecutor
from application.executors.property_suggestions import PropertySuggestionExecutor
from application.services.accepted_suggestions import AcceptedSuggestionService
from application.services.authentication import AuthenticationService
from application.services.class_suggestions import ClassSuggestionService
from application.services.disliked_suggestions import DislikedSuggestionService
from application.services.liked_suggestions import LikedSuggestionService
from application.services.property_suggestions import PropertySuggestionService
from application.services.token_rate_limiter import DailyTokenRateLimiter
from infrastructure.llm.generators.any_llm import SuggestionGenerator_AnyLLM
from infrastructure.llm.prompts.methodology_english import (
    PromptConstructor_Methodology_PromptsInEnglish,
)
from infrastructure.persistence.evaluations.suggestion_evaluations import (
    SuggestionEvaluationRepository,
)
from infrastructure.persistence.jobs.jsonl_suggestion_job_repository import (
    JsonLineSuggestionJobRepository,
)
from infrastructure.persistence.suggestions.file_system_suggestion_repository import (
    FileSystemSuggestionRepository,
)


def create_app() -> FastAPI:
    load_dotenv()

    data_directory = os.getenv("DATA_DIRECTORY", "data")
    static_directory = os.getenv("STATIC_DIRECTORY")
    token_rate_limiter = DailyTokenRateLimiter.from_environment(data_directory)
    authentication_service = _build_authentication_service()

    def authenticate_request(
        request: Request,
        user_id: Optional[str] = Header(None),
        password: Optional[str] = Header(None),
        authorization: Optional[str] = Header(None),
    ):
        return authentication_service.authenticate(request, user_id, password, authorization)

    app = FastAPI(dependencies=[Depends(authenticate_request)])

    llm_provider = os.getenv("LLM_PROVIDER", "openai")
    llm_model = os.getenv("LLM_MODEL", "gpt-5.5")
    routers = _build_methodology_prompt_routers(
        model=llm_model,
        provider=llm_provider,
        data_directory=data_directory,
        token_rate_limiter=token_rate_limiter,
    )

    for router in routers:
        app.include_router(router)
    app.include_router(get_token_usage_router(token_rate_limiter))

    _configure_openapi_security(app)

    if static_directory:
        print("INFO:     Serving frontend files.")
        app.mount("/", StaticFiles(directory=static_directory, html=True))

    return app


def _build_authentication_service() -> AuthenticationService:
    user_keys_json = os.getenv("USER_KEYS")
    if not user_keys_json:
        print("WARNING:  USER_KEYS not set in environment")
        user_keys_list = []
    else:
        try:
            user_keys_list = json.loads(user_keys_json)
        except Exception:
            print("WARNING:  USER_KEYS is not valid")
            user_keys_list = []

    oidc_user_id_claims = [
        claim.strip()
        for claim in os.getenv("OIDC_USER_ID_CLAIMS", "sub,preferred_username").split(",")
        if claim.strip()
    ]
    return AuthenticationService.from_environment(user_keys_list, oidc_user_id_claims)


def _build_methodology_prompt_routers(
    model: str,
    provider: str,
    data_directory: str,
    token_rate_limiter: DailyTokenRateLimiter,
):
    llm_identifier = f"{provider}/{model}"
    job_repo = JsonLineSuggestionJobRepository(
        f"{data_directory}/logs/{llm_identifier}/methodology_prompts_in_english_suggestion_jobs.jsonl"
    )
    suggestion_repo = FileSystemSuggestionRepository(
        storage_folder=f"{data_directory}/global_class_suggestions/{llm_identifier}/v2/long"
    )
    prompt_constructor = PromptConstructor_Methodology_PromptsInEnglish()
    generator = SuggestionGenerator_AnyLLM(
        prompt_constructor,
        model=model,
        provider=provider,
        language="cs",
        token_rate_limiter=token_rate_limiter,
    )

    class_executor = ClassSuggestionExecutor(generator)
    class_service = ClassSuggestionService(
        job_repo,
        suggestion_repo,
        class_executor,
        token_rate_limiter,
    )
    class_router = get_class_suggestion_router(class_service)

    property_executor = PropertySuggestionExecutor(generator)
    property_service = PropertySuggestionService(
        job_repo,
        suggestion_repo,
        property_executor,
        token_rate_limiter,
    )
    property_router = get_property_suggestion_router(property_service)

    accepted_repo = SuggestionEvaluationRepository(
        f"{data_directory}/logs/{llm_identifier}/methodology_prompts_in_english_accepted_suggestions.jsonl"
    )
    accepted_service = AcceptedSuggestionService(accepted_repo, job_repo)
    liked_service = LikedSuggestionService(accepted_repo, job_repo)
    disliked_service = DislikedSuggestionService(accepted_repo, job_repo)

    return (
        class_router,
        property_router,
        get_accepted_suggestion_router(accepted_service),
        get_liked_suggestion_router(liked_service),
        get_disliked_suggestion_router(disliked_service),
    )


def _configure_openapi_security(app: FastAPI) -> None:
    def custom_openapi():
        if app.openapi_schema:
            return app.openapi_schema
        openapi_schema = get_openapi(
            title=app.title,
            version="0.1.0",
            routes=app.routes,
        )
        components = openapi_schema.setdefault("components", {})
        security_schemes = components.setdefault("securitySchemes", {})
        security_schemes["LegacyUserId"] = {
            "type": "apiKey",
            "in": "header",
            "name": "user-id",
            "description": "Legacy static credential user identifier. Must be sent together with the password header.",
        }
        security_schemes["LegacyUserPassword"] = {
            "type": "apiKey",
            "in": "header",
            "name": "password",
            "description": "Legacy static credential. Must be sent together with the user-id header.",
        }
        security_schemes["OIDCBearer"] = {
            "type": "http",
            "scheme": "bearer",
            "bearerFormat": "JWT",
        }
        openapi_schema["security"] = [
            {"LegacyUserId": [], "LegacyUserPassword": []},
            {"OIDCBearer": []},
        ]
        app.openapi_schema = openapi_schema
        return app.openapi_schema

    app.openapi = custom_openapi
