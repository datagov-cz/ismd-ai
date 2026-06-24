CREATE TABLE IF NOT EXISTS feedback_records (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    job_id TEXT NOT NULL,
    suggestion_id TEXT NOT NULL,
    feedback_type TEXT NOT NULL,
    created_at TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_feedback_records_job_id ON feedback_records(job_id);
CREATE INDEX IF NOT EXISTS idx_feedback_records_suggestion_id ON feedback_records(suggestion_id);
