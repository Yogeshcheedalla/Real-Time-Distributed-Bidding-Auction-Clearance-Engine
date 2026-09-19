# Testing — what is proven, and how

Two layers, both executed on this machine:

1. **`mvn test` (44 tests, 6 modules)** — real Spring contexts on H2 in PostgreSQL mode
   (plus a real Eureka boot). Deterministic business logic.
2. **`scripts\run-pg-tests.bat` (2 ITs)** — the same code against **real PostgreSQL 18**,
   in throwaway schemas migrated by the production Flyway SQL. This layer exists because
   H2 does not re-evaluate UPDATE predicates after row-lock waits the way PostgreSQL does
   — and that difference caught a genuine race the H2 suite could never show.

## Guarantee → test map

| Guarantee | Test (class) |
|---|---|
| JWT issue/verify, tamper + wrong-key + expiry rejection, ≥32-byte secret enforced | `JwtServiceTest` (auth), `JwtValidationFilterTest` (gateway edge) |
| BCrypt-only password storage; no plaintext anywhere | `registerStoresBcryptHashNeverPlaintext`, `refreshTokenStoredHashedNotRaw` |
| Duplicate email 409; weak password/terms 400; ADMIN never self-assigned | `AuthServiceIntegrationTest` |
| Refresh rotation + **family revocation on reuse** | `refreshRotationAndReuseDetectionRevokesFamily` |
| Suspension cuts login and live sessions | `suspensionBlocksLoginAndCutsSessions` |
| Google sign-in upserts exactly one user per Google subject | `googleLoginUpsertsUserOncePerGoogleSubject` |
| Auction state machine: invalid transition → 409; SOLD/UNSOLD reserve rules | `AuctionLifecycleTest` (creation validations, close paths, cancel-after-close) |
| Winner resolution defers while ledger says open (anti-snipe) + clock self-heal | `openDeferKeepsAuctionRetryableAndHealsClock` |
| WINNER_DECLARED written in the same commit as SOLD; optimistic lock versioning | `closeWithWinnerSoldAndOutbox`, `versionIncrementsOnEveryMutation` |
| Bid ladder: equal second bid rejected; strictly increasing ledger | `equalSecondBidIsRejectedByLadder`, `hundredBidLadderStaysStrictlyIncreasing` |
| Idempotency-Key replay returns original, never a second bid | `idempotencyKeyReplayReturnsOriginalWithoutSecondBid` |
| Anti-snipe extension + hard cap | `antiSnipeExtendsInsideWindowAndStopsAtCap` |
| Seller/admin/closed-auction bid rejections | `closedAndSellerBidsRejected`, `winnerIsHighestThenEarliestThenId_andSealStopsBidding` |
| Deterministic winner (repeatable resolution, tie-break ladder) | `winnerIsHighestThenEarliestThenId_andSealStopsBidding` |
| Outbox → invoice created idempotently (at-least-once = exactly-once effect) | `winnerDeclaredEventCreatesInvoiceIdempotently`, `onlyWinnerPaidTwiceStaysUnique` |
| Payment PENDING→PROCESSING→SUCCESS/FAILED, retry, refund, ownership guards | `happyPathProcessingThenRefund`, `failureThenRetrySucceeds`, `ownershipAndStateGuards` |
| **24 simultaneous equal bids → exactly 1 accepted (real PostgreSQL)** | `BidPostgresConcurrencyIT#twentyFourEqualBidsExactlyOneWinsOnRealPostgres` |
| **100-bidder ladder + deterministic seal (real PostgreSQL)** | `BidPostgresConcurrencyIT#hundredBiddersLadderHoldsOnRealPostgres` |
| Demo seeders honest (no fabricated price movement) + idempotent | `DemoAuctionSeederTest`, `DemoUserSeederTest` |

## Live end-to-end evidence (executed against the running stack)

- Reserve enforcement: ₹32,000 top bid vs ₹42,000 reserve → `UNSOLD / RESERVE_NOT_MET`
- SOLD chain: scheduler close → `WINNER_DECLARED` → payment-service auto-invoice →
  winner `POST /process` → `SUCCESS` + provider ref → outbox ack `published=true`
- Deployed Vercel bundle → tunnel → gateway login: 200 + JWT + correct CORS origin header

## Known limits (stated, not hidden)

- STOMP broadcast and Google's full browser round-trip are verified at the HTTP/302
  level; an interactive browser walkthrough is a manual step.
- Redis/Kafka paths are documented fallbacks (in-process throttle, outbox polling);
  their live variants activate with `REDIS_HOST` / `KAFKA_ENABLED` infrastructure.
