-- V6__add_signal_keys.sql
-- Public Signal Protocol key material + outbox encryption marker.
-- Private keys never leave the client.

CREATE TABLE user_identity_keys (
    user_id             BIGINT PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    registration_id     INTEGER NOT NULL,
    identity_key_public TEXT NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    rotated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE signed_pre_keys (
    user_id     BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    key_id      INTEGER NOT NULL,
    public_key  TEXT NOT NULL,
    signature   TEXT NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (user_id, key_id)
);

CREATE INDEX idx_signed_pre_keys_user_created ON signed_pre_keys(user_id, created_at DESC);

CREATE TABLE one_time_pre_keys (
    user_id      BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    key_id       INTEGER NOT NULL,
    public_key   TEXT NOT NULL,
    consumed_at  TIMESTAMPTZ,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (user_id, key_id)
);

CREATE INDEX idx_otpk_available ON one_time_pre_keys(user_id, key_id) WHERE consumed_at IS NULL;

ALTER TABLE outbox
    ADD COLUMN encryption VARCHAR(16) NOT NULL DEFAULT 'NONE';
