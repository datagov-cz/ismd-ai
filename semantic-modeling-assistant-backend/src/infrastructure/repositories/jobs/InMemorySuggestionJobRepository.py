from infrastructure.repositories.jobs.SuggestionJobRepositoryPort import SuggestionJobRepositoryPort
from infrastructure.repositories.jobs.SuggestionJob import SuggestionJob
from uuid import UUID
from typing import Dict, Optional

class InMemorySuggestionJobRepository(SuggestionJobRepositoryPort):
    def __init__(self):
        self.jobs: Dict[UUID, SuggestionJob] = {}

    def save(self, job: SuggestionJob) -> None:
        self.jobs[job.job_id] = job

    def get(self, job_id: UUID) -> Optional[SuggestionJob]:
        return self.jobs.get(job_id)

    def update(self, job: SuggestionJob) -> None:
        self.jobs[job.job_id] = job