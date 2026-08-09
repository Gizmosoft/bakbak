# Gup backend

Spring Boot service for authentication, conversation metadata, presence, Signal **public** key storage, and **blind** WebSocket/STOMP message relay.

## Role in the system

- Issues JWTs and enforces participant authorization
- Stores users, conversations, presence, and temporary outbox rows in PostgreSQL
- Relays opaque chat payloads (`SIGNAL_V1` ciphertext); does not decrypt message bodies
- Publishes and serves Signal prekey bundles (public material only)

Message history is **not** stored permanently on the server. See the root [README](../README.md) for architecture.

## Package

Java package root: `uk.deadcatlab.bakbak`

| Area | Path |
|------|------|
| REST | `.../controller/` |
| STOMP | `.../websocket/` |
| Domain services | `.../service/` |
| Flyway | `src/main/resources/db/migration/` |

## Docs

| Document | Use |
|----------|-----|
| [Root README](../README.md) | System architecture & contracts |
| [CONTRIBUTING](../CONTRIBUTING.md) | How to run this service locally |
| [documents/DECISIONS.md](../documents/DECISIONS.md) | ADRs |

## Quick start

See [CONTRIBUTING.md](../CONTRIBUTING.md#backend).
