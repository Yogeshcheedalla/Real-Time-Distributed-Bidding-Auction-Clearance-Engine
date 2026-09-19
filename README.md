# BidVelocity — Real-Time Distributed Bidding & Auction Clearance Engine

Where every bid moves in real time. Production-grade microservices auction platform:
**Java 21 · Spring Boot 3.5 · Spring Cloud 2025 · PostgreSQL (database-per-service) · Eureka · Spring Cloud Gateway · JWT + Google OAuth2 · Flyway · (optional) Redis & Kafka.**

> Status legend — ✅ implemented & tested · 🚧 in progress · ⌛ planned

| Service | Port | Status |
|---|---|---|
| eureka-server (service discovery) | 8761 | ✅ 1/1 tests (real boot + registry endpoints) |
| api-gateway (routing · JWT edge validation · CORS · rate limit · correlation ids) | 8080 | ✅ 4/4 filter tests; live Eureka run pending |
| auth-service (register/login/JWT/refresh-rotation/roles/Google OAuth2/Flyway) | 8081 | ✅ 14/14 tests (H2-PG-mode real stack) |
| auction-service (listings · state machine · scheduling · closing · outbox) | 8082 | ✅ 9/9 tests |
| bidding-service (ladder-CAS + FOR UPDATE · idempotency · anti-sniping · WebSocket/STOMP) | 8083 | ✅ 6/6 deterministic tests · PG concurrency IT gated on local PostgreSQL (`scripts\run-pg-tests.bat`) |
| payment-service (WinnerDeclared outbox consumer · payment states · mock provider) | 8084 | ✅ 5/5 tests |
| frontend (React + TypeScript + Vite + Tailwind + STOMP) | 5173 | ✅ tsc + vite build clean (live E2E pending services running) |
| Redis cache / Kafka events | 6379 / 9092 | optional local infra, documented in `docs/infrastructure.md` |

## Architecture

```text
React/Vite :5173 → API Gateway :8080 → Auth :8081 → bidvelocity_auth
                                    ↘ Auction :8082 → bidvelocity_auction
                                    ↘ Bidding :8083 → bidvelocity_bidding (+ Redis, :8761 Eureka)
                                    ↘ Payment :8084 → bidvelocity_payment  (consumes WinnerDeclared via Kafka)
```

Database-per-service is strict: no cross-service table access, no cross-database FKs.
Winner settlement is event-driven (`WinnerDeclared` → payment), never a synchronous
Bidding→Auction→Payment chain. PostgreSQL is the durable source of truth; Redis only
accelerates the hot bidding path.

## Prerequisites (native Windows — no Docker)

- Temurin **JDK 21** and **Maven 3.9** (scripts pin `JAVA_HOME`)
- **PostgreSQL** running on `localhost:5432`
- Optional: Redis (`localhost:6379`), Kafka (`localhost:9092`) — services degrade gracefully without them

## First-time setup

```bat
:: 1. copy the env template and fill in YOUR values (never commit .env, never share secrets)
copy .env.example .env

:: 2. create the four service-owned databases (prompts for the postgres password locally)
scripts\setup-databases.bat

:: 3. start, in separate terminals, in this order:
scripts\start-eureka.bat
scripts\start-gateway.bat
scripts\start-auth.bat
```

Google sign-in (optional): Google Cloud Console → OAuth client (Web app) with
*Authorized JavaScript origin* `http://localhost:5173` and
*Authorized redirect URI* `http://localhost:8080/login/oauth2/code/google`.
Put the client id/secret **only** in `.env` for the auth service — the secret must never
reach React or the repository.

## Build & test

```bat
scripts\build-test.bat        :: all modules
mvn -B -ntp -pl services/auth-service test   :: single module
```

The suite reproduces the guarantees proven in the BidVelocity design demo: JWT issue/verify/
tamper rejection, BCrypt-only persistence, state-machine 409s, duplicate-email 409, idempotent
bid replay, one-winner concurrency, anti-sniping caps, deterministic winner resolution,
event-driven payment creation and the 100-concurrent-bidder invariants (bidding-service phase).

## Conventions

- Errors: every service answers `{timestamp,status,error,message,path,correlationId}`
- No plaintext passwords, no committed secrets, no tokens in logs
- Typography: Inter / system UI stack only
- Flyway owns schemas (`ddl-auto: validate`)
