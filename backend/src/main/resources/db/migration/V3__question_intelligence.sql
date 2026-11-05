ALTER TABLE questions ADD COLUMN duplicate_of_id BIGINT;
ALTER TABLE questions ADD COLUMN priority_score INTEGER NOT NULL DEFAULT 0;
ALTER TABLE questions ADD COLUMN priority_reason VARCHAR(500);

ALTER TABLE questions
    ADD CONSTRAINT fk_questions_duplicate_of
    FOREIGN KEY (duplicate_of_id) REFERENCES questions (id);

CREATE INDEX idx_questions_meeting_priority ON questions (meeting_id, priority_score DESC, created_at);
CREATE INDEX idx_questions_duplicate_of ON questions (duplicate_of_id);
