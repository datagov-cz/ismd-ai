from infrastructure.persistence.jobs.suggestion_job_repository import SuggestionJobRepositoryPort
from infrastructure.persistence.jobs.suggestion_job import SuggestionJob
from uuid import UUID
from typing import Any, Dict, Optional
import json
from filelock import FileLock
import threading
from infrastructure.persistence.jobs.suggestion_job import PropertySuggestionJob

class JsonLineSuggestionJobRepository(SuggestionJobRepositoryPort):
    def __init__(self, filepath: str):
        self.jobs: Dict[UUID, SuggestionJob] = {}
        self.filepath = filepath
        self.lock = FileLock(f"{filepath}.lock")
        self.memory_lock = threading.Lock()

    def save(self, job: SuggestionJob) -> None:
        # Persist to file (thread-safe)
        with self.lock:
            with open(self.filepath, "a", encoding="utf-8") as f:
                f.write(self._prepare_job_for_logging(job) + "\n")

    def _prepare_job_for_logging(self, job: SuggestionJob) -> str:
        """
        Prepare the job object for logging. Only include basic SuggestionJob fields.
        """
        data: dict[str, Any] = {
            "job_id": str(job.job_id),
            "owner_user_id": job.owner_user_id,
            "legal_act_key": job.legal_act_key,
            "k": job.k,
            "structural_element_ids": job.structural_element_ids,
            "context_text": serialize_langstring(job.context_text),
            "started_at": job.started_at.isoformat() if job.started_at else None,
            "ended_at": job.ended_at.isoformat() if job.ended_at else None,
            "type": type(job).__name__,
        }

        # Extend with known_conceptual_model if present
        if hasattr(job, "known_conceptual_model") and job.known_conceptual_model:
            model = job.known_conceptual_model
            data["known_conceptual_model"] = {
                "classes": [
                    {
                        "id": c.id,
                        "name": serialize_langstring(c.name),
                        "definition": serialize_langstring(getattr(c, "definition", None)),
                        "explanation": serialize_langstring(getattr(c, "explanation", None)),
                        "isSubjectOfLaw": getattr(c, "isSubjectOfLaw", None),
                        "isObjectOfLaw": getattr(c, "isObjectOfLaw", None),
                        "specializes": [sc.id for sc in (getattr(c, "specializesClass", []) or [])],
                        "attributes": [
                            {
                                "id": a.id,
                                "name": serialize_langstring(a.name),
                                "definition": serialize_langstring(getattr(a, "definition", None)),
                                "explanation": serialize_langstring(getattr(a, "explanation", None)),
                            }
                            for a in (getattr(c, "ownsAttribute", []) or [])
                        ],
                    }
                    for c in (getattr(model, "classes", []) or [])
                ],
                "relationships": [
                    {
                        "id": r.id,
                        "name": serialize_langstring(r.name),
                        "definition": serialize_langstring(getattr(r, "definition", None)),
                        "explanation": serialize_langstring(getattr(r, "explanation", None)),
                        "mediates": [mc.id for mc in getattr(r, "mediatesClass", [])],
                    }
                    for r in (getattr(model, "relationships", []) or [])
                ],
            }
        if data["type"] == "ClassSuggestionsJob":
          data["suggestions"] = []
          if hasattr(job, "suggestions") and job.suggestions:
              for s in job.suggestions:
                  suggestion_data = {
                      "id": s.id,
                      "name": serialize_langstring(s.name),
                      "definition": serialize_langstring(getattr(s, "definition", None)),
                      "explanation": serialize_langstring(getattr(s, "explanation", None)),
                      "type": type(s).__name__,
                  }
                  suggestion_data["basedOn"] = [
                    lse.officialIdentifier for lse in getattr(s, "isBasedOnLegalStructuralElement", []) or []
                  ]
                  if suggestion_data["type"] == "GlobalClassSuggestion":
                      suggestion_data["isSubjectOfLaw"] = getattr(s, "isSubjectOfLaw", None)
                      suggestion_data["isObjectOfLaw"] = getattr(s, "isObjectOfLaw", None)
                      suggestion_data["specializes"] = [
                          sc.id for sc in (getattr(s, "specializes", []) or [])
                      ]
                  data["suggestions"].append(suggestion_data)
        elif data["type"] == "PropertySuggestionJob":
          property_job = job if isinstance(job, PropertySuggestionJob) else PropertySuggestionJob(**job.__dict__)
          data["selected_class_id"] = str(property_job.selected_class_id)
          data["suggestions"] = []
          logged_new_suggested_classes = set()
          if property_job.attribute_suggestions:
              for s in property_job.attribute_suggestions:
                  suggestion_data = {
                      "id": s.id,
                      "name": serialize_langstring(s.name),
                      "definition": serialize_langstring(getattr(s, "definition", None)),
                      "explanation": serialize_langstring(getattr(s, "explanation", None)),
                      "type": type(s).__name__,
                  }
                  suggestion_data["basedOn"] = [
                    lse.officialIdentifier for lse in getattr(s, "isBasedOnLegalStructuralElement", []) or []
                  ]
                  data["suggestions"].append(suggestion_data)
              for s in property_job.relationship_suggestions:
                  suggestion_data = {
                      "id": s.id,
                      "name": serialize_langstring(s.name),
                      "definition": serialize_langstring(getattr(s, "definition", None)),
                      "explanation": serialize_langstring(getattr(s, "explanation", None)),
                      "type": type(s).__name__,
                  }
                  suggestion_data["basedOn"] = [
                    lse.officialIdentifier for lse in getattr(s, "isBasedOnLegalStructuralElement", []) or []
                  ]
                  if suggestion_data["type"] == "GlobalRelationshipSuggestion":
                      suggestion_data["mediates"] = [
                          mc.id for mc in (getattr(s, "mediatesClass", []) or [])
                      ]
                  data["suggestions"].append(suggestion_data)
                  # Add missing mediatesClass classes as suggestions
                  if "known_conceptual_model" in data:
                      known_class_ids = {c["id"] for c in data["known_conceptual_model"].get("classes", [])}
                      # Collect all mediatesClass objects from relationship_suggestions
                      for mc in getattr(s, "mediatesClass", []) or []:
                          if mc.id != data["selected_class_id"] and mc.id not in known_class_ids and mc.id not in logged_new_suggested_classes:
                            data["suggestions"].append({
                                "id": mc.id,
                                "name": serialize_langstring(mc.name),
                                "type": "GlobalClassSuggestion"
                            })
                            logged_new_suggested_classes.add(mc.id)
        return json.dumps(data)

    def get(self, job_id: UUID) -> Optional[SuggestionJob]:
        with self.memory_lock:
            return self.jobs.get(job_id)

    def update(self, job: SuggestionJob) -> None:
        with self.memory_lock:
            self.jobs[job.job_id] = job

def serialize_langstring(value):
    """
    Helper to serialize LangString objects to a simple string (Czech mutation only).
    If value is a LangString, returns value["cs"] or value.value if language is 'cs', else None.
    If value is already a string or None, returns as is.
    """
    if value is None:
        return None
    # If it's a dict-like with 'cs' key
    if isinstance(value, dict) and "cs" in value:
        return value["cs"]
    # If it's an object with 'cs' attribute
    if hasattr(value, "cs"):
        return getattr(value, "cs")
    # If it's an object with 'value' and 'language' attributes (like LangString)
    if hasattr(value, "value") and hasattr(value, "language"):
        if getattr(value, "language") == "cs":
            return getattr(value, "value")
    # If it's already a string
    if isinstance(value, str):
        return value
    # If it's an object with __getitem__ and 'cs' key
    try:
        return value["cs"]
    except Exception:
        pass
    return None
