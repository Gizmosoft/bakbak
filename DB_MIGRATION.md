# GUP — Architecture Migration TODO

Migrate from server-stored messages to a **local-first, store-and-forward** architecture:

- **PostgreSQL** (server): users + auth only, plus a temporary `outbox` for offline delivery
- **SQLite** (device): primary message store — all conversations and messages live here
- **WebSocket relay**: messages travel server → recipient in real time; server copy deleted after delivery ACK

**Single-device-per-account** scope for now. Multi-device sync is explicitly deferred.

---

## Legend

- `[ ]` — not started
- `[x]` — complete
- `[~]` — in progress
- `[!]` — blocked / needs decision

---

## Phase 0 — Foundation & Contracts

Define shared data contracts before touching any implementation. Everything downstream depends on these.

### 0.1 Message Envelope Schema

- [x] Define the canonical `MessageEnvelope` type shared across WebSocket send, relay broadcast, and SQLite insert
  - Fields: `id` (client-generated UUID v4), `conversationId`, `senderId`, `content`, `sentAt` (ISO-8601 client timestamp), `serverReceivedAt` (set by server), `type` (`CHAT` | `ACK` | `DELIVERED` | `SYSTEM`)
  - File: `frontend/gup-ui/src/types/message.ts` — extend existing `Message` type
  - File: `backend/src/main/java/uk/deadcatlab/bakbak/dto/response/ChatMessageBroadcast.java` — update broadcast DTO

### 0.2 Delivery ACK Schema

- [x] Define `DeliveryAck` type: `{ messageId: string, conversationId: string, recipientId: string, ackedAt: string }`
  - Frontend: `frontend/gup-ui/src/types/message.ts`
  - Backend DTO: create `backend/src/main/java/.../dto/request/DeliveryAckRequest.java`

### 0.3 Presence / Online Status Schema

- [x] Define presence event shape: `{ userId: string, status: 'ONLINE' | 'OFFLINE', timestamp: string }`
  - Backend: create `backend/src/main/java/.../dto/response/PresenceEvent.java`
  - Frontend: `frontend/gup-ui/src/types/user.ts`

### 0.4 Decision Log

- [x] Document in `DECISIONS.md`: single-device scope, E2E encryption to be implemented after this migration process is completed, SQLite as truth, ACK-on-receive (not ACK-on-read)

---

## Phase 1 — Backend: PostgreSQL Slim-Down

Strip the server of permanent message storage. Add outbox + presence infrastructure.

### 1.1 Flyway Migration — Add Outbox, Drop Messages as Primary Store

- [ ] Create `V2__add_outbox.sql`
  - New table `outbox` — columns: `id UUID PK`, `conversation_id UUID FK→conversations`, `sender_id UUID FK→users`, `recipient_id UUID FK→users`, `message_id UUID` (client-generated, for idempotency), `content TEXT NOT NULL`, `created_at TIMESTAMPTZ DEFAULT now()`, `expires_at TIMESTAMPTZ` (TTL, e.g. 30 days)
  - Index on `(recipient_id, created_at)` for efficient pull-on-connect
  - Index on `message_id` for idempotent insert check
  - File: `backend/src/main/resources/db/migration/V2__add_outbox.sql`

- [ ] Create `V3__add_presence.sql`
  - New table `user_presence` — columns: `user_id UUID PK FK→users`, `status VARCHAR(10)`, `last_seen_at TIMESTAMPTZ`, `session_id VARCHAR(128)` (STOMP session ID for tie-breaking)
  - File: `backend/src/main/resources/db/migration/V3__add_presence.sql`

- [ ] Create `V4__drop_messages_constraints.sql`
  - Remove foreign key from `messages` table (keep table temporarily for migration safety, mark deprecated)
  - Do NOT drop `messages` table yet — keep for rollback window
  - File: `backend/src/main/resources/db/migration/V4__drop_messages_constraints.sql`

### 1.2 Outbox Entity & Repository

- [ ] Create `backend/src/main/java/.../model/OutboxMessage.java`
  - JPA entity mapping `outbox` table
  - Lombok `@Data`, `@Builder`, `@NoArgsConstructor`, `@AllArgsConstructor`

- [ ] Create `backend/src/main/java/.../repository/OutboxMessageRepository.java`
  - Extend `JpaRepository<OutboxMessage, UUID>`
  - Custom query: `findAllByRecipientIdOrderByCreatedAtAsc(UUID recipientId)`
  - Custom query: `existsByMessageId(UUID messageId)` (idempotency check)
  - Custom query: `deleteByMessageIdAndRecipientId(UUID messageId, UUID recipientId)`

### 1.3 Presence Entity & Repository

- [ ] Create `backend/src/main/java/.../model/UserPresence.java`
  - JPA entity mapping `user_presence` table

- [ ] Create `backend/src/main/java/.../repository/UserPresenceRepository.java`
  - `findByUserId(UUID userId)`
  - `findAllByUserIdIn(List<UUID> userIds)` — for bulk presence check on conversation list

### 1.4 OutboxService

- [ ] Create `backend/src/main/java/.../service/OutboxService.java`
  - `enqueue(OutboxMessage msg)` — idempotent insert (check `existsByMessageId` first)
  - `drainForRecipient(UUID recipientId): List<OutboxMessage>` — fetch all pending
  - `acknowledge(UUID messageId, UUID recipientId)` — delete row after ACK
  - `pruneExpired()` — `@Scheduled` job, run every hour, delete rows where `expires_at < now()`

### 1.5 PresenceService

- [ ] Create `backend/src/main/java/.../service/PresenceService.java`
  - `markOnline(UUID userId, String sessionId)`
  - `markOffline(UUID userId, String sessionId)`
  - `isOnline(UUID userId): boolean`
  - `getPresence(List<UUID> userIds): Map<UUID, PresenceStatus>`

### 1.6 Refactor MessageService

- [ ] Modify `backend/src/main/java/.../service/MessageService.java`
  - Remove: `saveMessage()` (no longer persists to `messages` table)
  - Remove: `getMessages()` / pagination queries (messages now live on device)
  - Keep: participant validation logic (moved to shared helper or conversation service)
  - Add: delegate to `OutboxService.enqueue()` when recipient is offline
  - Add: delegate to `PresenceService.isOnline()` to decide relay vs. enqueue

### 1.7 Refactor ChatController (WebSocket)

- [ ] Modify `backend/src/main/java/.../websocket/ChatController.java`
  - On `SEND /app/chat/{conversationId}`:
    1. Validate sender is participant (existing logic)
    2. Resolve all recipient user IDs for conversation
    3. For each recipient:
       - If online (`PresenceService.isOnline()`): relay via `/topic/conversation/{conversationId}`
       - If offline: `OutboxService.enqueue()`
    4. Echo message back to sender on `/user/queue/sent` (for sender's SQLite write confirmation)
  - On `SEND /app/ack`:
    - Accept `DeliveryAckRequest`
    - Call `OutboxService.acknowledge(messageId, recipientId)`
    - Notify original sender via `/user/queue/delivery-receipts` (optional, for "delivered" ticks)

- [ ] Add `SEND /app/presence/ping` handler — client heartbeats every 30s; update `user_presence.last_seen_at`

### 1.8 Presence Lifecycle via WebSocket Interceptors

- [ ] Modify `backend/src/main/java/.../websocket/WebSocketAuthInterceptor.java`
  - On `CONNECT`: call `PresenceService.markOnline(userId, sessionId)` + broadcast presence event
  - On `DISCONNECT`: call `PresenceService.markOffline(userId, sessionId)` + broadcast presence event
  - Drain outbox on connect: call `OutboxService.drainForRecipient(userId)` and push each to `/user/queue/inbox`

### 1.9 Remove MessageController REST Endpoints

- [ ] Modify `backend/src/main/java/.../controller/MessageController.java`
  - Remove `GET /api/conversations/{id}/messages` endpoint (history now served from device SQLite)
  - Keep file but repurpose or delete — confirm no frontend dependency before removing
  - Add `GET /api/conversations/{id}/participants/presence` — returns presence map for a conversation (used by frontend to show online indicators)

### 1.10 Pending Messages REST Endpoint (Bootstrap on App Open)

- [ ] Add endpoint to `ConversationController` or new `InboxController`:
  - `GET /api/inbox/pending` — returns all outbox rows for authenticated user
  - Called once on app open before WebSocket connects (graceful fallback)
  - Payload: `List<PendingMessageResponse>` — same shape as WebSocket relay envelope

### 1.11 Update ConversationService

- [ ] Modify `backend/src/main/java/.../service/ConversationService.java`
  - Remove: `last_message_at` update on every message save (no longer tracked server-side)
  - Keep: get-or-create conversation, participant management, conversation listing for user
  - `listForUser()` response: strip out any embedded message previews (frontend derives from SQLite)

### 1.12 Backend Tests

- [ ] Update `MessageControllerTest.java` — remove history endpoint tests; add ACK endpoint tests
- [ ] Create `OutboxServiceTest.java` — test enqueue idempotency, drain, prune, ACK delete
- [ ] Create `PresenceServiceTest.java` — test online/offline transitions, multi-session tie-breaking
- [ ] Update `ChatController` WebSocket integration test — test relay path and outbox fallback path
- [ ] Update `ConversationControllerTest.java` — remove message-preview assertions from list response

---

## Phase 2 — Frontend: SQLite Integration

Replace TanStack Query REST fetches for messages with local SQLite reads/writes.

### 2.1 Install & Configure expo-sqlite

- [ ] Install package: `expo install expo-sqlite`
- [ ] Verify `expo-sqlite` is listed in `frontend/gup-ui/package.json` dependencies
- [ ] Add `"expo-sqlite"` to `app.json` plugins array if required by SDK 54

### 2.2 SQLite Schema & Migrations

- [ ] Create `frontend/gup-ui/src/db/schema.ts`
  - Tables:
    - `conversations (id TEXT PK, participant_key TEXT, other_user_id TEXT, other_user_display_name TEXT, other_user_username TEXT, created_at TEXT, last_message_at TEXT, last_message_preview TEXT)`
    - `messages (id TEXT PK, conversation_id TEXT, sender_id TEXT, content TEXT, sent_at TEXT, server_received_at TEXT, status TEXT CHECK(status IN ('SENDING','SENT','DELIVERED','FAILED')))`
    - `outbox_pending (id TEXT PK, conversation_id TEXT, content TEXT, created_at TEXT, retry_count INTEGER DEFAULT 0)` — client-side send queue for offline sends
  - Export SQL strings for `CREATE TABLE IF NOT EXISTS` for each

- [ ] Create `frontend/gup-ui/src/db/migrations.ts`
  - Version-gated migration runner using `PRAGMA user_version`
  - Migration 1: initial schema (conversations, messages, outbox_pending)
  - Export `runMigrations(db: SQLiteDatabase): Promise<void>`

### 2.3 Database Client

- [ ] Create `frontend/gup-ui/src/db/client.ts`
  - Open database with `SQLite.openDatabaseAsync('gup.db')`
  - Run migrations on open
  - Export singleton `db` instance
  - Export typed query helpers: `executeAsync`, `getAllAsync`, `getFirstAsync`

### 2.4 Database Provider

- [ ] Create `frontend/gup-ui/src/providers/DatabaseProvider.tsx`
  - Opens DB on mount, runs migrations
  - Exposes `db` via context (`useDatabaseContext` hook)
  - Shows loading state during DB open/migration (prevents UI flash on first install)
  - Add to provider stack in `app/_layout.tsx` — wrap before `AuthProvider`

### 2.5 Message Repository (SQLite)

- [ ] Create `frontend/gup-ui/src/db/repositories/message.repository.ts`
  - `insertMessage(msg: MessageEnvelope): Promise<void>` — upsert by `id` (idempotent)
  - `getMessages(conversationId: string, limit: number, beforeId?: string): Promise<Message[]>` — cursor-based pagination matching old API contract
  - `updateMessageStatus(id: string, status: MessageStatus): Promise<void>`
  - `getLastMessage(conversationId: string): Promise<Message | null>`

### 2.6 Conversation Repository (SQLite)

- [ ] Create `frontend/gup-ui/src/db/repositories/conversation.repository.ts`
  - `upsertConversation(conv: ConversationRecord): Promise<void>`
  - `listConversations(userId: string): Promise<ConversationWithLastMessage[]>` — joins with messages for preview
  - `getConversation(id: string): Promise<ConversationRecord | null>`
  - `updateLastMessage(conversationId: string, preview: string, at: string): Promise<void>`

### 2.7 Client-Side Outbox Repository (SQLite)

- [ ] Create `frontend/gup-ui/src/db/repositories/outbox.repository.ts`
  - `enqueue(item: OutboxPending): Promise<void>` — write to `outbox_pending` before sending
  - `dequeue(id: string): Promise<void>` — remove after successful WebSocket delivery
  - `getPending(): Promise<OutboxPending[]>` — load on connect to retry
  - `incrementRetry(id: string): Promise<void>`
  - `purgeFailed(maxRetries: number): Promise<void>` — remove after N failures

### 2.8 Refactor `useMessages` Hook

- [ ] Modify `frontend/gup-ui/src/features/chat/hooks/useMessages.ts`
  - Remove: `useQuery` fetching `GET /api/conversations/{id}/messages`
  - Replace with: query SQLite `message.repository.getMessages(conversationId, limit, cursor)`
  - Return same shape (`data`, `isLoading`, `fetchNextPage`, `hasNextPage`) for zero screen-side changes
  - Trigger: re-run query when a new message is inserted into SQLite (use SQLite change event or manual invalidation)

### 2.9 Refactor `useConversationList` Hook

- [ ] Modify `frontend/gup-ui/src/features/conversations/hooks/useConversationList.ts`
  - Remove: `useQuery` fetching `GET /api/conversations`
  - Replace with: query SQLite `conversation.repository.listConversations(userId)`
  - Seed SQLite from server on first login (see Phase 3.1)
  - Keep TanStack Query as the re-render trigger using `queryKey` that invalidates on SQLite writes

### 2.10 Remove `messages.api.ts` REST Dependency

- [ ] Modify `frontend/gup-ui/src/api/messages.api.ts`
  - Remove `fetchMessages()` function (no longer used; messages come from SQLite)
  - Keep file if other exports remain; otherwise delete and remove from `src/api/index.ts`

### 2.11 Update TypeScript Types

- [ ] Modify `frontend/gup-ui/src/types/message.ts`
  - Add `status: 'SENDING' | 'SENT' | 'DELIVERED' | 'FAILED'` field
  - Add `clientId: string` field (UUID generated on client before send)
  - Rename ambiguous fields to align with SQLite schema

- [ ] Modify `frontend/gup-ui/src/types/conversation.ts`
  - Add `lastMessagePreview: string | null`
  - Add `lastMessageAt: string | null`
  - Add `otherUserPresence: 'ONLINE' | 'OFFLINE' | 'UNKNOWN'`

---

## Phase 3 — Frontend: WebSocket Relay + ACK Layer

Wire the new send/receive flow through the updated WebSocket protocol.

### 3.1 Bootstrap: Seed SQLite on Login

- [ ] Modify `frontend/gup-ui/src/providers/AuthProvider.tsx`
  - After successful login / token validation:
    1. Call `GET /api/conversations` → upsert each into SQLite via `conversation.repository`
    2. Call `GET /api/inbox/pending` → insert each pending message into SQLite, then send ACKs
  - This ensures SQLite is warm before any screen renders

### 3.2 Refactor WebSocket Send Flow

- [ ] Modify `frontend/gup-ui/src/websocket/chat.client.ts`
  - `sendMessage(conversationId, content)`:
    1. Generate `clientId = uuidv4()`
    2. Write to `outbox_pending` (SQLite) with `status = SENDING`
    3. Write to `messages` (SQLite) with `status = SENDING` — optimistic insert
    4. STOMP SEND to `/app/chat/{conversationId}` with `{ id: clientId, content }`
    5. On echo from `/user/queue/sent`:
       - Update SQLite message `status = SENT`
       - Remove from `outbox_pending`
    6. On timeout (10s): mark `status = FAILED` in SQLite, increment retry count

- [ ] Install `uuid` package: `npx expo install expo-crypto` (use `Crypto.randomUUID()` instead of third-party uuid)

### 3.3 Refactor WebSocket Receive Flow

- [ ] Modify `frontend/gup-ui/src/websocket/cache-updates.ts` → rename to `message-sync.ts`
  - On message received from `/topic/conversation/{id}` or `/user/queue/inbox`:
    1. `message.repository.insertMessage(envelope)` — idempotent upsert
    2. `conversation.repository.updateLastMessage(conversationId, preview, at)`
    3. Invalidate TanStack Query key for conversation list and message list (triggers UI re-render)
    4. Send ACK: STOMP SEND to `/app/ack` with `{ messageId, conversationId }`

- [ ] Modify `frontend/gup-ui/src/features/chat/hooks/useChatSubscription.ts`
  - Subscribe to `/user/queue/inbox` (server-pushed pending messages on connect)
  - Subscribe to `/user/queue/sent` (echo confirming server received send)
  - Subscribe to `/user/queue/delivery-receipts` (optional: update message status to DELIVERED)

### 3.4 Offline Send Queue — Retry on Reconnect

- [ ] Modify `frontend/gup-ui/src/providers/ChatConnectionProvider.tsx`
  - On STOMP `CONNECTED` event:
    1. Call `outbox.repository.getPending()` to get unsent messages
    2. Attempt to resend each via WebSocket
    3. Remove from outbox on success, increment retry on failure
    4. Purge after 5 consecutive failures (mark message `FAILED` in SQLite)

### 3.5 Presence Heartbeat

- [ ] Add to `frontend/gup-ui/src/providers/ChatConnectionProvider.tsx`
  - After STOMP connect, start `setInterval` sending STOMP SEND to `/app/presence/ping` every 30s
  - Clear interval on disconnect / component unmount

### 3.6 Fetch Presence for Conversation

- [ ] Create `frontend/gup-ui/src/features/chat/hooks/usePresence.ts`
  - `usePresence(conversationId: string)` — calls `GET /api/conversations/{id}/participants/presence`
  - Returns `{ userId: string, status: 'ONLINE' | 'OFFLINE' }[]`
  - Refetch every 60s (polling fallback; real-time via presence events from WebSocket is Phase 4)

### 3.7 Update Message Bubble UI

- [ ] Modify `frontend/gup-ui/src/features/chat/components/MessageBubble.tsx`
  - Add delivery status indicator: clock icon = `SENDING`, single tick = `SENT`, double tick = `DELIVERED`, red X = `FAILED`
  - Failed messages: tap to retry (re-enqueue via `chat.client.sendMessage`)

### 3.8 Update Conversation List UI

- [ ] Modify `frontend/gup-ui/src/features/conversations/components/ConversationListItem.tsx`
  - Show `lastMessagePreview` from SQLite instead of server response
  - Show `lastMessageAt` timestamp
  - Show online indicator dot when `otherUserPresence === 'ONLINE'`

---

## Phase 4 — Hardening & Edge Cases

### 4.1 Idempotency on Both Ends

- [ ] Backend: `OutboxService.enqueue()` must check `existsByMessageId()` before insert — prevents duplicate outbox rows if client retries a send
- [ ] Frontend: `message.repository.insertMessage()` must use `INSERT OR IGNORE` / upsert — prevents duplicate rows if server relays a message the client already echoed

### 4.2 Message Expiry & Cleanup

- [ ] Backend: `OutboxService.pruneExpired()` scheduled job (every hour, delete where `expires_at < now()`)
- [ ] Frontend: No automatic purge (messages stay in SQLite forever — user owns their history)
- [ ] Config: make outbox TTL configurable via `application.properties` (`bakbak.outbox.ttl-days=30`)

### 4.3 Conversation Bootstrap Gap

- [ ] Handle the case where a new conversation is started on another client (edge case under single-device scope — document for now, fix if needed)
- [ ] On `GET /api/conversations` response, upsert any conversations not already in SQLite

### 4.4 App Backgrounding / WebSocket Reconnect

- [ ] Modify `frontend/gup-ui/src/providers/ChatConnectionProvider.tsx`
  - Use React Native `AppState` API to detect foreground/background transitions
  - On foreground: reconnect WebSocket if disconnected, re-run bootstrap (`GET /api/inbox/pending`)
  - On background: send STOMP DISCONNECT gracefully (so server marks offline immediately)

### 4.5 SQLite Thread Safety

- [ ] All SQLite operations must run through the single `db` client instance from `DatabaseProvider`
- [ ] Wrap concurrent writes with a queue or use WAL mode: `PRAGMA journal_mode=WAL` in `client.ts` initialization
- [ ] Test on both iOS simulator and Android emulator for SQLite locking issues

### 4.6 Error Boundaries

- [ ] Add error boundary around chat screen for SQLite failures (DB open failure, migration failure)
- [ ] Fallback: show empty state with "Storage unavailable" message rather than crash
- [ ] Log SQLite errors to console in dev mode

### 4.7 Security Audit

- [ ] Backend: verify outbox rows are only accessible by the `recipient_id` user — no endpoint leaks others' pending messages
- [ ] Backend: `GET /api/inbox/pending` must filter strictly by authenticated user's ID
- [ ] Backend: ACK endpoint must verify the ack-ing user is the `recipient_id` on the outbox row
- [ ] Frontend: SQLite file is stored in app sandbox (not shared storage) — verify with Expo docs for iOS/Android

### 4.8 Migration Rollback Plan

- [ ] Document rollback steps in `DECISIONS.md`: re-enable `MessageRepository` saves, re-enable REST history endpoint, wipe outbox table
- [ ] Keep `messages` table in PostgreSQL until Phase 4 is complete and stable (do not run drop migration until then)
- [ ] Create `V5__drop_messages_table.sql` but leave it un-applied until post-validation

---

## Phase 5 — Testing

### 5.1 Backend Integration Tests

- [ ] `OutboxServiceTest.java` — enqueue idempotency, drain returns correct rows, prune removes expired, ACK deletes correct row
- [ ] `PresenceServiceTest.java` — mark online/offline, concurrent session handling, presence map query
- [ ] WebSocket integration test — verify relay path: sender sends → recipient (mocked STOMP session) receives
- [ ] WebSocket integration test — verify outbox path: sender sends → recipient offline → outbox row created
- [ ] WebSocket integration test — verify drain on connect: connect with pending outbox → messages pushed to `/user/queue/inbox`
- [ ] `InboxControllerTest.java` — REST pending messages endpoint auth, response shape

### 5.2 Frontend Unit Tests

- [ ] `message.repository.test.ts` — insertMessage idempotency, pagination cursor, status update
- [ ] `conversation.repository.test.ts` — upsert, list ordering, last message update
- [ ] `outbox.repository.test.ts` — enqueue/dequeue cycle, retry increment, purge logic
- [ ] `useMessages.test.ts` — verify SQLite is queried, not REST
- [ ] `useConversationList.test.ts` — verify SQLite is queried, seeded from server on first login

### 5.3 End-to-End (Manual)

- [ ] Online send/receive: two devices on LAN, send message, verify appears instantly on both, verify NOT in `messages` PostgreSQL table
- [ ] Offline delivery: recipient kills app, sender sends, recipient relaunches — message delivered from outbox
- [ ] Optimistic UI: send message, immediately shows as `SENDING` in bubble, transitions to `SENT` on echo
- [ ] Failed send: kill network mid-send, verify `FAILED` status, retry works on reconnect
- [ ] App backgrounded: send message while app is in background, open app, message is in SQLite

---

## Phase 6 — Cleanup

- [ ] Delete `backend/src/main/java/.../controller/MessageController.java` (confirm no remaining consumers)
- [ ] Delete `backend/src/main/java/.../repository/MessageRepository.java`
- [ ] Delete `backend/src/main/java/.../model/Message.java`
- [ ] Delete `backend/src/main/java/.../dto/response/MessageResponse.java`
- [ ] Delete `frontend/gup-ui/src/api/messages.api.ts` (if fully empty)
- [ ] Remove `messages` table references from `ConversationService`
- [ ] Apply `V5__drop_messages_table.sql` migration
- [ ] Remove `useQuery` imports from `useMessages.ts` and `useConversationList.ts` (if fully removed from REST)
- [ ] Remove `src/websocket/cache-updates.ts` (replaced by `message-sync.ts`)
- [ ] Audit `src/constants/query-keys.ts` — remove stale keys for messages and conversations REST
- [ ] Audit `src/constants/api-paths.ts` — remove stale paths for message history endpoint
- [ ] Final: run full test suite, verify zero references to old `GET /api/conversations/{id}/messages`

---

## Deferred (Out of Scope for This Migration)

- [ ] **Multi-device sync** — requires a sync protocol (e.g. last-seen cursor + server-side message log replay). Design separately.
- [ ] **End-to-end encryption** — messages currently plaintext on the wire and in SQLite. Add after architecture is stable.
- [ ] **Push notifications** — FCM/APNs for offline message alerts. Integrates with outbox drain flow.
- [ ] **Device backup / restore** — SQLite export to iCloud/Google Drive. Design separately.
- [ ] **Group chats > 2 participants** — outbox fan-out to N recipients, per-recipient ACK tracking. Already partially supported by schema but untested.
- [ ] **Read receipts** — separate from delivery ACKs. Requires additional `read_at` tracking in SQLite and a read-receipt relay channel.
- [ ] **Message deletion / editing** — new message type (`DELETE`, `EDIT`) relayed over WebSocket, applied to SQLite.
- [ ] **Media messages** — file upload, CDN storage, thumbnail generation. Out of scope.
