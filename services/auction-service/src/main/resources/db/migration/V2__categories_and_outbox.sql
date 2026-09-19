-- V2: categories + transactional outbox (event backbone until Kafka is provisioned)
CREATE TABLE IF NOT EXISTS auction_categories (
    id   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name VARCHAR(60) NOT NULL,
    CONSTRAINT uq_category_name UNIQUE (name)
);

INSERT INTO auction_categories (name) VALUES
 ('Electronics'),('Collectibles'),('Art'),('Vehicles'),('Fashion'),('Home'),('Gadgets'),('Luxury')
ON CONFLICT (name) DO NOTHING;

-- Outbox: events written in the SAME transaction as the state change, consumed by
-- other services (payment) or a Kafka relay publisher. Guarantees at-least-once.
CREATE TABLE IF NOT EXISTS outbox_events (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    aggregate_id BIGINT       NOT NULL,
    topic        VARCHAR(60)  NOT NULL,
    payload      JSONB        NOT NULL,
    published    BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_outbox_unpublished ON outbox_events (published, id) WHERE published = FALSE;
CREATE INDEX IF NOT EXISTS idx_outbox_topic       ON outbox_events (topic, aggregate_id);
