-- V5__drop_messages_table.sql
-- Server no longer stores message history; device SQLite is the source of truth.

DROP TABLE IF EXISTS messages;
