# Gup UI (`gup-ui`)

Expo / React Native client for Gup: auth, conversation list, 1:1 chat, local SQLite history, STOMP realtime, and Signal Protocol E2EE.

## Role in the system

- Owns message history in **SQLite** and long-term crypto secrets in **SecureStore**
- Encrypts chat bodies before STOMP send; decrypts on receive / inbox drain
- Publishes public prekeys to the backend on login
- ACKs delivery after persisting inbound messages locally

The backend is a blind relay for ciphertext. Full system design: [root README](../../README.md).

## Layout

| Path | Role |
|------|------|
| `app/` | Expo Router screens |
| `src/features/` | Auth, conversations, chat, search |
| `src/api/` | REST client |
| `src/websocket/` | STOMP + message sync |
| `src/crypto/` | X3DH / Double Ratchet (`@noble`) |
| `src/db/` | SQLite schema & repositories |
| `src/providers/` | Auth, DB, Query, chat connection |

## Docs

| Document | Use |
|----------|-----|
| [Root README](../../README.md) | Architecture & contracts |
| [CONTRIBUTING](../../CONTRIBUTING.md) | Local Expo / env setup |

## Quick start

See [CONTRIBUTING.md](../../CONTRIBUTING.md#frontend).
