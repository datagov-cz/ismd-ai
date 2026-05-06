from uuid import UUID
from infrastructure.repositories.accepted.AcceptedSuggestionRepository import SuggestionEvaluationRepository

class AcceptedSuggestionService:
    def __init__(self, repo: SuggestionEvaluationRepository):
        self.repo = repo

    def accept(self, job_id: UUID, suggestion_id: str) -> None:
        self.repo.record_suggestion_acceptance(job_id, suggestion_id)
