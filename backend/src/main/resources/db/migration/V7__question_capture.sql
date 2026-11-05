ALTER TABLE questions ADD COLUMN language VARCHAR(16) NOT NULL DEFAULT 'und';
ALTER TABLE questions ADD COLUMN translated_text VARCHAR(6000);
ALTER TABLE questions ADD COLUMN source VARCHAR(20) NOT NULL DEFAULT 'TYPED';
ALTER TABLE questions ADD COLUMN start_ms BIGINT;
ALTER TABLE questions ADD COLUMN end_ms BIGINT;
ALTER TABLE questions ADD COLUMN question_confidence DOUBLE PRECISION;

CREATE INDEX idx_questions_meeting_slide_created
    ON questions (meeting_id, slide_number, created_at);
