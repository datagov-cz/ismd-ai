from fastapi import FastAPI, Depends, Header, HTTPException, status
from fastapi.staticfiles import StaticFiles

from controllers.ClassSuggestionController import get_class_suggestion_router
from controllers.PropertySuggestionController import get_property_suggestion_router
from controllers.AcceptedSuggestionController import get_accepted_suggestion_router
from controllers.LikedSuggestionController import get_liked_suggestion_router
from controllers.DislikedSuggestionController import get_disliked_suggestion_router
from controllers.TokenUsageController import get_token_usage_router

from infrastructure.repositories.jobs.JsonLineSuggestionJobRepository import JsonLineSuggestionJobRepository
from infrastructure.repositories.suggestions.FileSystemSuggestionRepository import FileSystemSuggestionRepository
from infrastructure.llm.SuggestionGenerator_AnyLLM import SuggestionGenerator_AnyLLM
from infrastructure.llm.prompt_constructors.PromptConstructor_Methodology_PromptsInEnglish import PromptConstructor_Methodology_PromptsInEnglish

from executors.ClassSuggestionExecutor import ClassSuggestionExecutor
from executors.PropertySuggestionExecutor import PropertySuggestionExecutor

from services.ClassSuggestionService import ClassSuggestionService
from services.PropertySuggestionService import PropertySuggestionService
from services.AcceptedSuggestionService import AcceptedSuggestionService
from services.LikedSuggestionService import LikedSuggestionService
from services.DislikedSuggestionService import DislikedSuggestionService
from services.TokenRateLimiter import DailyTokenRateLimiter
from infrastructure.repositories.accepted.AcceptedSuggestionRepository import SuggestionEvaluationRepository

import os
import json
from dotenv import load_dotenv

load_dotenv()
user_keys_json = os.getenv("USER_KEYS")
if not user_keys_json:
    print("WARNING:  USER_KEYS not set in environment")
    USER_KEYS_LIST = []
else:
    try:
        USER_KEYS_LIST = json.loads(user_keys_json)
    except Exception:
        print("WARNING:  USER_KEYS is not valid")
        USER_KEYS_LIST = []

data_directory = os.getenv("DATA_DIRECTORY", "data")
static_director = os.getenv("STATIC_DIRECTORY")
token_rate_limiter = DailyTokenRateLimiter.from_environment(data_directory)

# Dependency to check user_id and password in headers
def check_user_credentials(user_id: str = Header(...), password: str = Header(...)):
    for user in USER_KEYS_LIST:
        if user.get("user_id") == user_id and (user.get("password") == password or user.get("key") == password):
            return True
    raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Invalid credentials.")

def set_app_Methodology_PromptsInEnglish(model: str, provider: str):
  llm_identifier = f"{provider}/{model}"
  job_repo = JsonLineSuggestionJobRepository(f"{data_directory}/logs/{llm_identifier}/methodology_prompts_in_english_suggestion_jobs.jsonl")
  suggestion_repo = FileSystemSuggestionRepository(storage_folder= f"{data_directory}/global_class_suggestions/{llm_identifier}/v2/long")
  prompt_constructor = PromptConstructor_Methodology_PromptsInEnglish()
  generator = SuggestionGenerator_AnyLLM(
      prompt_constructor,
      model=model,
      provider=provider,
      language="cs",
      token_rate_limiter=token_rate_limiter,
  )

  class_executor = ClassSuggestionExecutor(generator)
  class_service = ClassSuggestionService(job_repo, suggestion_repo, class_executor, token_rate_limiter)
  class_router = get_class_suggestion_router(class_service)

  property_executor = PropertySuggestionExecutor(generator)
  property_service = PropertySuggestionService(job_repo, suggestion_repo, property_executor, token_rate_limiter)
  property_router = get_property_suggestion_router(property_service)

  accepted_repo = SuggestionEvaluationRepository(f"{data_directory}/logs/{llm_identifier}/methodology_prompts_in_english_accepted_suggestions.jsonl")
  accepted_service = AcceptedSuggestionService(accepted_repo)
  liked_service = LikedSuggestionService(accepted_repo)
  disliked_service = DislikedSuggestionService(accepted_repo)
  accepted_router = get_accepted_suggestion_router(accepted_service)
  liked_router = get_liked_suggestion_router(liked_service)
  disliked_router = get_disliked_suggestion_router(disliked_service)
  return (
      class_router,
      property_router,
      accepted_router,
      liked_router,
      disliked_router,
  )

app = FastAPI(dependencies=[Depends(check_user_credentials)])

llm_provider = os.getenv("LLM_PROVIDER", "openai")
llm_model = os.getenv("LLM_MODEL", "gpt-4.1")

#(class_router, attribute_router, relationship_router, property_router) = set_app_Methodology_PromptsInEnglish("gpt-4.1-mini", "openai")
(class_router,
 property_router,
 accepted_router,
 liked_router,
 disliked_router,) = set_app_Methodology_PromptsInEnglish(llm_model, llm_provider)

app.include_router(class_router)
app.include_router(property_router)
app.include_router(accepted_router)
app.include_router(liked_router)
app.include_router(disliked_router)
app.include_router(get_token_usage_router(token_rate_limiter))

if static_director:
    print("INFO:     Serving frontend files.")
    app.mount("/", StaticFiles(directory=static_director, html=True))
