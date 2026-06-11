from uuid import UUID
from infrastructure.repositories.accepted.AcceptedSuggestionRepository import SuggestionEvaluationRepository
from infrastructure.repositories.jobs.SuggestionJobRepositoryPort import SuggestionJobRepositoryPort

class AcceptedSuggestionService:
    def __init__(self, repo: SuggestionEvaluationRepository, job_repo: SuggestionJobRepositoryPort):
        self.repo = repo
        self.job_repo = job_repo

    def accept(self, job_id: UUID, suggestion_id: str, user_id: str) -> None:
        self._ensure_owned_suggestion(job_id, suggestion_id, user_id)
        self.repo.record_suggestion_acceptance(job_id, suggestion_id, user_id)

    def _ensure_owned_suggestion(self, job_id: UUID, suggestion_id: str, user_id: str) -> None:
        job = self.job_repo.get(job_id)
        if job is None or job.owner_user_id != user_id:
            raise LookupError("Job not found")

        suggestions = list(getattr(job, "suggestions", []) or [])
        suggestions.extend(getattr(job, "attribute_suggestions", []) or [])
        suggestions.extend(getattr(job, "relationship_suggestions", []) or [])
        if not any(str(getattr(suggestion, "id", "")) == suggestion_id for suggestion in suggestions):
            raise LookupError("Suggestion not found")
