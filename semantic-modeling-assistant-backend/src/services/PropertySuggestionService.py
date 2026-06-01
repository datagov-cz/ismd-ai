from uuid import uuid4, UUID
from typing import List, Optional, cast
from infrastructure.repositories.jobs.SuggestionJobRepositoryPort import SuggestionJobRepositoryPort
from infrastructure.repositories.suggestions.SuggestionRepositoryPort import SuggestionRepositoryPort
from infrastructure.repositories.legal_acts.LegalActRepositoryESEL import LegalActRepositoryESEL
from infrastructure.repositories.jobs.SuggestionJob import PropertySuggestionJob
from executors.PropertySuggestionExecutor import PropertySuggestionExecutor
from model.domain.suggestion_model import GlobalAttributeSuggestion, GlobalRelationshipSuggestion, LegalAct
from model.domain.conceptual_model import ConceptualModel, Class
from services.TokenRateLimiter import DailyTokenRateLimiter
from services._utils import _select_structural_elements

import asyncio
import traceback

class PropertySuggestionService:
    def __init__(
            self,
            job_repo: SuggestionJobRepositoryPort,
            suggestion_repo: SuggestionRepositoryPort,
            executor: PropertySuggestionExecutor,
            token_rate_limiter: DailyTokenRateLimiter):
        self.job_repo = job_repo
        self.suggestion_repo = suggestion_repo
        self.executor = executor
        self.token_rate_limiter = token_rate_limiter
        self.legal_act_repo = LegalActRepositoryESEL()

    async def _get_legal_act(
            self,
            number: int,
            year: int,
            date: str) -> LegalAct:
        legal_act_key = f"https://opendata.eselpoint.cz/esel-esb/eli/cz/sb/{year}/{number}/{date}"
        return self.legal_act_repo.load_legal_act(legal_act_key)

    def get_job_status(
            self,
            job_id: UUID) -> PropertySuggestionJob:
        job_base = self.job_repo.get(job_id)
        job = cast(PropertySuggestionJob, job_base)
        return job

    async def start_property_suggestions_top_k_extraction_job(
            self,
            number: int,
            year: int,
            date: str,
            k: int,
            structural_element_ids: Optional[List[str]],
            selected_class_id: str,
            context_text: Optional[str] = None,
            known_conceptual_model: Optional[ConceptualModel] = None,
            user_id: Optional[str] = None
        ) -> PropertySuggestionJob:
        if user_id:
            self.token_rate_limiter.ensure_available(user_id)
        key = f"https://opendata.eselpoint.cz/esel-esb/eli/cz/sb/{year}/{number}/{date}"
        job_id = uuid4()
        job = PropertySuggestionJob(
            job_id=job_id,
            legal_act_key=key,
            k=k,
            structural_element_ids=structural_element_ids,
            context_text=context_text,
            known_conceptual_model=known_conceptual_model,
            selected_class_id=selected_class_id,
            status="in_progress",
            attribute_suggestions=[],
            relationship_suggestions=[]
        )
        job.start()
        self.job_repo.update(job)
        legal_act = await self._get_legal_act(number, year, date)

        if known_conceptual_model:
            selected_class = next((cls for cls in known_conceptual_model.classes if cls.id == selected_class_id), None)
            if not selected_class:
                raise ValueError(f"Class with ID {selected_class_id} not found in the provided conceptual model.")
        else:
            raise ValueError("Known conceptual model with at least the selected class is required to generate attribute suggestions.")

        asyncio.create_task(
            self._execute_property_suggestions_top_k_extraction_job(
                job,
                legal_act,
                k,
                structural_element_ids,
                selected_class,
                context_text,
                known_conceptual_model,
                user_id
            )
        )
        return job

    async def _execute_property_suggestions_top_k_extraction_job(
            self,
            job: PropertySuggestionJob,
            legal_act: LegalAct,
            k: int,
            structural_element_ids: Optional[List[str]],
            selected_class: Class,
            context_text: Optional[str],
            known_conceptual_model: Optional[ConceptualModel] = None,
            user_id: Optional[str] = None):
        try:
            selected_elements = _select_structural_elements(legal_act=legal_act, structural_element_ids=structural_element_ids)
            async for kind, suggestion in self.executor.stream_top_k_global_property_suggestions(
                legal_act,
                k,
                selected_elements,
                selected_class,
                context_text,
                known_conceptual_model,
                user_id
            ):
                if kind == "attribute":
                    job.attribute_suggestions.append(cast(GlobalAttributeSuggestion, suggestion))
                elif kind == "relationship":
                    job.relationship_suggestions.append(cast(GlobalRelationshipSuggestion, suggestion))
            job.status = "completed"
            job.end()
            self.job_repo.save(job)
        except Exception as e:
            job.status = "failed"
            job.end()
            self.job_repo.save(job)
            print(f"Error property_suggestions_top_k_extraction_job {job.job_id}: {e}")
            traceback.print_exc()
