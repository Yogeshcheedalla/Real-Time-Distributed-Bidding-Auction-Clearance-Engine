# BidVelocity — System Architecture

## Component topology (as built)

```mermaid
graph TD
    UI[React + Vite :5173]
    GW[API Gateway :8080<br/>JWT edge validation · CORS · rate limit · correlation-id]
    EU[Eureka :8761]

    AUTH[Auth Service :8081<br/>register · login · JWT · refresh rotation · Google OAuth2]
    AUC[Auction Service :8082<br/>listings · state machine · scheduler · transactional outbox]
    BID[Bidding Service :8083<br/>FOR UPDATE + ladder CAS · idempotency · anti-snipe · STOMP]
    PAY[Payment Service :8084<br/>outbox consumer · payment states · MockPaymentProvider]

    ADB[(bidvelocity_auth)]
    BDB[(bidvelocity_auction)]
    CDB[(bidvelocity_bidding)]
    PDB[(bidvelocity_payment)]

    RDS[(Redis<br/>optional)]
    KFK[(Kafka<br/>optional)]

    UI --> GW
    GW --> AUTH & AUC & BID & PAY
    UI -. ws /topic/auction/:id .-> BID
    AUTH --> ADB
    AUC --> BDB
    BID --> CDB
    PAY --> PDB
    AUC -- "Feign: winner resolution (seal)" --> BID
    BID -- "Feign: state sync / hydration" --> AUC
    PAY -- "Feign: outbox poll WINNER_DECLARED" --> AUC
    AUTH & AUC & BID & PAY & GW --> EU
    BID -.-> RDS
    AUC -.-> KFK
```

Dashed edges are optional or fallback paths: Redis accelerates throttling when
present (in-process fallback today); Kafka replaces outbox polling when provisioned
(same events, same handler). PostgreSQL is always the durable source of truth.

## Auction lifecycle

```mermaid
stateDiagram-v2
    [*] --> SCHEDULED: seller creates (validated)
    SCHEDULED --> LIVE: scheduler, start_time reached
    LIVE --> ENDING: 60s before end
    ENDING --> LIVE: >60s remain after anti-snipe extension
    LIVE --> ENDED: clock expires
    ENDING --> ENDED: clock expires
    ENDED --> SOLD: winner exists and reserve met
    ENDED --> UNSOLD: no bids or reserve not met
    SCHEDULED --> CANCELLED: seller/admin
    LIVE --> CANCELLED: seller/admin
    ENDING --> CANCELLED: seller/admin
```

Invalid transitions return `409 INVALID_STATE_TRANSITION`. Closing is
re-entrant-safe: the auction asks the bidding ledger to **seal** (stop bids,
row-locked) and return the deterministic winner
(`amount DESC → accepted_at ASC → id ASC`); if the ledger reports the auction
is still open (anti-snipe extended), the auction heals its clock and retries
next tick. `WinnerDeclared` is written to the transactional **outbox in the same
commit** as the SOLD state change — payment settlement is asynchronous, never a
synchronous Bidding→Auction→Payment chain.

## Bid acceptance gate (concurrency)

```mermaid
sequenceDiagram
    participant C as Client
    participant G as Gateway
    participant B as Bidding Service
    participant DB as PostgreSQL (bidding_db)
    C->>G: POST /api/bids (JWT, Idempotency-Key)
    G->>B: routed, identity headers injected
    B->>DB: ensure runtime row (REQUIRES_NEW, insert-if-absent)
    B->>DB: BEGIN · SELECT runtime FOR UPDATE
    B->>B: validate seller/admin/state/clock/ladder
    B->>DB: INSERT bid · UPDATE runtime SET top WHERE ladder predicate holds
    alt predicate matched (1 row)
        B-->>C: 201 ACCEPTED (+STOMP broadcast after commit)
    else price moved (0 rows)
        B-->>C: 400/409 with fresh minimum
    end
```

Two layers make this race-free on any number of bidding instances: the row
lock serializes the critical section, and the ladder condition lives **inside
the UPDATE predicate**, so it is evaluated against the row's committed values
at lock-acquisition time — a stale Java-side read can never double-accept.
`Idempotency-Key` has a unique constraint; replays return the original outcome.

## Data ownership (database-per-service)

| Service | Owns | Never touches |
|---|---|---|
| auth | users, roles, user_roles, refresh_tokens | other services' tables |
| auction | auctions, auction_categories, outbox_events | bids, payments |
| bidding | bids, bid_runtime | auctions, payments |
| payment | payments, payment_transactions | bids, auctions |

Cross-service references are logical IDs only; no cross-database foreign keys;
all interaction is REST/Feign or outbox events.
