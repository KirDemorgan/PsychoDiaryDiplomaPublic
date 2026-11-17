--liquibase formatted sql
--changeset diary:001-users dbms:postgresql
CREATE TABLE app_user (
    id uuid PRIMARY KEY,
    version bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    email varchar(254) NOT NULL UNIQUE,
    password_hash varchar(255) NOT NULL,
    display_name varchar(100),
    time_zone varchar(100) NOT NULL,
    data_revision bigint NOT NULL DEFAULT 0 CHECK (data_revision >= 0),
    role varchar(20) NOT NULL DEFAULT 'USER' CHECK (role = 'USER'),
    CONSTRAINT ck_email_normalized CHECK (email = lower(btrim(email))),
    CONSTRAINT ck_display_name CHECK (display_name IS NULL OR char_length(btrim(display_name)) > 0)
);
CREATE TABLE user_settings (
    id uuid PRIMARY KEY,
    version bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    user_id uuid NOT NULL UNIQUE REFERENCES app_user(id) ON DELETE CASCADE,
    ai_enabled boolean NOT NULL DEFAULT false,
    ai_consent_version varchar(255),
    ai_consent_accepted_at timestamptz,
    reminders_enabled boolean NOT NULL DEFAULT true,
    reminder_repeat_minutes integer CHECK (reminder_repeat_minutes BETWEEN 1 AND 1440),
    neutral_notification_text boolean NOT NULL DEFAULT true,
    theme varchar(255) NOT NULL DEFAULT 'SYSTEM' CHECK (theme IN ('SYSTEM','LIGHT','DARK')),
    CONSTRAINT ck_ai_consent CHECK (NOT ai_enabled OR (ai_consent_version IS NOT NULL AND ai_consent_accepted_at IS NOT NULL))
);
CREATE TABLE auth_session (
    id uuid PRIMARY KEY,
    version bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    user_id uuid NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    expires_at timestamptz NOT NULL,
    revoked_at timestamptz
);
CREATE INDEX ix_session_user ON auth_session(user_id);
CREATE INDEX ix_session_expiry ON auth_session(expires_at);
CREATE TABLE refresh_token (
    id uuid PRIMARY KEY,
    version bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    session_id uuid NOT NULL REFERENCES auth_session(id) ON DELETE CASCADE,
    token_hash varchar(64) NOT NULL UNIQUE,
    expires_at timestamptz NOT NULL,
    consumed_at timestamptz
);
CREATE INDEX ix_refresh_session ON refresh_token(session_id);
CREATE INDEX ix_refresh_expiry ON refresh_token(expires_at);
--rollback DROP TABLE refresh_token;
--rollback DROP TABLE auth_session;
--rollback DROP TABLE user_settings;
--rollback DROP TABLE app_user;
