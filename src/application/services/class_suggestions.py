from uuid import uuid4, UUID
from typing import Dict, List, Optional, cast
from infrastructure.persistence.jobs.suggestion_job_repository import SuggestionJobRepositoryPort
from infrastructure.persistence.suggestions.suggestion_repository import SuggestionRepositoryPort
from infrastructure.legal_acts.esel_repository import LegalActRepositoryESEL
from infrastructure.persistence.jobs.suggestion_job import ClassSuggestionsJob
from application.executors.class_suggestions import ClassSuggestionExecutor
from domain.models.suggestion_model import LegalAct
from api.schemas.class_suggestion import ClassSuggestion
from domain.models.conceptual_model import ConceptualModel
from application.services.token_rate_limiter import DailyTokenRateLimiter
from application.services.legal_text_selection import _select_structural_elements
import asyncio
import traceback

class ClassSuggestionService:
    def __init__(
            self,
            job_repo: SuggestionJobRepositoryPort,
            suggestion_repo: SuggestionRepositoryPort,
            executor: ClassSuggestionExecutor,
            token_rate_limiter: DailyTokenRateLimiter):
        self.job_repo = job_repo
        self.suggestion_repo = suggestion_repo
        self.executor = executor
        self.token_rate_limiter = token_rate_limiter
        self.legal_act_repo = LegalActRepositoryESEL()

    async def start_class_suggestions_top_k_extraction_job(
            self,
            number: int,
            year: int,
            date: str,
            k: int,
            structural_element_ids: Optional[List[str]],
            context_text: Optional[str] = None,
            known_conceptual_model: Optional[ConceptualModel] = None,
            user_id: str = ""
        ) -> ClassSuggestionsJob:
        self.token_rate_limiter.ensure_available(user_id)
        key = f"https://opendata.eselpoint.cz/esel-esb/eli/cz/sb/{year}/{number}/{date}"
        job_id = uuid4()
        job = ClassSuggestionsJob(
            job_id=job_id,
            owner_user_id=user_id,
            legal_act_key=key,
            k=k,
            structural_element_ids=structural_element_ids,
            context_text=context_text,
            known_conceptual_model=known_conceptual_model,
            status="in_progress",
            suggestions=[]
        )
        job.start()
        self.job_repo.update(job)
        legal_act = await self._get_legal_act(number, year, date)
        asyncio.create_task(self._execute_class_suggestions_top_k_extraction_job(job, legal_act, k, structural_element_ids, context_text, known_conceptual_model, user_id))
        return job
        
    async def _execute_class_suggestions_top_k_extraction_job(
            self,
            job: ClassSuggestionsJob,
            legal_act: LegalAct,
            k: int,
            structural_element_ids: Optional[List[str]],
            context_text: Optional[str],
            known_conceptual_model: Optional[ConceptualModel] = None,
            user_id: Optional[str] = None):
        try:
            # Get selected elements
            selected_elements = _select_structural_elements(legal_act=legal_act, structural_element_ids=structural_element_ids)
            async for suggestion in self.executor.stream_top_k_global_class_suggestions(legal_act, k, selected_elements, context_text, known_conceptual_model, user_id):
                job.suggestions.append(suggestion)
            job.status = "completed"
            job.end()
            self.job_repo.save(job)
            print(f"class_suggestions_top_k_extraction_job {job.job_id} completed successfully.")
        except Exception as e:
            job.status = "failed"
            job.end()
            self.job_repo.save(job)
            print(f"Error class_suggestions_top_k_extraction_job {job.job_id}: {e}")
            print("Traceback details:")
            traceback.print_exc()
    
    def get_job_status(
            self,
            job_id: UUID,
            user_id: str,
            legal_act_key: Optional[str] = None) -> Optional[ClassSuggestionsJob]:
        job_base = self.job_repo.get(job_id)
        if job_base is None:
            return None
        if job_base.owner_user_id != user_id:
            return None
        if legal_act_key is not None and job_base.legal_act_key != legal_act_key:
            return None
        job = cast(ClassSuggestionsJob, job_base)
        return job
    
    async def _get_legal_act(
            self,
            number: int,
            year: int,
            date: str) -> LegalAct:
        legal_act_key = f"https://opendata.eselpoint.cz/esel-esb/eli/cz/sb/{year}/{number}/{date}"
        return self.legal_act_repo.load_legal_act(legal_act_key)
