import os
import json
from uuid import UUID
from datetime import datetime
from filelock import FileLock

class SuggestionEvaluationRepository:
    def __init__(self, filepath: str):
        self.filepath = filepath
        os.makedirs(os.path.dirname(filepath), exist_ok=True)
        self.lock = FileLock(f"{filepath}.lock")

    def _record(self, job_id: UUID, suggestion_id: str, user_id: str, event: str) -> None:
        entry = {
            "event": event,
            "timestamp": datetime.utcnow().isoformat(),
            "user_id": user_id,
            "job_id": str(job_id),
            "suggestion_id": suggestion_id,
        }
        with self.lock:
            with open(self.filepath, "a", encoding="utf-8") as f:
                f.write(json.dumps(entry) + "\n")

    def record_suggestion_acceptance(self, job_id: UUID, suggestion_id: str, user_id: str) -> None:
        self._record(job_id, suggestion_id, user_id, "accepted")

    def record_suggestion_like(self, job_id: UUID, suggestion_id: str, user_id: str) -> None:
        self._record(job_id, suggestion_id, user_id, "liked")

    def record_suggestion_dislike(self, job_id: UUID, suggestion_id: str, user_id: str) -> None:
        self._record(job_id, suggestion_id, user_id, "disliked")
