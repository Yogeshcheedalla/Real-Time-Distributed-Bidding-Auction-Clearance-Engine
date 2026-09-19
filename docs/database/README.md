# Database Design — one schema per service, four PostgreSQL databases

No service ever opens another service's database. Cross-service references are
**logical IDs only** (no cross-database foreign keys). Every schema is owned by
Flyway migrations (`spring.jpa.hibernate.ddl-auto=validate`).

```
PostgreSQL (localhost:5432, or sandbox :5433)
├── bidvelocity_auth      → Auth Service    : users, roles, user_roles, refresh_tokens
├── bidvelocity_auction   → Auction Service : auctions, auction_categories, outbox_events
├── bidvelocity_bidding   → Bidding Service : bids, bid_runtime
└── bidvelocity_payment   → Payment Service : payments, payment_transactions
```

## bidvelocity_auth
- `users` — email unique; `password_hash` BCrypt only; `provider LOCAL|GOOGLE` with
  partial unique index on `(provider, provider_id)`; `status ACTIVE|SUSPENDED`
- `roles` — CHECK-constrained to USER/SELLER/ADMIN (seeded in V2)
- `user_roles` — composite PK
- `refresh_tokens` — stores **SHA-256 of the token only**, `family_id` for rotation
  reuse-detection, indexed lookups

## bidvelocity_auction
- `auctions` — full lifecycle `status` CHECK; price CHECKs (`starting_price>0`,
  `end_time>start_time`); anti-snipe config columns; `version` for optimistic locking;
  indexes on `status`, `start_time`, `end_time`, `seller_id`, `(category,current_price)`
- `outbox_events` — transactional outbox (aggregate, topic, payload, published) with a
  partial index on unpublished rows
- `auction_categories` — seeded catalogue

## bidvelocity_bidding
- `bids` — the **durable ledger**; `amount > 0`; unique `idempotency_key`;
  winner-order index `(auction_id, amount DESC, accepted_at ASC, id ASC)`
- `bid_runtime` — per-auction serialization row (PK = auction_id). The acceptance gate
  is a single UPDATE whose WHERE clause re-checks status, clock, ladder and CAS parity;
  writers serialize on `pg_advisory_xact_lock(auction_id)` + `SELECT … FOR UPDATE`

## bidvelocity_payment
- `payments` — **unique `idempotency_key` (`auction:<id>`)** makes event redelivery safe;
  status CHECK `PENDING|PROCESSING|SUCCESS|FAILED|EXPIRED|REFUNDED`; provider + refs only,
  never card data
- `payment_transactions` — append-only audit per attempt/success/failure/refund

## Event flow (why there is no cross-service JOIN)

```
BID ACCEPTED        → bidding DB (truth)  → STOMP push + best-effort projection sync
AUCTION SOLD        → auction DB + WINNER_DECLARED in the SAME transaction (outbox)
INVOICE             → payment DB, created by polling the outbox (at-least-once + unique key
                      = exactly-once effect). Kafka replaces the poller with identical payloads.
```

PostgreSQL is the source of truth for accepted bids, auction state and payment records in
every configuration; Redis (optional) only accelerates hot reads and throttling.
