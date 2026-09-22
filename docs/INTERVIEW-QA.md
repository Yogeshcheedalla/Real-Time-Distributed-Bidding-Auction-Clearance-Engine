# BidVelocity Interview Bank — 15 Questions with Model Answers
(Basic → Medium → Senior follow-ups. Answers match this repo's actual code.)

## BASICS

### Q1. What is microservices architecture, and how is it different from a monolith? Why use it here?
**Answer:** A monolith is one deployable unit sharing one process and usually one database; microservices split the system into small, independently deployable services that communicate over the network and own their data. I chose it for BidVelocity because the pieces scale and fail differently: bidding is write-heavy with intense row contention and needs horizontal scaling, auth is read-heavy and stable, payment is bursty and must tolerate its provider being slow. In a monolith, a slow payment call sits on the same thread pool as bid placement — one bad dependency takes everything down.
**Bonus line:** "The cost is real too — network partitions, eventual consistency, deployment complexity — I took those costs deliberately, not by default."

### Q2. What does "database-per-service" mean, and why not one shared database?
**Answer:** Each service owns a PostgreSQL database (`bidvelocity_auth/auction/bidding/payment`) and no service can open another's. A shared DB is a hidden coupling: schema changes break everyone, and code slowly starts JOINing across 'service' boundaries, making it a distributed monolith. Cross-service references here are **logical IDs only** — the auction row stores `winner_id` but never joins to the users table; it asks the bidding service's API for the winner instead.
**Bonus line:** "Flyway per service means each schema versions with its own service."

### Q3. What does the API Gateway do? Why not let clients call services directly?
**Answer:** One public entry point (:8080). It verifies the JWT once, strips any client-supplied `X-User-*` headers and injects verified ones, applies CORS and per-route rate limits (tighter on `/api/bids` and login), attaches a correlation id, and routes by logical name via Eureka. Without it: every service re-implements auth, there's no single place to throttle abuse, and clients need to know the topology.
**Bonus line:** "It's the choke point where security and observability are enforced once instead of seven times."

### Q4. What is service discovery? What does Eureka actually solve?
**Answer:** Services register themselves with Eureka on boot (hostname + port + status); the gateway resolves `lb://AUTH-SERVICE` against that registry at request time and round-robins across instances. It solves static configuration: start a second bidding instance and the gateway load-balances to it with zero config changes; an instance dies and gets evicted.
**Bonus line:** "On Kubernetes I'd use its native DNS/Service instead — Eureka is the right tool for this no-Docker deployment, not a universal one."

### Q5. Explain the structure of a JWT. How do you verify one?
**Answer:** Three base64url parts — `header.payload.signature`. Header declares the algorithm (HS256), payload carries claims (`sub`=userId, `email`, `roles`, `iat`, `exp`), signature is HMAC(header+payload, secret). Verification = recompute the signature with the secret and compare, then check `exp`. Any tampered byte fails the signature. In my system it's verified twice — gateway and each service — because a direct network call must not bypass the edge.
**Bonus line:** "It's signed, not encrypted — anyone can read the payload, nobody can alter it. Never put secrets in claims."

### Q6. Authentication vs authorization — how are they enforced in your system?
**Answer:** Authentication = who you are: the auth service checks credentials (BCrypt compare) and mints a JWT. Authorization = what you may do: roles (USER/SELLER/ADMIN) travel as claims and are enforced with `@PreAuthorize` on methods — `POST /api/auctions` needs SELLER, `POST /api/payments/{id}/refund` needs ADMIN. Plus object-level checks: a winner can only process their own invoice; a seller can only cancel their own auction.
**Bonus line:** "Role checks alone aren't enough — the ownership check is the one people forget."

## MEDIUM

### Q7. Why BCrypt instead of SHA-256 for passwords?
**Answer:** SHA-256 is fast — that's exactly wrong for passwords; GPUs crack billions per second. BCrypt is deliberately slow and adaptive (cost factor 10 here ≈ 100ms), and salts automatically so identical passwords produce different hashes. My schema stores `password_hash` only; plaintext never touches DB, logs, or responses, and the user DTO physically can't serialize it.
**Bonus line:** "Argon2 is the modern default; BCrypt is battle-tested and acceptable at cost ≥ 10."

### Q8. Why do you need refresh tokens at all? Explain rotation and reuse detection.
**Answer:** Short-lived access tokens (2h) limit theft damage, but re-login every 2 hours is terrible UX — so a long-lived refresh token (14d) mints new access tokens. Mine is an opaque random string, stored only as a SHA-256 hash. **Rotation:** every use invalidates the old token and issues a new one in the same family. **Reuse detection:** if an already-rotated token comes back, it's presumed stolen — the whole family is revoked, forcing re-login. The revocation runs in a `REQUIRES_NEW` transaction so the 401 I throw right after can't roll it back.
**Bonus line:** "That REQUIRES_NEW detail was a bug I actually hit in testing — the revoke was being rolled back with the failed request."

### Q9. Two users bid ₹1,100 at the same instant. How is exactly one accepted?
**Answer:** Three layers in one transaction: (1) `pg_advisory_xact_lock(auctionId)` serializes all bid attempts for that auction across every instance; (2) `SELECT … FOR UPDATE` locks the runtime row; (3) the final UPDATE is a compare-and-swap — its WHERE clause re-checks the price, top-bid id, status, clock and ladder minimum against the row's *committed* values at lock-acquisition time. Exactly one UPDATE matches one row; the others match zero and return 409 with the new minimum.
**Bonus line:** "I proved it with 24 concurrent equal bids against real PostgreSQL — H2 gave false confidence because it doesn't re-evaluate UPDATE predicates after lock waits."

### Q10. What is an Idempotency-Key and why does bidding need it?
**Answer:** A client-generated unique token per logical request (UUID). The bids table has a UNIQUE constraint on it. If the client times out and retries — or double-clicks — the second request finds the existing row and returns the original result instead of creating a duplicate bid. The DB constraint is the final arbiter: two concurrent requests with the same key can't both insert; one gets a constraint violation mapped to 409.
**Bonus line:** "Payment processing uses the same idea — the invoice key `auction:<id>` makes event redelivery harmless."

### Q11. What is the transactional outbox pattern? Why not just publish to Kafka directly?
**Answer:** The problem: committing a DB change and publishing an event are two systems — you can commit and crash before publishing, or publish and roll back. The outbox makes them one atomic act: `WINNER_DECLARED` is inserted as a row in the *same transaction* as the SOLD update. A relay (here, the payment service's poller; a Kafka publisher when enabled) drains the table. Publishing "directly" can never be transactional with the DB write without this (or CDC/Debezium).
**Bonus line:** "Delivery is at-least-once, so consumers must be idempotent — with the unique invoice key, that combination gives exactly-once effect."

### Q12. When do you use synchronous (Feign) vs asynchronous (events) communication?
**Answer:** Synchronous when the caller genuinely needs the answer to proceed and the dependency is fast and healthy: auction asking bidding to seal-and-resolve at close. Asynchronous when the caller shouldn't care about the callee's latency or survival: settlement after SOLD. The anti-pattern is a synchronous *chain* on the hot path — Bidding→Auction→Payment per bid — where the slowest link defines availability.
**Bonus line:** "Rule of thumb: reads you're waiting on = sync; state changes you've already durably recorded = event."

## SENIOR FOLLOW-UPS

### Q13. "What breaks if the payment service is down when an auction sells?"
**Answer:** Nothing upstream. The SOLD state and outbox event already committed; the poller resumes when payment returns and drains the backlog — at-least-once. Duplicate deliveries are no-ops via the unique key. The user-visible effect is a late invoice, not a lost sale. That's the point of decoupling settlement from the bid path.
**Bonus line:** "I also made the outbox ack transactional — an early version marked events published even when the ack silently failed."

### Q14. "Why HS256? What's the risk, and what would change in production?"
**Answer:** HS256 is symmetric — every verifier holds the signing secret, so any service compromise can mint tokens. Acceptable for a small trusted mesh sharing one env var. Production: RS256/JWKS — auth keeps the private key, others fetch the public key; plus refresh tokens in HttpOnly SameSite cookies instead of localStorage (XSS trade-off), and token revocation lists if lifetimes must be long.
**Bonus line:** "Naming the exact upgrade path shows you picked HS256 knowingly, not ignorantly."

### Q15. "What was your hardest bug in this project?"
**Answer (the concurrency one — tell it as a story):** "First-bid burst. 24 concurrent bids on a fresh auction: the runtime projection row didn't exist yet, so every thread tried to create it through a nested transaction while holding an outer one. PostgreSQL exposed a lost-update race that H2 had hidden — H2 doesn't re-evaluate UPDATE predicates after waiting for a row lock. I fixed it with a serialized hydration guard (one creator per auction per JVM), an advisory transaction lock, and a true compare-and-swap; then I moved the concurrency proof to real PostgreSQL running the production Flyway migrations."
**Bonus line:** Interviewers can smell rehearsed success stories; a precise, engine-level bug story is the strongest credibility signal you can give.

---
## Rapid-fire one-liners (definitions they sometimes ask cold)
- **Optimistic locking:** version column checked at UPDATE; fail rather than block. Used on `auctions`.
- **Pessimistic locking:** row lock held through the transaction. Used on `bid_runtime`.
- **Correlation id:** id generated at the edge, propagated in headers + logs, ties one request across services.
- **Flyway:** versioned SQL migrations applied in order, checksummed; `ddl-auto=validate` means Hibernate never edits the schema.
- **STOMP over WebSocket:** message-frame protocol for pub/sub topics (`/topic/auction/42`) — the push channel for live bids.
- **Stateless service:** no session on the server; every request carries its own JWT. Enables N instances behind a load balancer.
