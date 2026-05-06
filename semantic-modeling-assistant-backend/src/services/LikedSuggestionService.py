from uuid import UUID
from infrastructure.repositories.accepted.AcceptedSuggestionRepository import SuggestionEvaluationRepository

class LikedSuggestionService:
    def __init__(self, repo: SuggestionEvaluationRepository):
        self.repo = repo

    def like(self, job_id: UUID, suggestion_id: str) -> None:
        self.repo.record_suggestion_like(job_id, suggestion_id)
