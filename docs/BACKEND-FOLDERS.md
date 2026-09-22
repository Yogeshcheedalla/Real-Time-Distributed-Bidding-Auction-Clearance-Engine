# Backend Folders Explained — Complete Description
(Plain English. Matches the real files on disk. For interview revision.)

Location of this file: `docs/BACKEND-FOLDERS.md`
Backend root: `C:\Real-Time Distributed Bidding & Auction Clearance Engine\services\`

Every service folder has the same shape (standard Spring Boot layout):

```
<service-name>/
├── pom.xml                  → the service's dependency list + build recipe (Maven)
└── src/
    ├── main/java/           → the actual code
    ├── main/resources/      → application.yml (config) + db/migration (SQL tables)
    └── test/java/           → automated tests
```

The parent `pom.xml` (one level above `services/`) is the "boss pom" — it lists all six
modules and pins shared versions (Spring Boot 3.5.5, Spring Cloud 2025.0.1, Java 21).

---

## 1. eureka-server/  — "the phone book" (port 8761)

The smallest service. It stores who is alive.

| File | What it does |
|---|---|
| `EurekaServerApplication.java` | One class. The `@EnableEurekaServer` annotation turns this app into the registry. Every other service registers itself here on startup. |
| `application.yml` | Port 8761, and "register with myself = no" (it is the registry, not a client). |
| `EurekaServerIntegrationTest.java` | Boots the real server and checks health + the `/eureka/apps` endpoint answer. |

Interview line: "Eureka solves location: the gateway calls `lb://AUTH-SERVICE`, Eureka answers with live instances, so I can run 3 copies or kill one with zero config changes."

---

## 2. api-gateway/ — "the front door" (port 8080)

The only service the browser may touch. All routing + edge security.

| File | What it does |
|---|---|
| `GatewayApplication.java` | Starts the reactive gateway, registers with Eureka. |
| `filters/JwtValidationFilter.java` | Checks the JWT on every non-public call. Strips client-forged `X-User-*` headers, injects verified ones. Public list: auth endpoints, Google OAuth paths, and **GET** on marketplace/bid-feed routes (anonymous browsing), everything else needs a token. |
| `filters/CorrelationIdFilter.java` | Puts one id on every request (`X-Correlation-Id`), echoes it in the response, so one request can be traced across all service logs. |
| `filters/RateLimitFilter.java` | Fixed-window counter per IP+route: 60/min for bids, 10/min for login (brute-force guard), 300 default. Returns 429 in the same JSON error shape. |
| `application.yml` | The route table: which path goes to which service (`/api/auctions/**` → AUCTION-SERVICE, `/oauth2/**` → AUTH, `/ws/**` → BIDDING over websocket…). Also CORS origins and the OAuth route's `SetRequestHeader` fix (forces the registered `localhost:8080` so Google never sees the tunnel host). |
| `JwtValidationFilterTest.java` | 6 tests: valid token injects identity, expired/forged rejected, public GET passes, POST blocked, headers stripped. |

Interview line: "One choke point where security, rate limiting and tracing are enforced once instead of in five services."

---

## 3. auth-service/ — "identity" (port 8081, owns bidvelocity_auth)

| Folder/File | What it does |
|---|---|
| `AuthApplication.java` | Startup class. |
| `domain/User.java` | The `users` table as a Java object: email, name, **password_hash only**, provider (LOCAL/GOOGLE), status (ACTIVE/SUSPENDED), roles. |
| `domain/Role.java` | USER / SELLER / ADMIN rows. |
| `domain/RefreshToken.java` | Stores the **SHA-256 hash** of each refresh token + `family_id` + expiry + revoked flag. The raw token is never stored. |
| `repo/…Repository.java` (3) | Spring Data interfaces: `findByEmailIgnoreCase`, `findByTokenHash`, `revokeFamily`… |
| `security/JwtService.java` | Mints and verifies the HS256 access token (claims: userId, email, roles, iat, exp). Refuses to start if the secret is under 32 bytes. |
| `security/JwtAuthFilter.java` | Reads the bearer token on every request and puts the user into Spring's SecurityContext. |
| `security/SecurityConfig.java` | Which URLs are public, which need which role; CSRF off (stateless JWT API). |
| `config/CryptoConfig.java` | The BCrypt password encoder bean (cost 10). Separate class on purpose — it broke a Spring bean cycle. |
| `config/DemoUserSeeder.java` | Only when `DEMO_SEED=true`: creates the demo accounts with real BCrypt hashes. |
| `oauth/GoogleLoginSuccessHandler.java` | After Google says OK: find-or-create user by Google subject, give USER role, mint our JWT, redirect the browser to the frontend with the token in the URL **fragment** (fragments never hit server logs). |
| `service/AuthService.java` | The brain: register (duplicate check, hashing, role rules — ADMIN can never be self-assigned), login (equal-time failure for unknown users), refresh (**rotation** + **family revocation on reuse**), logout, admin user list/status. |
| `service/RefreshReuseGuard.java` | Tiny class whose only job is `REQUIRES_NEW` — so the family-revoke survives the rollback caused by the 401 we throw right after. |
| `web/AuthController.java` | REST endpoints: `/api/auth/register|login|refresh|logout`, `/api/users/me`, `/api/admin/users…`. |
| `web/ApiException.java` + `GlobalExceptionHandler.java` | Every error leaves as the same JSON: `{timestamp,status,error,message,path,correlationId}`. |
| `dto/Dtos.java` | Request/response shapes with validation annotations (`@Email`, `@Size(min=8)`…). Records, no Lombok. |
| `resources/application.yml` | DB URL `bidvelocity_auth`, Flyway on, Google client id/secret **from env only**, `forward-headers-strategy: framework`. |
| `resources/db/migration/V1–V3.sql` | The tables: users → roles+user_roles → refresh_tokens. Flyway applies them in order, once. |
| `src/test/…` (3 files) | 16 tests: JWT round-trip/tamper/expiry, BCrypt-only storage, duplicate email 409, rotation+reuse, suspension cuts sessions, Google upsert, seeder login works. |

---

## 4. auction-service/ — "the listing lifecycle" (port 8082, owns bidvelocity_auction)

| Folder/File | What it does |
|---|---|
| `domain/Auction.java` | The auction: prices, times, status, anti-snipe config, winner fields, and a `@Version` column — **optimistic locking**. |
| `domain/OutboxEvent.java` | A row that means "an event must be delivered". |
| `repo/AuctionRepository.java` | Finds due auctions + Specification search (keyword/category/status/price/sort/page). |
| `repo/OutboxRepository.java` | Poll unpublished events, mark published. |
| `client/BiddingClient.java` | Feign HTTP client: at close time, asks the **bidding** service to seal the ledger and return the deterministic winner (`RESOLVED / NO_BIDS / OPEN_DEFER`). This is database-per-service in action — no JOIN into the bids table. |
| `service/AuctionService.java` | Create with validation, search, cancel, close, and `resolve()` — the state machine: `SCHEDULED→LIVE→ENDING→ENDED→SOLD/UNSOLD`, illegal moves throw 409. SOLD + `WINNER_DECLARED` outbox row are written **in the same transaction**. |
| `service/AuctionScheduler.java` | Runs every second: start what's due, mark ending (last 60 s), close what's expired. Safe to re-run (idempotent); if bidding is unreachable it just tries next tick. |
| `config/DemoAuctionSeeder.java` | Demo catalogue with real Wikimedia image URLs — and prices that honestly start at starting price (no fake history). |
| `web/AuctionController.java` | Public GETs, seller POSTs, admin close. |
| `web/AuctionInternalController.java` | PUT `/bid-state` — bidding pushes price/count/extension updates back to the projection. |
| `web/OutboxController.java` | GET poll + POST ack (transactional) for the payment consumer. |
| `security/…` (3 files) | Same JWT pattern as auth (verify-only; public GET browsing). |
| `resources/db/migration/V1–V3.sql` | auctions (+ indexes, CHECK constraints) → categories + outbox → image_url. |
| tests (2) | 10 tests: state machine, SOLD/UNSOLD/reserve rules, outbox content, OPEN_DEFER clock heal, seeder honesty. |

---

## 5. bidding-service/ — "the hot path" (port 8083, owns bidvelocity_bidding)

The most important service for the interview.

| Folder/File | What it does |
|---|---|
| `domain/Bid.java` | One bid row. `idempotency_key` is UNIQUE — double-clicks cannot create two bids. |
| `domain/BidRuntime.java` | The per-auction "current state" row (price, top bid, clock, extension count). **This row is the serialization point.** |
| `repo/BidRuntimeRepository.java` | The heart: `lockForUpdate` (SELECT … FOR UPDATE), `advisoryLock` (pg_advisory_xact_lock — orders all bids across all instances), and `casAcceptBid` — one UPDATE whose WHERE re-checks status + clock + ladder + old price + top-bid id **at lock time**. 1 row = accepted; 0 = lost the race → 409 with new minimum. |
| `repo/BidRepository.java` | Winner query: `amount DESC, accepted_at ASC, id ASC` — the deterministic tie-break ladder. |
| `service/BidService.java` | The flow: advisory lock → idempotency replay check → row lock → validate (seller? admin? live? clock? amount ≥ minimum?) → insert bid → CAS the top → after commit: STOMP push + best-effort sync back to auction. |
| `service/RuntimeHydrationGuard.java` | Makes sure the runtime row exists **before** the bid transaction (FOR UPDATE on zero rows locks nothing!). One creator per auction per JVM + PK-conflict catch for other instances. This guard was born from a real race the PostgreSQL tests caught. |
| `client/AuctionClient.java` | Feign: fetch auction state (to hydrate) + push price/extension updates. |
| `config/RealtimeConfig.java` | STOMP over WebSocket: endpoint `/ws`, topics `/topic/auction/{id}` and `/topic/user/{id}`; also the small thread pool for async projection sync. |
| `web/BidController.java` | POST `/api/bids` (USER/SELLER only), GET feeds, `/api/bids/my`. |
| `web/BidThrottle.java` | 12 bids per 10 s per user+auction (in-process; Redis when present). |
| `web/BidInternalController.java` | The seal-and-resolve endpoint auction calls; state-sync endpoint. |
| `resources/db/migration/V1.sql` | bids + bid_runtime with the winner-order index. |
| `BidConcurrencyTest.java` | H2 logic tests: ladder, replay, anti-snipe cap, sealed rejects. |
| `BidPostgresConcurrencyIT.java` | **The proof**: 24 equal bids on real PostgreSQL → exactly one accepted; 100-bidder ladder strictly increasing. Runs only when PG env vars exist (skips cleanly otherwise). |

---

## 6. payment-service/ — "settlement" (port 8084, owns bidvelocity_payment)

| Folder/File | What it does |
|---|---|
| `domain/Payment.java` | One invoice. `idempotency_key = auction:<id>` is UNIQUE — the same event delivered twice creates one invoice. Status enum: PENDING/PROCESSING/SUCCESS/FAILED/EXPIRED/REFUNDED. |
| `domain/PaymentTransaction.java` | Append-only audit trail (attempt, success, failure, refund). |
| `provider/PaymentProvider.java` | The interface — charge/capture/refund. |
| `provider/MockPaymentProvider.java` | Dev implementation with a repeating outcome list (≈82% success) so retry paths are exercised. Razorpay/Stripe would implement the same interface — the service never changes. |
| `service/PaymentService.java` | createInvoice (idempotent), process (state machine + provider call), refund, expiry sweep, ownership checks. Contains the **WinnerDeclaredConsumer** — polls the auction outbox every 5 s; failures just retry later. |
| `client/AuctionOutboxClient.java` | Feign: poll `/outbox/WINNER_DECLARED` + ack. |
| `security/…` | Verify-only JWT (this service never issues tokens). |
| `web/PaymentController.java` | my/seller lists, get, process, refund (admin), internal create. |
| `resources/db/migration/V1.sql` | payments + payment_transactions. |
| `PaymentFlowTest.java` | 5 tests: event→invoice idempotent, success+refund, failure→retry→success, ownership guards, double-create stays one. |

---

## What is NOT in these folders (and why that's good)

- **No cross-service database access.** `client/` folders are how services talk: HTTP (Feign), not SQL.
- **No secrets in code.** Every password/key/secret comes from environment/`.env` (git-ignored).
- **No shared code module.** Each service repeats its small `JwtService`/error handler on purpose — sharing a jar would couple deployment; duplication is cheaper than coupling at this size.
- `.metadata/` inside `services/` is just the Eclipse/STS workspace data — not part of the build (it's git-ignored).

## The one-minute story connecting all six
Browser → **gateway** (check token, rate-limit, tag with request-id) → **auth** (who you are) or **auction** (what's for sale) or **bidding** (the race: advisory lock → row lock → CAS → broadcast over WebSocket) → when the clock ends, auction asks bidding to **seal and name the winner**, writes SOLD + outbox event in one transaction → **payment** polls the event and opens an invoice → winner pays → done. **Eureka** quietly keeps everyone findable.
