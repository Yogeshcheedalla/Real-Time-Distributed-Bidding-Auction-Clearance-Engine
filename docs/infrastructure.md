# Local infrastructure (no Docker)

Everything runs natively on Windows. Only PostgreSQL is required for Phases 1–8;
Redis and Kafka are **optional accelerators** and are documented here separately.

## PostgreSQL — required (already installed on this machine)

- Service: `postgresql-x64-18` on `localhost:5432` (verified running)
- Run `scripts\setup-databases.bat` once → creates the four service-owned databases:
  `bidvelocity_auth`, `bidvelocity_auction`, `bidvelocity_bidding`, `bidvelocity_payment`
- Each service's JDBC URL points at **only its own database**; there are no cross-database
  foreign keys and services never open another service's schema.

## Redis — optional (hot bidding path)

Purpose per spec: current-highest-bid cache, auction countdown state, bid throttling,
idempotency keys. PostgreSQL remains the durable source of truth — flushing Redis loses nothing.

To add later: install a Windows-native Redis build (e.g. Memurai or tporadowski/redis zip),
start it on `localhost:6379`, set `REDIS_HOST/REDIS_PORT` in `.env`. Until then the
bidding service uses its in-process throttle + PostgreSQL constraints (correct, just not
horizontally-shared). This is a documented, visible fallback — never a silent fake.

## Kafka — optional (event transport)

`WinnerDeclared` / `BidAccepted` / `PaymentCompleted` events flow through a Spring
`EventPublisher` abstraction:

- `KAFKA_ENABLED=false` (default): in-process publish/subscribe — single-node correct.
- `KAFKA_ENABLED=true` + local Kafka on `localhost:9092`: same events on real topics
  (`bv.auctions`, `bv.bids`, `bv.payments`).

To add later: download Apache Kafka, `bin\windows\zookeeper-server-start.bat` +
`kafka-server-start.bat config/kafka.properties`, set the env vars in `.env`.

## Port map

| Port | Owner |
|---|---|
| 5173 | React/Vite dev server |
| 8080 | API Gateway (note: if Docker Desktop holds :8080 via WSL relay, close it or set `PORT=` override) |
| 8081–8084 | Auth / Auction / Bidding / Payment |
| 8761 | Eureka |
| 6379 / 9092 | Redis / Kafka (optional) |
