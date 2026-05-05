# Bakbak Chat App MVP — Backend Design Document

**Version:** 1.0
**Last Updated:** April 19, 2026
**Scope:** Backend (Spring Boot) + Database (PostgreSQL) only
**Author:** Kartikey

---

## Table of Contents

1. [Project Overview](#1-project-overview)
2. [Tech Stack](#2-tech-stack)
3. [Functional Requirements](#3-functional-requirements)
4. [Non-Functional Requirements](#4-non-functional-requirements)
5. [High-Level Design](#5-high-level-design)
6. [Database Design](#6-database-design)
7. [API Contract](#7-api-contract)
8. [WebSocket Contract](#8-websocket-contract)
9. [Security Design](#9-security-design)
10. [Project Structure](#10-project-structure)
11. [Service Layer Design](#11-service-layer-design)
12. [Error Handling](#12-error-handling)
13. [Configuration & Profiles](#13-configuration--profiles)
14. [Implementation Roadmap](#14-implementation-roadmap)
15. [Testing Strategy](#15-testing-strategy)
16. [Future Enhancements](#16-future-enhancements)

---

## 1. Project Overview

A cross-platform mobile chat application with a Java Spring Boot backend and PostgreSQL database. This document covers the backend + database layer only. The mobile client (React Native / Expo) will be implemented in a later phase.

### MVP Goals
- Secure user authentication (signup + login)
- User discovery via username search
- Real-time 1-to-1 text messaging
- Persistent chat history with a contact window showing all active conversations

### Out of Scope (for MVP)
- Group chats / chat rooms
- Multimedia / file attachments
- Message edits, deletes, or reactions
- End-to-end encryption
- Push notifications
- Forgot password / email verification flows
- Read receipts & typing indicators

---

## 2. Tech Stack

| Layer | Technology | Version |
|---|---|---|
| Language | Java | 21 (LTS) |
| Framework | Spring Boot | 4.0.5 |
| Build Tool | Maven | 3.9+ |
| Database | PostgreSQL | 16+ |
| ORM | Spring Data JPA / Hibernate | Bundled with Spring Boot |
| Security | Spring Security + JWT (jjwt) | Bundled + 0.12.x |
| Password Hashing | BCrypt (via Spring Security) | — |
| Real-time | Spring WebSocket + STOMP | Bundled |
| Validation | Jakarta Bean Validation | Bundled |
| Logging | SLF4J + Logback | Bundled |
| Testing | JUnit 5 + Mockito + Testcontainers | Bundled |

### Required Dependencies (Maven)
- `spring-boot-starter-web`
- `spring-boot-starter-websocket`
- `spring-boot-starter-data-jpa`
- `spring-boot-starter-security`
- `spring-boot-starter-validation`
- `postgresql` (runtime)
- `io.jsonwebtoken:jjwt-api`, `jjwt-impl`, `jjwt-jackson`
- `lombok` (optional, for boilerplate reduction)
- `spring-boot-starter-test`
- `org.testcontainers:postgresql` (test scope)

---

## 3. Functional Requirements

### 3.1 Authentication
- Users sign up with username, email, password, display name, and date of birth
- Users log in using email + password
- Successful auth returns a JWT token for subsequent requests
- All non-auth endpoints require a valid JWT

### 3.2 User Discovery
- Authenticated users can search for other users by username (prefix match, case-insensitive)
- Search results exclude the requesting user
- Returns minimal user info (id, username, display name)

### 3.3 Messaging
- 1-to-1 text messages only
- Messages are delivered in real-time via WebSocket
- Messages persist in the database for history
- No edits, deletions, or attachments

### 3.4 Chat Persistence
- A conversation is created the first time two users message each other
- Once created, a conversation appears permanently in both users' contact windows
- Contact window lists all conversations sorted by most recent activity
- Full message history is available for each conversation, paginated

---

## 4. Non-Functional Requirements

| Category | Requirement |
|---|---|
| Performance | Contact window load < 200ms for users with up to 500 conversations |
| Scalability | Architecture supports up to 100K users without redesign |
| Security | Passwords hashed with BCrypt (cost 12); JWTs signed with HS256 |
| Reliability | Message persistence is transactional; no silent message loss |
| Observability | Structured logs; clear error responses |
| Portability | Config externalized via environment variables |

---

## 5. High-Level Design

### 5.1 System Context

```
┌─────────────────┐         HTTPS/REST        ┌──────────────────┐
│  React Native   │ ────────────────────────► │  Spring Boot     │
│  Mobile App     │ ◄──────────────────────── │  Backend (Java)  │
│  (Future)       │         WebSocket/STOMP   │                  │
└─────────────────┘                           └──────────────────┘
                                                      │
                                                      │ JDBC
                                                      ▼
                                              ┌──────────────────┐
                                              │  PostgreSQL      │
                                              │  Database        │
                                              └──────────────────┘
```

### 5.2 Core Domain Entities

1. **User** — authenticated identity with a unique username
2. **Conversation** — a 1-to-1 chat thread between two users (first-class entity)
3. **ConversationParticipant** — join entity linking users to conversations
4. **Message** — a single text message within a conversation

### 5.3 Key Design Decisions

| Decision | Rationale |
|---|---|
| Conversation as first-class entity | Clean contact window query; easy extension to group chats |
| `participant_key` unique column | Prevents duplicate 1:1 conversations under race conditions |
| Denormalized `last_message_at` on conversation | One-query contact window sort without aggregate joins |
| Cursor-based pagination for messages | Stable during active chats; scales indefinitely |
| DTOs separate from JPA entities | Prevents password leakage; clean API evolution |
| Stateless JWT auth (no refresh token for MVP) | Simpler initial implementation; refresh added later |
| STOMP over WebSocket | Spring's built-in broker; pub/sub by topic fits natively |
| Compound index `(conversation_id, created_at DESC)` | History queries stay O(log n) regardless of total message count |

---

## 6. Database Design

### 6.1 Schema

#### `users`
| Column | Type | Constraints |
|---|---|---|
| `id` | `BIGSERIAL` | PK |
| `username` | `VARCHAR(30)` | UNIQUE, NOT NULL |
| `email` | `VARCHAR(255)` | UNIQUE, NOT NULL |
| `password_hash` | `VARCHAR(255)` | NOT NULL |
| `display_name` | `VARCHAR(100)` | NULL |
| `date_of_birth` | `DATE` | NOT NULL |
| `created_at` | `TIMESTAMPTZ` | NOT NULL DEFAULT `now()` |
| `updated_at` | `TIMESTAMPTZ` | NOT NULL DEFAULT `now()` |

**Indexes:**
- `UNIQUE INDEX idx_users_username ON users(username)`
- `UNIQUE INDEX idx_users_email ON users(email)`
- `INDEX idx_users_username_lower ON users(LOWER(username))` — case-insensitive search

#### `conversations`
| Column | Type | Constraints |
|---|---|---|
| `id` | `BIGSERIAL` | PK |
| `participant_key` | `VARCHAR(50)` | UNIQUE, NOT NULL |
| `created_at` | `TIMESTAMPTZ` | NOT NULL DEFAULT `now()` |
| `last_message_at` | `TIMESTAMPTZ` | NULL |

> **`participant_key` format:** `"<smallerUserId>:<largerUserId>"` — e.g. `"7:42"`. The unique constraint prevents duplicate conversations between the same two users even under concurrent creation.

#### `conversation_participants`
| Column | Type | Constraints |
|---|---|---|
| `conversation_id` | `BIGINT` | FK → `conversations.id`, NOT NULL |
| `user_id` | `BIGINT` | FK → `users.id`, NOT NULL |
| `joined_at` | `TIMESTAMPTZ` | NOT NULL DEFAULT `now()` |

**Constraints:**
- `PRIMARY KEY (conversation_id, user_id)`
- `INDEX idx_participants_user ON conversation_participants(user_id)` — for "list my conversations"

#### `messages`
| Column | Type | Constraints |
|---|---|---|
| `id` | `BIGSERIAL` | PK |
| `conversation_id` | `BIGINT` | FK → `conversations.id`, NOT NULL |
| `sender_id` | `BIGINT` | FK → `users.id`, NOT NULL |
| `content` | `TEXT` | NOT NULL, max 4000 chars (app-enforced) |
| `created_at` | `TIMESTAMPTZ` | NOT NULL DEFAULT `now()` |

**Indexes:**
- `INDEX idx_messages_conversation_created ON messages(conversation_id, created_at DESC)` — critical for history pagination

### 6.2 Entity Relationship Diagram

```
┌─────────────┐           ┌──────────────────────────┐
│   users     │           │ conversation_participants│
│─────────────│     ┌────►│──────────────────────────│
│ id (PK)     │◄────┘     │ conversation_id (FK)     │◄──┐
│ username    │           │ user_id (FK)             │   │
│ email       │           │ joined_at                │   │
│ password... │           └──────────────────────────┘   │
│ ...         │                                          │
└─────────────┘           ┌──────────────────┐           │
      ▲                   │  conversations   │           │
      │                   │──────────────────│           │
      │                   │ id (PK)          │───────────┘
      │                   │ participant_key  │
      │                   │ created_at       │
      │                   │ last_message_at  │
      │                   └──────────────────┘
      │                          ▲
      │                          │
      │                   ┌──────────────────┐
      │                   │    messages      │
      └───────────────────│──────────────────│
                          │ id (PK)          │
                          │ conversation_id  │
                          │ sender_id (FK)   │
                          │ content          │
                          │ created_at       │
                          └──────────────────┘
```

### 6.3 Flyway Migration (V1)

```sql
-- V1__initial_schema.sql

CREATE TABLE users (
    id              BIGSERIAL PRIMARY KEY,
    username        VARCHAR(30) NOT NULL UNIQUE,
    email           VARCHAR(255) NOT NULL UNIQUE,
    password_hash   VARCHAR(255) NOT NULL,
    display_name    VARCHAR(100),
    date_of_birth   DATE NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_users_username_lower ON users(LOWER(username));

CREATE TABLE conversations (
    id               BIGSERIAL PRIMARY KEY,
    participant_key  VARCHAR(50) NOT NULL UNIQUE,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_message_at  TIMESTAMPTZ
);

CREATE TABLE conversation_participants (
    conversation_id  BIGINT NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
    user_id          BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    joined_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (conversation_id, user_id)
);

CREATE INDEX idx_participants_user ON conversation_participants(user_id);

CREATE TABLE messages (
    id               BIGSERIAL PRIMARY KEY,
    conversation_id  BIGINT NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
    sender_id        BIGINT NOT NULL REFERENCES users(id),
    content          TEXT NOT NULL,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_messages_conversation_created
    ON messages(conversation_id, created_at DESC);
```

---

## 7. API Contract

### 7.1 Authentication Endpoints (Public)

#### `POST /api/auth/register`

**Request:**
```json
{
  "username": "kartikey",
  "email": "k@example.com",
  "password": "Str0ngPass!",
  "displayName": "Kartikey",
  "dateOfBirth": "1998-05-12"
}
```

**Response `201 Created`:**
```json
{
  "token": "eyJhbGc...",
  "user": {
    "id": 1,
    "username": "kartikey",
    "email": "k@example.com",
    "displayName": "Kartikey"
  }
}
```

**Errors:** `400` (validation), `409` (username/email taken)

#### `POST /api/auth/login`

**Request:**
```json
{ "email": "k@example.com", "password": "Str0ngPass!" }
```

**Response `200 OK`:**
```json
{ "token": "...", "user": { ... } }
```

**Errors:** `401` (invalid credentials)

### 7.2 User Endpoints (Authenticated)

#### `GET /api/users/me`
Returns current user's profile.

**Response `200 OK`:**
```json
{ "id": 1, "username": "kartikey", "email": "k@example.com", "displayName": "Kartikey" }
```

#### `GET /api/users/search?q=kart&limit=20`
Returns users whose username starts with `q` (case-insensitive), excluding the requesting user.

**Response `200 OK`:**
```json
[
  { "id": 5, "username": "kartik99", "displayName": "Kartik" },
  { "id": 12, "username": "kartikey_dev", "displayName": "Kartikey D." }
]
```

### 7.3 Conversation Endpoints (Authenticated)

#### `POST /api/conversations`
Creates or fetches existing 1:1 conversation (idempotent).

**Request:**
```json
{ "targetUserId": 42 }
```

**Response `200 OK` (existing) or `201 Created` (new):**
```json
{
  "conversationId": 17,
  "otherUser": { "id": 42, "username": "alice", "displayName": "Alice" },
  "lastMessage": null,
  "lastMessageAt": null
}
```

**Errors:** `400` (self-conversation), `404` (target user not found)

#### `GET /api/conversations`
Contact window — all conversations for current user, sorted by `last_message_at DESC` (nulls last).

**Response `200 OK`:**
```json
[
  {
    "conversationId": 17,
    "otherUser": { "id": 42, "username": "alice", "displayName": "Alice" },
    "lastMessage": { "content": "see you at 5", "senderId": 42 },
    "lastMessageAt": "2026-04-19T14:32:00Z"
  }
]
```

#### `GET /api/conversations/{id}/messages?before=<iso-timestamp>&limit=50`
Paginated message history for a conversation. Cursor-based pagination using `created_at`.

**Query params:**
- `before` (optional): ISO timestamp; returns messages before this. Omit for newest.
- `limit` (optional, default 50, max 100): number of messages.

**Response `200 OK`:**
```json
[
  {
    "id": 1001,
    "conversationId": 17,
    "senderId": 42,
    "content": "see you at 5",
    "createdAt": "2026-04-19T14:32:00Z"
  }
]
```

**Errors:** `403` (not a participant), `404` (conversation not found)

---

## 8. WebSocket Contract

### 8.1 Connection
- **Endpoint:** `ws://host/ws` (SockJS fallback optional)
- **Protocol:** STOMP over WebSocket
- **Auth:** JWT passed in STOMP `CONNECT` frame `Authorization` header

### 8.2 Topics & Destinations

| Direction | Destination | Purpose |
|---|---|---|
| Server → Client | `/topic/conversation/{conversationId}` | Broadcast messages to all participants |
| Server → Client (user-specific) | `/user/queue/errors` | Private error channel |
| Client → Server | `/app/chat/{conversationId}` | Send a message |

### 8.3 Send Message Payload

**Client sends to `/app/chat/{conversationId}`:**
```json
{ "content": "Hello!" }
```

**Server broadcasts to `/topic/conversation/{conversationId}`:**
```json
{
  "id": 1002,
  "conversationId": 17,
  "senderId": 1,
  "senderUsername": "kartikey",
  "content": "Hello!",
  "createdAt": "2026-04-19T18:30:00Z"
}
```

### 8.4 Authorization Rules

| Action | Rule |
|---|---|
| Connect | Valid JWT required |
| Subscribe to `/topic/conversation/{id}` | User must be a participant of conversation |
| Send to `/app/chat/{id}` | User must be a participant of conversation |

---

## 9. Security Design

### 9.1 Password Storage
- **Algorithm:** BCrypt
- **Cost factor:** 12
- Passwords never logged, never returned in any DTO

### 9.2 JWT Strategy
- **Algorithm:** HS256
- **Payload:** `{ sub: userId, username, iat, exp }`
- **Expiration:** 7 days
- **Secret:** from env var `JWT_SECRET`, minimum 256 bits
- **Refresh tokens:** not in MVP (user re-logs in)

### 9.3 HTTP Authentication Flow

```
Request arrives
     ↓
JwtAuthFilter extracts "Authorization: Bearer <token>"
     ↓
Validates signature + expiration using JwtUtil
     ↓
Loads user details (UserDetailsServiceImpl)
     ↓
Sets SecurityContext with authenticated principal
     ↓
Controller accesses user via @AuthenticationPrincipal
```

### 9.4 WebSocket Authentication
1. Register a `ChannelInterceptor` on the inbound WS channel
2. On `CONNECT` frame, extract JWT from headers
3. Validate and attach `Principal` to the STOMP session
4. On `SUBSCRIBE` and `SEND`, verify principal and enforce participant check

### 9.5 Authorization Matrix

| Action | Authorization Rule |
|---|---|
| Register / Login | Public |
| Search users | Any authenticated user |
| View conversation list | Returns only own conversations |
| View specific conversation | Must be participant |
| Send message | Must be participant |
| Subscribe to conversation WS topic | Must be participant |

### 9.6 Input Validation
All request DTOs use Jakarta Bean Validation. Key rules:
- Username: 3–30 chars, alphanumeric + underscore
- Email: valid email format
- Password: 8–100 chars (no complexity requirement for MVP; consider zxcvbn later)
- DOB: must be in the past
- Message content: 1–4000 chars

---

## 10. Project Structure

```
src/
└── main/
    ├── java/uk/deadcatlab/bakbak/
    │   │
    │   ├── config/
    │   │   ├── SecurityConfig.java
    │   │   ├── WebSocketConfig.java
    │   │   └── CorsConfig.java
    │   │
    │   ├── controller/
    │   │   ├── AuthController.java
    │   │   ├── UserController.java
    │   │   ├── ConversationController.java
    │   │   └── MessageController.java
    │   │
    │   ├── websocket/
    │   │   ├── ChatController.java
    │   │   └── WebSocketAuthInterceptor.java
    │   │
    │   ├── service/
    │   │   ├── AuthService.java
    │   │   ├── UserService.java
    │   │   ├── ConversationService.java
    │   │   └── MessageService.java
    │   │
    │   ├── repository/
    │   │   ├── UserRepository.java
    │   │   ├── ConversationRepository.java
    │   │   ├── ConversationParticipantRepository.java
    │   │   └── MessageRepository.java
    │   │
    │   ├── model/
    │   │   ├── User.java
    │   │   ├── Conversation.java
    │   │   ├── ConversationParticipant.java
    │   │   └── Message.java
    │   │
    │   ├── dto/
    │   │   ├── request/
    │   │   │   ├── RegisterRequest.java
    │   │   │   ├── LoginRequest.java
    │   │   │   ├── CreateConversationRequest.java
    │   │   │   └── SendMessageRequest.java
    │   │   └── response/
    │   │       ├── AuthResponse.java
    │   │       ├── UserResponse.java
    │   │       ├── UserPublicResponse.java
    │   │       ├── ConversationResponse.java
    │   │       └── MessageResponse.java
    │   │
    │   ├── security/
    │   │   ├── JwtUtil.java
    │   │   ├── JwtAuthFilter.java
    │   │   └── UserDetailsServiceImpl.java
    │   │
    │   ├── exception/
    │   │   ├── GlobalExceptionHandler.java
    │   │   ├── ResourceNotFoundException.java
    │   │   ├── UnauthorizedException.java
    │   │   └── ForbiddenException.java
    │   │
    │   └── BakbakApplication.java
    │
    └── resources/
        ├── application.properties
        ├── application-dev.properties
        ├── application-prod.properties
        └── db/migration/
            └── V1__initial_schema.sql
```

---

## 11. Service Layer Design

### 11.1 AuthService
| Method | Signature | Responsibility |
|---|---|---|
| `register` | `AuthResponse register(RegisterRequest)` | Validate uniqueness, hash password, save user, issue JWT |
| `login` | `AuthResponse login(LoginRequest)` | Verify credentials, issue JWT |

### 11.2 UserService
| Method | Signature | Responsibility |
|---|---|---|
| `findById` | `User findById(Long id)` | Fetch user or throw `ResourceNotFoundException` |
| `findByEmail` | `User findByEmail(String email)` | Used by auth |
| `searchByUsername` | `List<UserPublicResponse> searchByUsername(String prefix, Long excludeUserId, int limit)` | Case-insensitive prefix search (no email in results) |
| `getCurrentUser` | `UserResponse getCurrentUser(Long userId)` | Profile endpoint |

### 11.3 ConversationService
| Method | Signature | Responsibility |
|---|---|---|
| `getOrCreate` | `ConversationResponse getOrCreate(Long userA, Long userB)` | Returns existing or creates new conversation idempotently |
| `listForUser` | `List<ConversationResponse> listForUser(Long userId)` | Contact window data |
| `assertParticipant` | `void assertParticipant(Long convId, Long userId)` | Authorization helper; throws `ForbiddenException` if not a member |

**`getOrCreate` logic:**
```
if userA == userB: throw BadRequest
key = buildParticipantKey(userA, userB)  // "min:max"

try:
    @Transactional:
        conv = new Conversation(participant_key = key)
        save(conv)
        save(new Participant(conv.id, userA))
        save(new Participant(conv.id, userB))
        return conv
catch DataIntegrityViolationException:  // unique constraint hit
    return repository.findByParticipantKey(key)
```

### 11.4 MessageService
| Method | Signature | Responsibility |
|---|---|---|
| `send` | `MessageResponse send(Long convId, Long senderId, String content)` | Persist message + update `last_message_at` (transactional) |
| `getHistory` | `List<MessageResponse> getHistory(Long convId, Instant before, int limit)` | Paginated fetch using cursor |

### 11.5 Transaction Boundaries
- **Register:** default transaction OK
- **getOrCreate conversation:** `@Transactional` (3 inserts)
- **Send message:** `@Transactional` (1 insert + 1 update)
- **All read-only queries:** `@Transactional(readOnly = true)`

---

## 12. Error Handling

### 12.1 Standard Error Response Format
```json
{
  "timestamp": "2026-04-19T18:22:00Z",
  "status": 400,
  "error": "Validation Failed",
  "message": "Username must be 3-30 characters",
  "path": "/api/auth/register"
}
```

### 12.2 Exception → Status Mapping
Implemented centrally in `GlobalExceptionHandler` with `@RestControllerAdvice`.

| Exception | HTTP Status |
|---|---|
| `MethodArgumentNotValidException` | `400` |
| `ResourceNotFoundException` | `404` |
| `UnauthorizedException` / `BadCredentialsException` | `401` |
| `ForbiddenException` / `AccessDeniedException` | `403` |
| `DataIntegrityViolationException` | `409` |
| `IllegalArgumentException` | `400` |
| `Exception` (catch-all) | `500` (generic message, no stack trace leak) |

---

## 13. Configuration & Profiles

### 13.1 `application.properties` (shared)
```properties
spring.application.name=bakbak
spring.jpa.hibernate.ddl-auto=validate
spring.jpa.show-sql=false
spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect

spring.flyway.enabled=true
spring.flyway.locations=classpath:db/migration

jwt.secret=${JWT_SECRET}
jwt.expiration-ms=604800000
```

### 13.2 `application-dev.properties`
```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/bakbak
spring.datasource.username=<db_username>
spring.datasource.password=<db_password>

jwt.secret=${JWT_SECRET:dev-local-jwt-secret-change-me}
jwt.expiration-ms=604800000 
```

### 13.3 `application-prod.properties`
```properties
spring.datasource.url=${DB_URL}
spring.datasource.username=${DB_USER}
spring.datasource.password=${DB_PASS}

logging.level.root=WARN
logging.level.uk.deadcatlab.bakbak=INFO
```

### 13.4 Required Environment Variables (prod)
- `DB_URL` — JDBC URL
- `DB_USER` — DB username
- `DB_PASS` — DB password
- `JWT_SECRET` — min 256-bit secret
- `SPRING_PROFILES_ACTIVE=prod|dev`

---

## 14. Implementation Roadmap

Build in this order — each step depends on the previous.

### Phase 1: Foundation
- [X] **Step 1:** Generate project via Spring Initializr (Boot 4.0.5, Java 21) with required dependencies
- [X] **Step 2:** Set up `application.properties` + profiles + PostgreSQL connection
- [X] **Step 3:** Add Flyway migration `V1__initial_schema.sql`
- [X] **Step 4:** Verify the app boots and migration runs against a local Postgres

### Phase 2: Domain Layer
- [X] **Step 5:** Create JPA entities (`User`, `Conversation`, `ConversationParticipant`, `Message`)
- [X] **Step 6:** Create Spring Data repositories for each entity
- [X] **Step 7:** Add custom query for `findByParticipantKey` in `ConversationRepository`

### Phase 3: Security Layer
- [X] **Step 8:** Implement `JwtUtil` (generate, parse, validate)
- [X] **Step 9:** Implement `UserDetailsServiceImpl`
- [X] **Step 10:** Implement `JwtAuthFilter`
- [X] **Step 11:** Configure `SecurityConfig` (filter chain, public/protected routes, BCrypt)

### Phase 4: Auth Feature
- [X] **Step 12:** Create `RegisterRequest`, `LoginRequest`, `AuthResponse`, `UserResponse` DTOs with validation
- [X] **Step 13:** Implement `AuthService` (register, login)
- [X] **Step 14:** Implement `AuthController`
- [X] **Step 15:** Smoke test `/register` + `/login` with Postman

### Phase 5: User Feature
- [X] **Step 16:** Implement `UserService` (findById, searchByUsername, getCurrentUser)
- [X] **Step 17:** Implement `UserController` (`/me`, `/search`)
- [X] **Step 18:** Smoke test both endpoints

### Phase 6: Conversation Feature
- [ ] **Step 19:** Create `CreateConversationRequest`, `ConversationResponse` DTOs
- [ ] **Step 20:** Implement `ConversationService` (`getOrCreate`, `listForUser`, `assertParticipant`)
- [ ] **Step 21:** Implement `ConversationController` (POST, GET list)
- [ ] **Step 22:** Smoke test conversation creation (including duplicate prevention)

### Phase 7: Message History (REST)
- [ ] **Step 23:** Create `MessageResponse` DTO
- [ ] **Step 24:** Implement `MessageService.getHistory` with cursor pagination
- [ ] **Step 25:** Implement `MessageController` for `/api/conversations/{id}/messages`
- [ ] **Step 26:** Smoke test pagination

### Phase 8: Real-time Messaging (WebSocket)
- [ ] **Step 27:** Configure `WebSocketConfig` (STOMP broker, `/ws` endpoint)
- [ ] **Step 28:** Implement `WebSocketAuthInterceptor` (JWT validation on CONNECT)
- [ ] **Step 29:** Implement `ChatController` with `@MessageMapping`
- [ ] **Step 30:** Implement `MessageService.send` and broadcast logic
- [ ] **Step 31:** Add participant-check authorization on SUBSCRIBE
- [ ] **Step 32:** Smoke test with two WebSocket clients (wscat or Postman)

### Phase 9: Polish
- [ ] **Step 33:** Implement `GlobalExceptionHandler` and custom exception classes
- [ ] **Step 34:** Configure `CorsConfig` for future React Native access
- [ ] **Step 35:** Add structured logging; remove debug logs from prod config
- [ ] **Step 36:** End-to-end smoke test of entire flow

### Phase 10: Deployment Prep (optional for MVP)
- [ ] **Step 37:** Dockerize the app (Dockerfile + docker-compose for Postgres)
- [ ] **Step 38:** Deploy backend + Postgres to home lab server (`deadcatlab.uk`)
- [ ] **Step 39:** Configure production env vars and test remotely

---

## 15. Testing Strategy

### 15.1 Unit Tests
Target: `service/` layer primarily.
- Mock repositories with Mockito
- Cover business logic edge cases:
  - Register with taken username / email
  - Login with wrong password
  - `getOrCreate` when conversation exists vs new
  - Pagination boundary cases

### 15.2 Integration Tests
Use **Testcontainers** to spin up real Postgres for integration tests.
- Cover repositories with real queries
- Cover controller + service + repo slices for happy paths
- Test JWT filter end-to-end (with a test `RestTemplate`)

### 15.3 Manual / Smoke Testing
After each phase, smoke test with Postman:
- Auth flow (register → login → use token)
- User search
- Conversation creation (including duplicate-prevention test)
- Message send + history fetch
- WebSocket connection + send/receive (use `wscat`)

### 15.4 Suggested Test Coverage Targets
| Layer | Target Coverage |
|---|---|
| Service | 80%+ |
| Controller | 60%+ (happy paths + key error cases) |
| Security / Filter | 70%+ |
| Repository | via integration tests only |

---

## 16. Future Enhancements

These are intentionally out of MVP scope but the design supports them without major rework.

| Feature | Notes |
|---|---|
| Read receipts | Add `read_at` column on `messages` + per-participant tracking table |
| Typing indicators | Transient WebSocket-only events, no persistence |
| Group chats | Already supported by schema — just allow >2 participants per conversation |
| Message attachments | New `attachments` table + S3-compatible storage |
| Push notifications | FCM/APNs integration on message send |
| Refresh tokens | Separate `refresh_tokens` table, new `/api/auth/refresh` endpoint |
| End-to-end encryption | Client-side key management + encrypted message bodies |
| Client-side SQLite cache | React Native side; server remains source of truth |
| Message edits / deletes | Add `edited_at`, `deleted_at` columns; audit table for edit history |
| Forgot password | Email service integration + reset token table |
| Email verification | Verification token table + middleware check |
| Horizontal scaling | Replace in-memory WebSocket broker with Redis Pub/Sub |

---

## Appendix A: Quick Reference — HTTP Status Codes Used

| Code | Meaning | When |
|---|---|---|
| `200` | OK | Successful GET, idempotent POST returning existing resource |
| `201` | Created | New resource created |
| `400` | Bad Request | Validation errors, malformed input |
| `401` | Unauthorized | Missing/invalid JWT, bad login credentials |
| `403` | Forbidden | Authenticated but not authorized (e.g. non-participant) |
| `404` | Not Found | Resource doesn't exist |
| `409` | Conflict | Unique constraint violation (email/username taken) |
| `500` | Server Error | Unexpected internal error |

---

## Appendix B: Glossary

- **BCrypt** — Adaptive password hashing function; slow-by-design to resist brute force
- **JWT** — JSON Web Token; signed token carrying user identity claims
- **STOMP** — Simple Text Oriented Messaging Protocol; pub/sub layer over WebSocket
- **Cursor pagination** — Pagination using a value (like timestamp) instead of offset; stable under inserts
- **Idempotent** — Same request can be repeated safely with same result (e.g. `POST /conversations`)
- **Flyway** — Database migration tool; versioned SQL scripts applied in order

---