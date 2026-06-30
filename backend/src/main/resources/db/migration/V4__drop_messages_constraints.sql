-- V4__drop_messages_constraints.sql
-- Deprecate server-side message persistence; keep table for rollback window.

ALTER TABLE messages DROP CONSTRAINT IF EXISTS messages_conversation_id_fkey;
ALTER TABLE messages DROP CONSTRAINT IF EXISTS messages_sender_id_fkey;

COMMENT ON TABLE messages IS 'DEPRECATED: kept for rollback; messages now live on device SQLite';
