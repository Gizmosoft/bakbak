# Gup Mobile App — Frontend Design Document

**Version:** 1.0  
**Scope:** React Native (Expo) mobile client for the Bakbak chat backend  
**Backend reference:** `../../backend/DESIGN.md`

---

## 1. Overview

Gup is a cross-platform mobile chat app built with **Expo SDK 54** and **TypeScript**. It connects to the existing Spring Boot backend (`backend/`) for authentication, user discovery, conversation management, message history, and real-time messaging over STOMP WebSocket.

### MVP Goals (client)

- Register and log in with JWT-based auth
- Search users by username prefix
- View a contact window (conversation list)
- Open a 1-to-1 chat, load paginated history, send/receive messages in real time

### Out of Scope (MVP)

- Group chats, attachments, push notifications, read receipts, typing indicators, password reset

---

## 2. Architecture

The app follows a **layered, feature-based** structure:

| Layer | Responsibility |
|---|---|
| `app/` | File-based routes (Expo Router). Thin screens that compose features. |
| `src/features/` | Domain UI and hooks (`auth`, `conversations`, `chat`, `search`). |
| `src/api/` | REST client and endpoint modules aligned with backend controllers. |
| `src/websocket/` | STOMP client for real-time send/receive. |
| `src/types/` | TypeScript types mirroring backend JSON DTOs. |
| `src/providers/` | App-wide context (auth session, TanStack Query, theme). |
| `src/components/` | Shared, domain-agnostic UI primitives. |

### Key libraries (planned)

| Concern | Library |
|---|---|
| Navigation | `expo-router` |
| Server state | `@tanstack/react-query` |
| Forms / validation | `react-hook-form`, `zod` |
| Token storage | `expo-secure-store` |
| WebSocket | `@stomp/stompjs` |

### Backend integration

| Client module | Backend |
|---|---|
| `auth.api.ts` | `POST /api/auth/register`, `POST /api/auth/login` |
| `users.api.ts` | `GET /api/users/me`, `GET /api/users/search` |
| `conversations.api.ts` | `POST /api/conversations`, `GET /api/conversations` |
| `messages.api.ts` | `GET /api/conversations/{id}/messages` |
| `websocket/chat.client.ts` | STOMP over `/ws`; send `/app/chat/{id}`; subscribe `/topic/conversation/{id}` |

All authenticated REST calls send `Authorization: Bearer <token>`. Errors follow the backend `ApiErrorResponse` shape (`status`, `error`, `message`, `path`).

### Environment

```
EXPO_PUBLIC_API_URL=http://localhost:8080   # dev; use 10.0.2.2 on Android emulator
```

CORS on the backend already allows local Expo origins (`application-dev.properties`).

---

## 3. Workflow

### 3.1 App bootstrap

```
App launch
    → read JWT from SecureStore
    → if valid: navigate to (app) stack
    → if missing/invalid: navigate to (auth) stack
```

Root `app/_layout.tsx` wraps the tree in providers (QueryClient, AuthProvider) and performs the auth gate.

### 3.2 Registration / login

```
User submits form (register or login)
    → POST /api/auth/*
    → store token + user in SecureStore / AuthContext
    → redirect to conversation list
```

On `401` from any API call: clear session and redirect to login.

### 3.3 Conversation list (contact window)

```
Screen mount
    → GET /api/conversations (TanStack Query)
    → render sorted list (lastMessageAt DESC)
    → tap row → navigate to chat/[conversationId]
```

Pull-to-refresh refetches the list. WebSocket broadcasts update the list cache when a new message arrives.

### 3.4 User search → start chat

```
User types query (debounced)
    → GET /api/users/search?q=...&limit=20
    → tap result
    → POST /api/conversations { targetUserId }
    → navigate to chat/[conversationId]
```

`POST /api/conversations` is idempotent — returns existing or new conversation.

### 3.5 Chat screen

```
Screen mount
    → GET /api/conversations/{id}/messages?limit=50 (newest page)
    → connect STOMP (if not connected) and subscribe /topic/conversation/{id}
    → render messages (inverted FlatList)

User sends message
    → SEND to /app/chat/{id} { content }
    → server broadcasts to /topic/conversation/{id}
    → append to local cache (optimistic or on broadcast)

Scroll up (load older)
    → GET .../messages?before=<oldest createdAt>&limit=50
    → prepend to list
```

On unmount: unsubscribe from the conversation topic (keep shared STOMP connection alive).

### 3.6 Data flow diagram

```
┌─────────────┐     REST (JWT)      ┌──────────────────┐
│  Expo App   │◄───────────────────►│  Spring Boot API │
│  (gup-ui)   │     STOMP /ws       │  (backend/)      │
└─────────────┘◄───────────────────►└──────────────────┘
       │
       ├── SecureStore (JWT)
       ├── TanStack Query (REST cache)
       └── STOMP subscriptions (live messages)
```

---

## 4. Project Structure

```
frontend/gup-ui/
├── app/                          # Routes (Expo Router)
│   ├── _layout.tsx               # Root: providers, auth gate, theme
│   ├── (auth)/
│   │   ├── _layout.tsx
│   │   ├── login.tsx
│   │   └── register.tsx
│   └── (app)/                    # Authenticated stack/tabs
│       ├── _layout.tsx
│       ├── index.tsx             # Conversation list
│       ├── search.tsx            # User search
│       └── chat/[conversationId].tsx
│
├── src/
│   ├── api/                      # REST integration
│   │   ├── client.ts             # fetch wrapper, base URL, auth header
│   │   ├── errors.ts             # ApiErrorResponse → app errors
│   │   ├── auth.api.ts
│   │   ├── users.api.ts
│   │   ├── conversations.api.ts
│   │   └── messages.api.ts
│   │
│   ├── websocket/
│   │   ├── chat.client.ts        # STOMP connect, send, subscribe
│   │   └── subscriptions.ts
│   │
│   ├── types/                    # Mirrors backend DTOs (camelCase JSON)
│   │   ├── auth.ts
│   │   ├── user.ts
│   │   ├── conversation.ts
│   │   ├── message.ts
│   │   └── api-error.ts
│   │
│   ├── features/
│   │   ├── auth/
│   │   │   ├── components/
│   │   │   ├── hooks/
│   │   │   └── schemas.ts        # zod validation
│   │   ├── conversations/
│   │   │   ├── components/
│   │   │   └── hooks/
│   │   ├── chat/
│   │   │   ├── components/
│   │   │   └── hooks/
│   │   └── search/
│   │
│   ├── components/               # Shared UI
│   │   ├── ui/                   # Button, Input, Avatar, etc.
│   │   └── layout/
│   │
│   ├── hooks/                    # Cross-cutting hooks
│   ├── lib/                      # Utilities (dates, formatting)
│   ├── constants/                # API paths, limits, theme tokens
│   ├── config/
│   │   └── env.ts                # EXPO_PUBLIC_API_URL resolution
│   └── providers/                # QueryClient, AuthProvider, ThemeProvider
│
├── assets/
├── app.json
├── package.json
├── tsconfig.json                 # path alias: "@/*" → "src/*"
└── DESIGN.md
```

Add folders incrementally — create a feature directory when a domain gets its second screen or hook.

---

## 5. Implementation Plan

Step-by-step plan to connect this app to the existing backend. Complete each phase before moving on; later phases depend on earlier ones.

### Phase 0 — Project scaffold

1. Install dependencies: `expo-router`, `@tanstack/react-query`, `expo-secure-store`, `react-hook-form`, `zod`, `@stomp/stompjs`.
2. Configure Expo Router (`app/` directory, update `package.json` main entry).
3. Add `tsconfig` path alias `@/*` → `src/*`.
4. Add `.env.development` with `EXPO_PUBLIC_API_URL`.
5. Create `src/config/env.ts` and `src/constants/` (API paths matching backend routes).

### Phase 1 — Types and API client

1. Define TypeScript types in `src/types/` from `backend/DESIGN.md` §7 (`AuthResponse`, `UserResponse`, `ConversationResponse`, `MessageResponse`, `ApiErrorResponse`, request bodies).
2. Implement `src/api/client.ts`: base URL, JSON headers, JWT injection, error parsing.
3. Implement `src/api/errors.ts` with typed error classes (`ApiError`, `UnauthorizedError`, etc.).
4. Implement `auth.api.ts`, `users.api.ts`, `conversations.api.ts`, `messages.api.ts` — one function per endpoint.
5. Smoke-test against a running backend (`mvn spring-boot:run` in `backend/`) using register/login from a temporary script or screen.

### Phase 2 — Auth and session

1. Add `src/lib/token-storage.ts` wrapping `expo-secure-store`.
2. Add `AuthProvider` in `src/providers/` (user, token, login, logout, bootstrap).
3. Build `(auth)/login.tsx` and `(auth)/register.tsx` with zod schemas matching backend validation rules.
4. Wire root `app/_layout.tsx` auth gate: unauthenticated → `(auth)`, authenticated → `(app)`.
5. Handle global `401`: clear token and redirect to login.

### Phase 3 — Conversation list

1. Set up TanStack Query provider in root layout.
2. Add `useConversations` hook (`GET /api/conversations`).
3. Build `(app)/index.tsx` with conversation list UI (avatar, display name, last message preview, timestamp).
4. Add pull-to-refetch and empty/loading/error states.
5. Navigate to `chat/[conversationId]` on row tap.

### Phase 4 — User search and new conversations

1. Add debounced `useUserSearch` hook (`GET /api/users/search`).
2. Build `(app)/search.tsx` with search input and result list.
3. On result tap: call `POST /api/conversations`, then navigate to chat screen.
4. Invalidate conversation list query after creating a conversation.

### Phase 5 — Chat screen (REST history)

1. Build `chat/[conversationId].tsx` shell with header (other user's display name).
2. Add `useMessages(conversationId)` with cursor pagination (`before`, `limit`).
3. Render inverted message list with sender-aware bubbles.
4. Load older messages on scroll-to-top.

### Phase 6 — Real-time messaging (WebSocket)

1. Implement `src/websocket/chat.client.ts`: connect to `{API_URL}/ws` with JWT in CONNECT headers.
2. Subscribe to `/topic/conversation/{id}` when chat screen mounts; unsubscribe on unmount.
3. Send messages via `/app/chat/{id}` with `{ content }` payload.
4. Merge incoming broadcasts into TanStack Query cache (open chat) and conversation list cache (update preview).
5. Handle reconnect on app foreground / network restore.
6. Surface errors from `/user/queue/errors` if present.

### Phase 7 — Polish and hardening

1. Consistent loading, error, and empty states across all screens.
2. Input validation aligned with backend (username, password, message length 1–4000).
3. Keyboard-safe chat input; maintain scroll position on new messages.
4. Log out clears SecureStore, Query cache, and STOMP connection.
5. Manual test matrix: two users, two devices/emulators, send/receive, pagination, search, re-login after token expiry.

### Phase 8 — Testing (optional for MVP, recommended before release)

1. Unit tests for API client error handling and zod schemas.
2. Component tests for auth forms and message list rendering (MSW for REST mocking).
3. Integration test: register → login → create conversation → fetch messages against Testcontainers backend or dev server.

---

## 6. Local Development Checklist

1. Start PostgreSQL and set `DB_URL`, `DB_USER`, `DB_PASS`, `JWT_SECRET` in `backend/.env`.
2. Run backend: `./mvnw spring-boot:run -Dspring-boot.run.profiles=dev` from `backend/`.
3. Set `EXPO_PUBLIC_API_URL` in `frontend/gup-ui/.env.development` (use `http://10.0.2.2:8080` for Android emulator).
4. Run app: `npx expo start` from `frontend/gup-ui/`.
5. Test with two accounts (register twice) on separate simulators or Expo Go devices.

---

## 7. References

- Backend API & WebSocket contract: `../../backend/DESIGN.md` §7–8
- Expo SDK 54 docs: https://docs.expo.dev/versions/v54.0.0/
- CORS config for local dev: `backend/src/main/resources/application-dev.properties`
