ALTER TABLE slides ADD COLUMN notes TEXT;
ALTER TABLE slides ADD COLUMN structure_json TEXT NOT NULL DEFAULT '{}';
ALTER TABLE slides ADD COLUMN embedding TEXT;
ALTER TABLE slides ADD COLUMN analysis_status VARCHAR(20) NOT NULL DEFAULT 'READY';
ALTER TABLE slides ADD COLUMN analysis_error VARCHAR(2000);
ALTER TABLE slides ADD COLUMN updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE ai_jobs ADD COLUMN attempt_count INTEGER NOT NULL DEFAULT 0;
ALTER TABLE ai_jobs ADD COLUMN max_attempts INTEGER NOT NULL DEFAULT 3;
ALTER TABLE ai_jobs ADD COLUMN next_attempt_at TIMESTAMP NULL;

CREATE INDEX idx_slides_presentation_status ON slides (presentation_id, analysis_status);
CREATE INDEX idx_ai_jobs_retry ON ai_jobs (status, next_attempt_at);
