-- V1: users — owned exclusively by the Auth Service (bidvelocity_auth)
CREATE TABLE IF NOT EXISTS users (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    email         VARCHAR(255) NOT NULL,
    first_name    VARCHAR(100) NOT NULL,
    last_name     VARCHAR(100) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,           -- BCrypt; never plaintext
    provider      VARCHAR(20)  NOT NULL DEFAULT 'LOCAL',   -- LOCAL | GOOGLE
    provider_id   VARCHAR(255),                    -- Google sub claim for OAuth users
    status        VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',  -- ACTIVE | SUSPENDED
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_users_email UNIQUE (email),
    CONSTRAINT ck_users_status CHECK (status IN ('ACTIVE','SUSPENDED')),
    CONSTRAINT ck_users_provider CHECK (provider IN ('LOCAL','GOOGLE'))
);

CREATE INDEX IF NOT EXISTS idx_users_email   ON users (email);
CREATE INDEX IF NOT EXISTS idx_users_status  ON users (status);
CREATE UNIQUE INDEX IF NOT EXISTS uq_users_provider_identity ON users (provider, provider_id) WHERE provider_id IS NOT NULL;
