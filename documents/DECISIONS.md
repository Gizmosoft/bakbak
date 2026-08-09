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

**Status:** Superseded by ADR-008  
**Date:** 2026-06-22

Messages were **plaintext on the wire and in SQLite** during the local-first migration. E2E was deferred until transport, outbox, and client storage contracts were stable.

**Superseded:** New CHAT messages use Signal Protocol (`SIGNAL_V1`). See ADR-008 and [E2E_ENCRYPTION.md](./E2E_ENCRYPTION.md).
---

## ADR-005 — Conversation bootstrap gap (single-device)

**Status:** Accepted  
**Date:** 2026-06-22

Under single-device scope, a conversation started on another account/device will not appear locally until the client calls `GET /api/conversations`. The app upserts server conversations into SQLite on login, on foreground resume, and whenever the conversation list is refreshed.

If multi-device becomes a requirement, add a push or polling mechanism to detect new threads without a full list fetch.

---

## ADR-006 — Local storage security

**Status:** Accepted (updated 2026-08-02)  
**Date:** 2026-06-22

`expo-sqlite` stores `gup.db` in the **app sandbox** on iOS and Android (not shared/external storage). After E2E (ADR-008), message bodies are stored as **plaintext after decrypt** for UX (Signal-like); ciphertext lives only on the wire and in the server outbox. Legacy `NONE` rows remain readable. Identity/prekey secrets live in SecureStore (ADR-009).

Server-side: `GET /api/inbox/pending` filters strictly by authenticated user ID; ACK deletes only when `messageId`, `recipientId`, and `conversationId` match the outbox row.

---

## ADR-007 — Outbox until ACK (online included)

**Status:** Accepted  
**Date:** 2026-07-25

Presence `ONLINE` must not mean “subscribed to `/topic/conversation/{id}`”. Clients only subscribe to a conversation topic while that chat screen is open.

Therefore every recipient always gets a server outbox row until delivery ACK. Online recipients also receive an immediate push on `/user/queue/inbox` (subscribed for the whole session). The conversation topic remains an optional fast path for an open chat.

Clients additionally call `GET /api/inbox/pending` after WebSocket `CONNECTED` to cover the connect-time drain race (server may push inbox before the client has subscribed).


If the migration must be reverted before cutover:

1. Revert `V5__drop_messages_table.sql` and restore `messages` table schema from `V1__initial_schema.sql`.
2. Re-enable PostgreSQL message persistence in `MessageService` and REST history endpoint.
3. Truncate or drop the server `outbox` table.
4. Clients fall back to TanStack Query REST fetches until SQLite integration is removed.

`V5__drop_messages_table.sql` was applied after Phase 4 validation; restore from backup if rollback is needed.

---

## ADR-008 — Signal Protocol E2EE via @noble (Expo Go)

**Status:** Accepted  
**Date:** 2026-08-02

New chat bodies use the **Signal Protocol** (X3DH + Double Ratchet) implemented in TypeScript with `@noble/curves`, `@noble/ciphers`, and `@noble/hashes` so the app remains testable in **Expo Go**.

- Wire/outbox: `encryption: SIGNAL_V1` and opaque ciphertext; server is a blind relay.
- Local SQLite: plaintext **after** decrypt (legacy `NONE` rows remain readable).
- Crypto is isolated behind `SignalProtocolService` for a possible future `libsignal` swap.

Supersedes ADR-004 for new messages. Details: [E2E_ENCRYPTION.md](./E2E_ENCRYPTION.md).

---

## ADR-009 — Single-device SecureStore key custody

**Status:** Accepted  
**Date:** 2026-08-02

Identity keys, signed-prekey secrets, and one-time prekey secrets are stored only in **`expo-secure-store`**. Ratchet/session state is in SQLite. No cloud key backup in v1.

Reinstall generates a new identity and republishes prekeys; peers detect identity change via TOFU and must re-establish sessions. Encrypted backup/restore remains deferred with multi-device sync.
