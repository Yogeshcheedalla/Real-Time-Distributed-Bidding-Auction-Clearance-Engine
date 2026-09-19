-- V2: roles + user_roles (USER | SELLER | ADMIN)
CREATE TABLE IF NOT EXISTS roles (
    id   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name VARCHAR(30) NOT NULL,
    CONSTRAINT uq_roles_name UNIQUE (name),
    CONSTRAINT ck_roles_name CHECK (name IN ('USER','SELLER','ADMIN'))
);

CREATE TABLE IF NOT EXISTS user_roles (
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role_id BIGINT NOT NULL REFERENCES roles(id),
    PRIMARY KEY (user_id, role_id)
);

INSERT INTO roles (name) VALUES ('USER'), ('SELLER'), ('ADMIN')
ON CONFLICT (name) DO NOTHING;
