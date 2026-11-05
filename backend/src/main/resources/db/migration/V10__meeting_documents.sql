CREATE TABLE meeting_documents (
    id BIGSERIAL PRIMARY KEY,
    meeting_id BIGINT NOT NULL UNIQUE REFERENCES meetings(id) ON DELETE CASCADE,
    status VARCHAR(20) NOT NULL,
    html_content TEXT NOT NULL,
    pdf_content BYTEA NOT NULL,
    generated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    reviewed_at TIMESTAMP WITH TIME ZONE,
    published_at TIMESTAMP WITH TIME ZONE
);
