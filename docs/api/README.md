# API Reference

Every service exposes live OpenAPI docs via springdoc:
`http://localhost:<port>/swagger-ui.html` (spec JSON at `/v3/api-docs`) on
auth :8081, auction :8082, bidding :8083, payment :8084.

All calls in production go through the gateway (`http://localhost:8080`); the gateway
validates JWTs at the edge, strips forged identity headers, rate-limits and assigns a
correlation id. Errors from every service share one envelope:

```json
{ "timestamp": "2026-09-20T01:30:00Z", "status": 400, "error": "INVALID_BID",
  "message": "Bid must be greater than or equal to 1200", "path": "/api/bids",
  "correlationId": "a1b2c3d4" }
```

## Auth Service
| Method | Path | Auth | Purpose |
|---|---|---|---|
| POST | `/api/auth/register` | public | Create account (USER or SELLER; ADMIN never self-assignable). Returns access+refresh JWT |
| POST | `/api/auth/login` | public | Email/password → tokens. 401 `INVALID_CREDENTIALS`, 403 `ACCOUNT_SUSPENDED` |
| POST | `/api/auth/refresh` | public(body) | Rotate refresh token; reuse revokes the whole family |
| POST | `/api/auth/logout` | public(body) | Revoke refresh family |
| GET | `/oauth2/authorization/google` | public | Start Google sign-in (Spring Security) → callback `/login/oauth2/code/google` → JWT fragment hand-off to the frontend |
| GET | `/api/users/me` | JWT | Current profile |
| GET | `/api/admin/users` | ADMIN | List users |
| PATCH | `/api/admin/users/{id}/status` | ADMIN | ACTIVE / SUSPENDED (cuts sessions) |

## Auction Service
| Method | Path | Auth | Purpose |
|---|---|---|---|
| GET | `/api/auctions` | public | Search: `q, category, status(OPEN/LIVE/...), min, max, sort(ending|newest|price), page, size` |
| GET | `/api/auctions/{id}` | public | Detail incl. `minNextBid`, anti-snipe counters, `version` |
| POST | `/api/auctions` | SELLER | Create (validated; starts SCHEDULED) |
| GET | `/api/auctions/mine` | SELLER | Seller's lots |
| POST | `/api/auctions/{id}/cancel` | SELLER/ADMIN | Cancel (state machine enforced) |
| POST | `/api/auctions/{id}/close` | ADMIN | Force close → deterministic resolution |
| PUT | `/api/auctions/{id}/bid-state` | SELLER/ADMIN | Bidding-service projection sync (price/count/extension) |
| GET | `/api/auctions/outbox/{topic}` | internal | Transactional-outbox poll (`after` cursor) |
| POST | `/api/auctions/outbox/ack` | internal | Mark events delivered |

## Bidding Service
| Method | Path | Auth | Purpose |
|---|---|---|---|
| POST | `/api/bids` | USER/SELLER | `{auctionId, amount, idempotencyKey}` → `{bid, extended, newEndTime, minNext, duplicate}`. Rejections: 400 `INVALID_BID`, 403 seller/admin, 409 `AUCTION_NOT_LIVE`/`AUCTION_CLOSED`/`BID_CONFLICT`, 429 `BID_THROTTLED` |
| GET | `/api/bids/auction/{id}` | public | Recent accepted bids (anonymized names) |
| GET | `/api/bids/my` | JWT | My bid history |
| GET | `/api/bids/internal/{id}/winner` | internal | Seal ledger + deterministic winner (`RESOLVED/NO_BIDS/OPEN_DEFER`) |
| POST | `/api/bids/internal/{id}/state` | internal | Auction lifecycle → runtime projection sync |
| WS | `/ws` (STOMP) | — | Subscribe `/topic/auction/{id}` (BID_ACCEPTED[_EXTENDED], AUCTION_SEALED), `/topic/user/{id}` (USER_OUTBID) |

## Payment Service
| Method | Path | Auth | Purpose |
|---|---|---|---|
| GET | `/api/payments/my` | JWT | Winner invoices |
| GET | `/api/payments/seller` | SELLER | Received-settlement view |
| GET | `/api/payments/{id}` | winner/ADMIN | Detail |
| POST | `/api/payments/{id}/process` | winner | PENDING/FAILED → PROCESSING → SUCCESS/FAILED (idempotent; retry allowed) |
| POST | `/api/payments/{id}/refund` | ADMIN | SUCCESS → REFUNDED |
| POST | `/api/payments/internal/from-winner` | ADMIN | Manual invoice (event fallback) |
