--liquibase formatted sql
-- auth_db: схема auth-сервиса CoGoal

--changeset bash:001-create-users
CREATE TABLE users (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    username       VARCHAR(32)  NOT NULL,
    email          VARCHAR(255) NOT NULL,
    password_hash  VARCHAR(100) NOT NULL,
    name           VARCHAR(100) NOT NULL,
    role           VARCHAR(10)  NOT NULL DEFAULT 'USER',
    created_at     TIMESTAMP    NOT NULL DEFAULT (now() AT TIME ZONE 'UTC'),

    CONSTRAINT ck_users_username_format CHECK (username ~ '^[a-zA-Z0-9_]{3,32}$'),
    CONSTRAINT ck_users_role            CHECK (role IN ('USER', 'ADMIN'))
);

CREATE UNIQUE INDEX ux_users_username_lower ON users (lower(username));
CREATE UNIQUE INDEX ux_users_email_lower    ON users (lower(email));

--rollback DROP TABLE users;

--changeset bash:002-create-auth-tokens
CREATE TABLE auth_tokens (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id     BIGINT       NOT NULL,
    token_hash  VARCHAR(64)  NOT NULL,
    type        VARCHAR(20)  NOT NULL,
    new_email   VARCHAR(255),
    expires_at  TIMESTAMP    NOT NULL,
    used_at     TIMESTAMP,
    revoked_at  TIMESTAMP,
    created_at  TIMESTAMP    NOT NULL DEFAULT (now() AT TIME ZONE 'UTC'),

    CONSTRAINT fk_auth_tokens_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT ux_auth_tokens_hash UNIQUE (token_hash),
    CONSTRAINT ck_auth_tokens_type CHECK (type IN ('REFRESH', 'PASSWORD_RESET', 'EMAIL_CONFIRM')),
    CONSTRAINT ck_auth_tokens_new_email CHECK ((type = 'EMAIL_CONFIRM') = (new_email IS NOT NULL))
);

CREATE INDEX ix_auth_tokens_user_type  ON auth_tokens (user_id, type);
CREATE INDEX ix_auth_tokens_expires_at ON auth_tokens (expires_at);

--rollback DROP TABLE auth_tokens;
