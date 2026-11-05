ALTER TABLE transcript_segments ADD COLUMN slide_number INTEGER;

CREATE INDEX idx_transcript_segments_meeting_slide
    ON transcript_segments (meeting_id, slide_number, created_at);
