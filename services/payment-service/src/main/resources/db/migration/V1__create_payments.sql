-- V1: payments — owned by the Payment Service (bidvelocity_payment)
CREATE TABLE IF NOT EXISTS payments (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    auction_id      BIGINT        NOT NULL,           -- logical FK to auction DB
    auction_title   VARCHAR(200)  NOT NULL DEFAULT '',
    seller_id       BIGINT        NOT NULL,
    winner_id       BIGINT        NOT NULL,
    amount          NUMERIC(14,2) NOT NULL CHECK (amount > 0),
    currency        VARCHAR(3)    NOT NULL DEFAULT 'INR',
    status          VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
    provider        VARCHAR(30)   NOT NULL DEFAULT 'MOCK',
    provider_ref    VARCHAR(120)  NULL,
    failure_reason  VARCHAR(60)   NULL,
    attempts        INT           NOT NULL DEFAULT 0,
    idempotency_key VARCHAR(120)  NOT NULL,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uq_payment_idempotency UNIQUE (idempotency_key),  -- one invoice per auction, at-least-once safe
    CONSTRAINT ck_payment_status CHECK (status IN ('PENDING','PROCESSING','SUCCESS','FAILED','EXPIRED','REFUNDED'))
);
CREATE INDEX IF NOT EXISTS idx_payments_winner ON payments (winner_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_payments_auction ON payments (auction_id);
CREATE INDEX IF NOT EXISTS idx_payments_status ON payments (status);

CREATE TABLE IF NOT EXISTS payment_transactions (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    payment_id  BIGINT      NOT NULL REFERENCES payments(id) ON DELETE CASCADE,
    kind        VARCHAR(20) NOT NULL,   -- ATTEMPT | SUCCESS | FAILURE | REFUND
    detail      VARCHAR(200) NOT NULL DEFAULT '',
    at          TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_paytx_payment ON payment_transactions (payment_id, at);
