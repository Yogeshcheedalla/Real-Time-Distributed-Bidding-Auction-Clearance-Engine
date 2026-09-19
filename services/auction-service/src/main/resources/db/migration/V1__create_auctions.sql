-- V1: auctions — owned exclusively by the Auction Service (bidvelocity_auction)
CREATE TABLE IF NOT EXISTS auctions (
    id                        BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    seller_id               BIGINT        NOT NULL,            -- logical FK to auth DB (no cross-db FK by design)
    title                   VARCHAR(140)  NOT NULL,
    description             VARCHAR(4000) NOT NULL DEFAULT '',
    category                VARCHAR(60)   NOT NULL,
    emoji                   VARCHAR(8)    NOT NULL DEFAULT '📦',
    starting_price          NUMERIC(14,2) NOT NULL CHECK (starting_price > 0),
    current_price           NUMERIC(14,2) NOT NULL,
    min_increment           NUMERIC(14,2) NOT NULL CHECK (min_increment > 0),
    reserve_price           NUMERIC(14,2) NULL CHECK (reserve_price IS NULL OR reserve_price > 0),
    start_time              TIMESTAMPTZ   NOT NULL,
    end_time                TIMESTAMPTZ   NOT NULL,
    status                  VARCHAR(20)   NOT NULL DEFAULT 'DRAFT',
    anti_sniping_enabled    BOOLEAN       NOT NULL DEFAULT TRUE,
    extension_window_secs   INT           NOT NULL DEFAULT 30 CHECK (extension_window_secs BETWEEN 5 AND 120),
    max_extensions          INT           NOT NULL DEFAULT 3  CHECK (max_extensions BETWEEN 1 AND 10),
    extension_count         INT           NOT NULL DEFAULT 0,
    bid_count               INT           NOT NULL DEFAULT 0,
    highest_bid_id          BIGINT        NULL,                -- logical FK to bidding DB
    winner_id               BIGINT        NULL,
    winning_amount          NUMERIC(14,2) NULL,
    close_reason            VARCHAR(60)   NULL,
    version                 BIGINT        NOT NULL DEFAULT 0,   -- optimistic lock
    created_at              TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT ck_auctions_status CHECK (status IN
      ('DRAFT','SCHEDULED','LIVE','ENDING','ENDED','CANCELLED','SOLD','UNSOLD')),
    CONSTRAINT ck_auctions_times CHECK (end_time > start_time)
);

CREATE INDEX IF NOT EXISTS idx_auctions_status    ON auctions (status);
CREATE INDEX IF NOT EXISTS idx_auctions_start     ON auctions (start_time);
CREATE INDEX IF NOT EXISTS idx_auctions_end       ON auctions (end_time);
CREATE INDEX IF NOT EXISTS idx_auctions_seller    ON auctions (seller_id);
CREATE INDEX IF NOT EXISTS idx_auctions_cat_price ON auctions (category, current_price);
