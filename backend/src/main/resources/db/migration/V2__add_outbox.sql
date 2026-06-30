-- V2__add_outbox.sql
-- Temporary server-side store for offline delivery (store-and-forward).

CREATE TABLE outbox (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id BIGINT NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
    sender_id       BIGINT NOT NULL REFERENCES users(id),
    recipient_id    BIGINT NOT NULL REFERENCES users(id),
    message_id      UUID NOT NULL,
    content         TEXT NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at      TIMESTAMPTZ NOT NULL
);

CREATE UNIQUE INDEX idx_outbox_message_recipient ON outbox(message_id, recipient_id);
CREATE INDEX idx_outbox_recipient_created ON outbox(recipient_id, created_at);
