from uuid import UUID
from infrastructure.repositories.accepted.AcceptedSuggestionRepository import SuggestionEvaluationRepository

class DislikedSuggestionService:
    def __init__(self, repo: SuggestionEvaluationRepository):
        self.repo = repo

    def dislike(self, job_id: UUID, suggestion_id: str) -> None:
        self.repo.record_suggestion_dislike(job_id, suggestion_id)
