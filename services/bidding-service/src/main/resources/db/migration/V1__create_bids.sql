-- V1: durable bid ledger + per-auction runtime (serialization point). Bidding DB is the
-- source of truth for accepted bids; PostgreSQL, not Redis, decides winners.
CREATE TABLE IF NOT EXISTS bid_runtime (
    auction_id          BIGINT PRIMARY KEY,          -- logical FK to auction DB
    seller_id           BIGINT        NOT NULL,
    status              VARCHAR(20)   NOT NULL,      -- SCHEDULED|LIVE|ENDING|SEALED
    starting_price      NUMERIC(14,2) NOT NULL,
    current_price       NUMERIC(14,2) NOT NULL,
    min_increment       NUMERIC(14,2) NOT NULL,
    start_time          TIMESTAMPTZ   NOT NULL,
    end_time            TIMESTAMPTZ   NOT NULL,      -- anti-snipe extensions mutate THIS
    anti_snipe          BOOLEAN       NOT NULL DEFAULT TRUE,
    extension_window    INT           NOT NULL DEFAULT 30,
    max_extensions      INT           NOT NULL DEFAULT 3,
    extension_count     INT           NOT NULL DEFAULT 0,
    highest_bid_id      BIGINT        NULL,
    bid_count           INT           NOT NULL DEFAULT 0,
    synced_version      BIGINT        NOT NULL DEFAULT 0,
    updated_at          TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS bids (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    auction_id       BIGINT        NOT NULL,
    bidder_id        BIGINT        NOT NULL,
    bidder_name      VARCHAR(200)  NOT NULL,
    amount           NUMERIC(14,2) NOT NULL CHECK (amount > 0),
    status           VARCHAR(20)   NOT NULL DEFAULT 'ACCEPTED',
    reject_reason    VARCHAR(60)   NULL,
    idempotency_key  VARCHAR(120)  NULL,
    accepted_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT ck_bid_status CHECK (status IN ('ACCEPTED','REJECTED')),
    CONSTRAINT ck_bid_reason CHECK (reject_reason IS NULL OR reject_reason IN
      ('AUCTION_NOT_LIVE','AUCTION_CLOSED','SELLER_BID_FORBIDDEN','ADMIN_BID_FORBIDDEN','BELOW_MINIMUM'))
);
ALTER TABLE bids DROP CONSTRAINT IF EXISTS uq_bid_idempotency;
ALTER TABLE bids ADD CONSTRAINT uq_bid_idempotency UNIQUE (idempotency_key);

CREATE INDEX IF NOT EXISTS idx_bids_auction_amount ON bids (auction_id, amount DESC, accepted_at ASC, id ASC);
CREATE INDEX IF NOT EXISTS idx_bids_bidder        ON bids (bidder_id, accepted_at DESC);
CREATE INDEX IF NOT EXISTS idx_bids_auction_time  ON bids (auction_id, accepted_at DESC);
