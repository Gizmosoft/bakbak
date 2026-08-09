# Contributing to Gup

Thanks for contributing. This guide covers **local development setup** only. Product architecture lives in the root [`README.md`](README.md) and under [`documents/`](documents/).

---

## Prerequisites

| Tool | Notes |
|------|--------|
| **JDK 21** | Backend |
| **Maven 3.9+** | Or use `backend/mvnw` |
| **PostgreSQL 16+** | Schema applied by Flyway on startup |
| **Node.js 20+** | Frontend / Expo |
| **Expo Go** (optional) | Physical device testing |

---

## Repository layout (for contributors)

```
backend/           # Spring Boot
frontend/gup-ui/   # Expo app
documents/         # Design & ADRs (read-only reference)
```

---

## Backend

```bash
cd backend
```

1. Create a PostgreSQL database (e.g. `bakbak`).
2. Copy or create `.env` in `backend/` with at least:

```bash
DB_URL=jdbc:postgresql://localhost:5432/bakbak?sslmode=disable
DB_USER=...
DB_PASS=...
JWT_SECRET=...   # >= 32 bytes for HS256
```

If you reach Postgres through an SSH tunnel, keep `sslmode=disable` on the JDBC URL to avoid SSL handshake hangs over the tunnel.

3. Run:

```bash
set -a && source .env && set +a
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

API listens on **port 8080** by default. Flyway applies migrations `V1`–`V6` automatically.

### Tests

```bash
./mvnw test
```

Unit/web-layer tests do not require a live DB. Full context tests may need `TEST_DB_*` / `JWT_SECRET` as in `src/test/resources/application-test.properties`.

---

## Frontend

```bash
cd frontend/gup-ui
npm install
```

1. Create `.env.development` (see also project docs):

```bash
# Physical device / Expo Go: use your machine LAN IP, not localhost
EXPO_PUBLIC_API_URL=http://192.168.x.x:8080
```

| Target | Typical `EXPO_PUBLIC_API_URL` |
|--------|-------------------------------|
| iOS Simulator | `http://localhost:8080` |
| Android Emulator | `http://10.0.2.2:8080` |
| Physical phone (Expo Go) | `http://<your-lan-ip>:8080` |

Phone and computer must be on the same network. After changing the env file, restart Expo with a clean cache:

```bash
npx expo start --clear
```

2. Ensure the backend is running and reachable from the device before logging in.

### Crypto note (Expo Go)

Signal key generation requires `crypto.getRandomValues`. The app polyfills this via `expo-crypto` in `src/crypto/polyfill.ts` (imported from the root layout). If key tables stay empty after login, check Metro for bootstrap warnings.

### Crypto unit tests

```bash
npm run test:crypto
```

---

## Typical verification checklist

1. Register / login two users on two clients.
2. Confirm rows appear in `user_identity_keys`, `signed_pre_keys`, and `one_time_pre_keys`.
3. Exchange messages; UI shows plaintext; server `outbox.content` is `SIGNAL_V1` ciphertext until ACK.
4. Airplane-mode recipient: message sits in outbox; reconnect drains and ACKs.

---

## Pull requests

- Prefer small, focused PRs.
- Match existing package structure and naming (`uk.deadcatlab.bakbak`, `@/` imports).
- Do not commit secrets (`.env`, credentials).
- Update [`documents/`](documents/) or the root README when behavior or contracts change.
- Keep architecture decisions in [`documents/DECISIONS.md`](documents/DECISIONS.md) when introducing lasting trade-offs.

---

## Questions

Open an issue or discuss in review. For protocol depth, start with [`documents/E2E_ENCRYPTION.md`](documents/E2E_ENCRYPTION.md) and the root [`README.md`](README.md).
