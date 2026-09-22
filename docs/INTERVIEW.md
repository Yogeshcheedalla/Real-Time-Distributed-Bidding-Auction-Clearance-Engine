# BidVelocity — Interview Prep (Backend, Connections, JWT, Concurrency)

Everything here reflects the **actual code in this repo** — say what you built, and be able to defend every claim.

---

## 0. The 30-second answer to "tell me about this project"

> "BidVelocity is a real-time auction platform built as six Spring Boot microservices —
> Eureka for discovery, a Spring Cloud Gateway at the edge, and Auth, Auction, Bidding and
> Payment services, each owning its own PostgreSQL database (database-per-service, enforced
> by Flyway). The hard part is concurrent bidding: hundreds of users race on the same item in
> the final seconds, so I made bid acceptance an atomic compare-and-swap on a locked row,
> proved it under real PostgreSQL with 24 simultaneous equal bids accepting exactly one winner.
> Settlement is event-driven via a transactional outbox — when an auction closes, a
> `WINNER_DECLARED` event is written in the same DB transaction as the SOLD state, and the
> payment service consumes it asynchronously. Auth is stateless JWT with refresh-token
> rotation and reuse detection, plus Google OAuth2."

---

## 1. The connection map — every hop and WHY it exists

```
Browser (React :5173)
   │  HTTPS + Authorization: Bearer <JWT>
   ▼
API Gateway (:8080)  ── registers with ──► Eureka (:8761)
   │  routes lb://SERVICE (resolved via Eureka)
   ├──► Auth (:8081)      ─► bidvelocity_auth
   ├──► Auction (:8082)   ─► bidvelocity_auction
   ├──► Bidding (:8083)   ─► bidvelocity_bidding   ◄── STOMP /ws /topic/auction/{id}
   └──► Payment (:8084)   ─► bidvelocity_payment

Service-to-service (NEVER through the gateway, host-network only):
   Auction  ──Feign GET /internal/{id}/winner──►  Bidding   (seal + deterministic winner)
   Bidding  ──Feign PUT /bid-state, POST /state──►  Auction  (projection sync + hydration)
   Payment  ──Feign GET /outbox/WINNER_DECLARED──►  Auction  (event poll + ack)
```

**Why each connection exists (this is what they'll probe):**

| Connection | Why | What if it were done differently |
|---|---|---|
| Browser → Gateway | Single public entry point; JWT validated once at the edge; CORS, rate-limit, correlation-id in one place | Without it every service re-implements auth/CORS and there's no choke point |
| Gateway → Eureka | Resolve `lb://AUTH-SERVICE` to a live instance; enables multiple instances + load balancing | Hardcoded URLs break scaling and failover |
| Services → Eureka | Self-register so the gateway discovers them | Manual config; doesn't scale |
| Browser → Bidding (STOMP) | Push bid updates in real time without polling | Polling adds latency + load; the room would "feel" fake |
| Auction → Bidding (Feign) | The **winner lives in the bidding ledger**, not the auction DB. Auction owns lifecycle, Bidding owns bids — so at close, auction asks bidding to *seal and return* the deterministic winner | A shared DB would let auction just read bids — but that violates database-per-service and couples the two |
| Bidding → Auction (Feign) | Bidding keeps a `bid_runtime` projection (price, ladder, anti-snipe config) and hydrates it from auction on first bid; also pushes price/extension back | Bidding re-querying auction on every bid would be slower and put auction on the hot path |
| Payment → Auction (outbox poll) | Settlement must be **asynchronous** — a payment failure must never roll back an accepted bid or a SOLD auction | A synchronous `Auction→Payment` call would make payment latency/failures block the bid path and could double-charge on retry |

**The key architectural claim to defend:** *"Don't make Bidding → Auction → Payment a long synchronous chain."* The bid path is fast and transactional; only settlement is event-driven.

---

## 2. JWT — verified against the actual code

**Yes, JWT is the token mechanism, and here is exactly how it works in this repo.**

### Access token (`JwtService.issueAccess`)
- Algorithm **HS256** (HMAC-SHA256, symmetric — same `JWT_SECRET` signs and verifies).
- Claims: `sub` = userId, `email`, `roles[]`, `iat`, `exp` (default 120 min).
- Format: `header.payload.signature`, base64url.

### Verification (defense in depth — TWO places)
1. **Gateway** (`JwtValidationFilter`): rejects missing/expired/tampered tokens; **strips any client-supplied `X-User-*` headers** (anti-forgery) and injects verified `X-User-Id/Email/Roles`.
2. **Each service** (`JwtAuthFilter`): re-verifies the bearer token itself, so a direct port-probe can't impersonate a caller (zero-trust between services).

### Tamper proof
`Jwts.parser().verifyWith(key).build().parseSignedClaims(token)` throws on any signature mismatch or expiry → we return `null` → 401. Tests prove a token signed with a *different* secret is rejected.

### Refresh tokens (NOT JWT — deliberately)
- Opaque 384-bit random string, returned once; only its **SHA-256 hash** is stored (`refresh_tokens.token_hash`).
- **Rotation**: every refresh invalidates the old token and issues a new one in the same **family** (`family_id`).
- **Reuse detection**: if an already-rotated token is replayed, the **whole family is revoked** (stolen-token defence). The revocation runs in a `REQUIRES_NEW` transaction (`RefreshReuseGuard`) so it survives the rollback caused by the subsequent 401 throw.

### Passwords
- **BCrypt** cost 10 (`CryptoConfig`), never plaintext, never logged. Login burns equal BCrypt work on unknown users to avoid a user-enumeration timing oracle.

### The classic follow-up: "Why HS256 vs RS256?"
> "HS256 is symmetric — one shared secret. It's fine here because the services are a small trusted mesh sharing `JWT_SECRET`. In a larger system or with third-party verifiers I'd use RS256 (asymmetric): auth keeps the private key, other services only get the public key, so they can verify but never mint tokens."

---

## 3. Concurrency — the strongest part of your story

**Bid acceptance gate** (`BidRuntimeRepository.casAcceptBid`) — three layers:
1. `pg_advisory_xact_lock(auctionId)` — serializes all bids for one auction across every instance until commit.
2. `SELECT … FOR UPDATE` on the `bid_runtime` row — the serialization point.
3. A **compare-and-swap `UPDATE … WHERE`** that re-checks status, clock, ladder minimum, current price and top-bid parity *inside the SQL predicate* — evaluated against the row's committed values at lock time. 1 row = accepted, 0 = someone moved first → 409.

**Idempotency:** `bids.idempotency_key` is UNIQUE; a replayed request returns the original bid, never a second row.

**Anti-sniping:** a bid inside the configurable window extends `end_time`, hard-capped by `max_extensions`.

**Deterministic winner:** `ORDER BY amount DESC, accepted_at ASC, id ASC` — same ledger always yields the same winner.

**The H2 vs PostgreSQL story (great to tell):** H2 does *not* re-evaluate an UPDATE's WHERE clause after a row-lock wait the way PostgreSQL does, so the H2 suite couldn't prove the race. I ran the concurrency tests against **real PostgreSQL** (throwaway schemas, production Flyway SQL) and it caught a genuine lost-update race in the first-bid burst — fixed with the advisory lock + serialized hydration guard.

---

## 4. Interview Q&A

### Architecture & microservices
**Q: Why database-per-service?**
A: Independent deployability and coupling. Services own their schema; cross-service references are logical IDs only, no cross-DB foreign keys. It's the difference between microservices and a distributed monolith.

**Q: How do services talk to each other?**
A: Synchronous OpenFeign for reads that must be consistent at that moment (winner resolution), and asynchronous events (transactional outbox → poll) for state changes like settlement. Never a long sync chain on the hot path.

**Q: Why Eureka over Kubernetes service discovery?**
A: This is a native-Windows, no-Docker build, so Eureka gives client-side discovery and `lb://` load balancing without a platform dependency. On K8s you'd use its built-in DNS + Service objects instead.

**Q: Single point of failure?**
A: The gateway is the choke point — mitigate with multiple gateway instances behind a load balancer; Eureka peers; each service runs 1..N instances. The DB is the real SPOF → managed Postgres with replicas/failover.

### JWT & security
**Q: Where is the JWT verified?**
A: At the gateway and again in every service (zero-trust). The gateway also strips forged identity headers.

**Q: How do you log a user out with a stateless token?**
A: Access tokens are short-lived; logout revokes the refresh family. You can't un-mint a JWT, so you rely on short expiry + refresh revocation.

**Q: Access token in localStorage — XSS risk?**
A: Real trade-off. Mitigations: short TTL, CSP, and for a hardened version put refresh in an HttpOnly SameSite cookie and keep only a short access token in memory.

**Q: How is the Google secret protected?**
A: It lives only in the auth-service environment (`.env`, git-ignored), never in the frontend or repo. The React app only hits `/oauth2/authorization/google`.

**Q: `redirect_uri_mismatch` — what caused it and how did you fix it?**
A: Behind the gateway + Cloudflare tunnel, the incoming `X-Forwarded-Host` was the tunnel domain, so Spring built a redirect URI Google never registered. I switched the gateway filter from `AddRequestHeader` to `SetRequestHeader` to force the registered `localhost:8080` origin on every path.

### Concurrency
**Q: Two users bid ₹1100 at the same instant — how do you guarantee one wins?**
A: The row lock + advisory lock serialize the transactions, and the CAS predicate re-checks the price at commit. Exactly one UPDATE matches; the other sees 0 rows and gets a 409 with the new minimum.

**Q: Optimistic vs pessimistic locking — where did you use each?**
A: Pessimistic (`FOR UPDATE`/advisory) on the hot bid row where contention is expected and retry is cheap. Optimistic (`@Version`) on the auction entity where conflicts are rare.

**Q: What is the lost-update problem you hit?**
A: Concurrent first-bids each tried to create the runtime projection; on H2 the CAS didn't re-evaluate after lock wait. Fixed by serializing hydration per auction and proving it on PostgreSQL.

### Transactions & events
**Q: Explain the transactional outbox.**
A: When an auction is marked SOLD, a `WINNER_DECLARED` row is inserted in the *same DB transaction*. A consumer (payment, or a Kafka relay) polls/streams it. This guarantees the event and the state change commit together — no "sold but no event" or "event but not sold".

**Q: At-least-once delivery + how do you avoid double invoices?**
A: The payment row has a UNIQUE `idempotency_key = auction:<id>`, so redelivering `WINNER_DECLARED` is a no-op. At-least-once + idempotent consumer = effectively exactly-once.

**Q: Why not just call payment synchronously?**
A: Payment latency or an outage would block/roll back auction closing. Events decouple them — the auction is SOLD regardless of whether payment is up.

**Q: Why polling instead of Kafka?**
A: No Docker constraint — Kafka is optional. The outbox is the abstraction; flipping `KAFKA_ENABLED=true` swaps the poller for a Kafka listener with identical payloads.

### Resilience
**Q: What happens if bidding is down when auction tries to close?**
A: The winner query fails, the auction stays `ENDED`, and the scheduler retries next tick — it never resolves on a partial view and never twice. If the ledger reports the auction is still open (anti-snipe extension), it returns `OPEN_DEFER` and the auction heals its clock.

**Q: Idempotency-Key — why?**
A: A client that retries a bid after a network timeout must not create two bids. The unique key makes the retry return the original result.

### Data & migrations
**Q: Why Flyway and `ddl-auto=validate`?**
A: Schema is code-reviewed SQL migrations under version control; Hibernate only validates against it, never mutates production schema implicitly.

**Q: How do you handle a breaking schema change?**
A: Expand-migrate-contract: add nullable column, backfill, deploy code that reads both, then drop the old column in a later migration.

### Testing
**Q: What did you actually test?**
A: 44 unit/integration tests across 6 modules (JWT tamper/expiry, BCrypt-only storage, state-machine 409s, idempotent replay, anti-snipe caps, deterministic resolution, outbox→invoice idempotency, payment retries) plus 2 real-PostgreSQL concurrency ITs (24-way exactly-one, 100-way ladder).

**Q: Why did you not trust the H2 concurrency result?**
A: Because H2's row-lock semantics differ from PostgreSQL's — it silently allowed a lost update the production DB would prevent. I moved the proof to real PostgreSQL.

---

## 5. Things to admit if pressed (honesty scores points)
- Redis/Kafka are documented drop-ins, not wired live (in-process throttle + outbox polling are the working fallbacks).
- HS256 shared secret is fine for this mesh; RS256 would be the production hardening step.
- The backend runs on one machine; a real deploy puts each service on its own host with managed Postgres (Neon/RDS) and a message broker.
- Access token in localStorage is a known XSS trade-off; HttpOnly cookie refresh is the upgrade.
