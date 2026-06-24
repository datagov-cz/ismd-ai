from abc import ABC, abstractmethod
from uuid import UUID
from typing import Optional, Dict
from infrastructure.persistence.jobs.suggestion_job import SuggestionJob

class SuggestionJobRepositoryPort(ABC):
    @abstractmethod
    def save(self, job: SuggestionJob) -> None:
        pass

    @abstractmethod
    def get(self, job_id: UUID) -> Optional[SuggestionJob]:
        pass

    @abstractmethod
    def update(self, job: SuggestionJob) -> None:
        pass