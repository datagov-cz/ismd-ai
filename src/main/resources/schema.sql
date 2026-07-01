CREATE TABLE IF NOT EXISTS feedback_records (
    id BIGSERIAL PRIMARY KEY,
    job_id UUID NOT NULL,
    suggestion_id TEXT NOT NULL,
    feedback_type TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_feedback_records_job_id ON feedback_records(job_id);
CREATE INDEX IF NOT EXISTS idx_feedback_records_suggestion_id ON feedback_records(suggestion_id);
