-- V7__add_attachments.sql
-- Media attachments stored in S3-compatible object storage; server only mints presigned URLs.

CREATE TABLE attachments (
    id              UUID PRIMARY KEY,
    message_id      UUID,
    uploader_id     BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    conversation_id BIGINT NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
    object_key      VARCHAR(512) NOT NULL UNIQUE,
    mime_type       VARCHAR(128) NOT NULL,
    size_bytes      BIGINT NOT NULL,
    status          VARCHAR(16) NOT NULL CHECK (status IN ('PENDING', 'CONFIRMED', 'ORPHANED')),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_attachments_uploader ON attachments(uploader_id);
CREATE INDEX idx_attachments_conversation ON attachments(conversation_id);
CREATE INDEX idx_attachments_pending ON attachments(status, created_at) WHERE status = 'PENDING';

ALTER TABLE outbox ADD COLUMN attachment_id UUID REFERENCES attachments(id);
