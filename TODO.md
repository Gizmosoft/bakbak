# Future Features

- [ ] **Phone-contact based chat discovery + message delivery** — sync device contacts, match against registered users by phone number, surface as suggested conversations; requires phone number field on `users` table, contact permission on mobile, and a contacts-matching API endpoint that returns only registered users without exposing the full user database
- [ ] **E2E Encryption (Signal Protocol)** — implement the Signal Double Ratchet algorithm for forward secrecy and break-in recovery; requires key exchange on first message (X3DH), per-session ratchet state stored in SQLite, server becomes a blind relay (ciphertext only in outbox), and a key backup/restore strategy for device migration
- [ ] **Multimedia support in messaging** — images, video, audio, and file attachments; requires a CDN or object storage backend (e.g. S3-compatible), pre-signed upload URLs issued by the server, thumbnail generation, and a new `attachments` table in SQLite with download/caching logic on the client
- [ ] **Read receipts, "Typing..." indicator, and "Connection Lost" UI** — read receipts require a separate `READ` ACK relayed over WebSocket and stored in SQLite; typing indicators are ephemeral STOMP events (no persistence); connection-lost banner uses React Native `NetInfo` + STOMP disconnect events to show an inline warning and suppress send UI
- [ ] **Push Notifications** — integrate FCM (Android) and APNs (iOS) via Expo Notifications; server sends a push when a message is enqueued to an offline recipient's outbox; requires storing device push tokens in PostgreSQL, handling token rotation, and a silent-push → foreground drain flow to avoid double-delivery
- [ ] **Message deletion and editing** — soft-delete and edit events relayed as special message types (`DELETE`, `EDIT`) over WebSocket; applied to SQLite on receipt; deleted messages show tombstone ("This message was deleted"); edits show edited indicator with last-edited timestamp; server only relays the event, does not store edit history

---

## Deferred (Out of Scope for Current Migration)

- [ ] **Multi-device sync** — requires a sync protocol (e.g. last-seen cursor + server-side message log replay). Design separately.
- [ ] **Device backup / restore** — SQLite export to iCloud / Google Drive. Design separately.
- [ ] **Group chats > 2 participants** — outbox fan-out to N recipients, per-recipient ACK tracking. Already partially supported by schema but untested.
