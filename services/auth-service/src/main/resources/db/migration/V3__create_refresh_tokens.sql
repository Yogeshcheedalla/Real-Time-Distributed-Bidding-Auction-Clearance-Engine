-- V3: refresh tokens (hashed at rest, rotation + reuse detection)
CREATE TABLE IF NOT EXISTS refresh_tokens (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id     BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash  VARCHAR(128) NOT NULL,              -- SHA-256 of the opaque token
    family_id   UUID         NOT NULL,              -- rotation family for reuse detection
    expires_at  TIMESTAMPTZ  NOT NULL,
    revoked     BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_refresh_user   ON refresh_tokens (user_id);
CREATE INDEX IF NOT EXISTS idx_refresh_family ON refresh_tokens (family_id);
CREATE UNIQUE INDEX IF NOT EXISTS uq_refresh_hash ON refresh_tokens (token_hash);
