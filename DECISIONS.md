# GUP — Architecture Decision Log

Decisions made during the local-first migration ([DB_MIGRATION.md](./DB_MIGRATION.md)). New entries are appended as phases complete.

---

## ADR-001 — Single-device scope

**Status:** Accepted  
**Date:** 2026-06-22

Each account is assumed to have **one active device** for the duration of this migration. Message history lives on that device in SQLite; the server does not serve permanent message storage.

Multi-device sync (last-seen cursor, cross-device replay) is explicitly deferred until the store-and-forward architecture is stable.

---

## ADR-002 — SQLite as source of truth for messages

**Status:** Accepted  
**Date:** 2026-06-22

All conversation messages are **read from and written to device SQLite**. PostgreSQL holds users, auth, conversations (metadata), and a **temporary server outbox** for offline delivery only.

The server deletes outbox rows after the recipient ACKs delivery. The client keeps full history locally with no automatic server-side purge.

---

## ADR-003 — ACK on receive, not on read

**Status:** Accepted  
**Date:** 2026-06-22

Delivery acknowledgment is sent when the recipient **persists the message to SQLite**, not when the user opens or reads the chat. This clears the server outbox and may update sender-side delivery status (e.g. double-tick).

Read receipts are out of scope for this migration and require a separate protocol.

---

## ADR-004 — End-to-end encryption deferred

**Status:** Accepted  
**Date:** 2026-06-22

Messages are **plaintext on the wire and in SQLite** during this migration. E2E encryption will be designed and implemented **after** the local-first store-and-forward architecture is complete and validated.

Rationale: stabilize transport, outbox, and client storage contracts first; encryption layers on top of a known envelope shape (`MessageEnvelope`).

---

## ADR-005 — Conversation bootstrap gap (single-device)

**Status:** Accepted  
**Date:** 2026-06-22

Under single-device scope, a conversation started on another account/device will not appear locally until the client calls `GET /api/conversations`. The app upserts server conversations into SQLite on login, on foreground resume, and whenever the conversation list is refreshed.

If multi-device becomes a requirement, add a push or polling mechanism to detect new threads without a full list fetch.

---

## ADR-006 — Local storage security

**Status:** Accepted  
**Date:** 2026-06-22

`expo-sqlite` stores `gup.db` in the **app sandbox** on iOS and Android (not shared/external storage). Message plaintext is acceptable for this migration phase per ADR-004; the file is protected by OS-level app isolation.

Server-side: `GET /api/inbox/pending` filters strictly by authenticated user ID; ACK deletes only when `messageId`, `recipientId`, and `conversationId` match the outbox row.

---

## Rollback (reference)

If the migration must be reverted before cutover:

1. Revert `V5__drop_messages_table.sql` and restore `messages` table schema from `V1__initial_schema.sql`.
2. Re-enable PostgreSQL message persistence in `MessageService` and REST history endpoint.
3. Truncate or drop the server `outbox` table.
4. Clients fall back to TanStack Query REST fetches until SQLite integration is removed.

`V5__drop_messages_table.sql` was applied after Phase 4 validation; restore from backup if rollback is needed.
