-- V3__add_presence.sql
-- Tracks online/offline state for store-and-forward routing.

CREATE TABLE user_presence (
    user_id         BIGINT PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    status          VARCHAR(10) NOT NULL,
    last_seen_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    session_id      VARCHAR(128)
);
