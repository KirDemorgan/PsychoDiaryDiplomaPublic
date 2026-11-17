--liquibase formatted sql
--changeset diary:002-diary dbms:postgresql
CREATE TABLE state_rating (
    id uuid PRIMARY KEY,
    version bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    user_id uuid NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    local_date date NOT NULL,
    slot varchar(20) NOT NULL CHECK (slot IN ('MORNING','DAY','EVENING')),
    time_zone varchar(100) NOT NULL,
    anxiety integer CHECK (anxiety BETWEEN 0 AND 10),
    mood integer CHECK (mood BETWEEN 0 AND 10),
    energy integer CHECK (energy BETWEEN 0 AND 10),
    CONSTRAINT uq_rating_slot UNIQUE(user_id, local_date, slot),
    CONSTRAINT ck_rating_not_empty CHECK (anxiety IS NOT NULL OR mood IS NOT NULL OR energy IS NOT NULL)
);
CREATE TABLE diary_entry (
    id uuid PRIMARY KEY,
    version bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    user_id uuid NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    revision integer NOT NULL DEFAULT 1 CHECK (revision > 0),
    occurred_at timestamptz NOT NULL,
    time_zone varchar(100) NOT NULL,
    report_visible boolean NOT NULL DEFAULT true,
    review_complete boolean NOT NULL DEFAULT false,
    text text NOT NULL CHECK (char_length(text) BETWEEN 1 AND 5000)
);
CREATE INDEX ix_entry_user_event ON diary_entry(user_id, occurred_at DESC, id DESC);
CREATE TABLE entry_revision (
    id uuid PRIMARY KEY,
    version bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    entry_id uuid NOT NULL REFERENCES diary_entry(id) ON DELETE CASCADE,
    revision integer NOT NULL CHECK (revision > 0),
    text text NOT NULL CHECK (char_length(text) BETWEEN 1 AND 5000),
    occurred_at timestamptz NOT NULL,
    time_zone varchar(100) NOT NULL,
    CONSTRAINT uq_entry_revision UNIQUE(entry_id, revision)
);
ALTER TABLE diary_entry ADD CONSTRAINT fk_current_revision
    FOREIGN KEY (id, revision) REFERENCES entry_revision(entry_id, revision)
    DEFERRABLE INITIALLY DEFERRED;
CREATE TABLE mutation_receipt (
    id uuid PRIMARY KEY,
    version bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    user_id uuid NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    route varchar(255) NOT NULL,
    operation_id uuid NOT NULL,
    request_hash varchar(64) NOT NULL,
    resource_id uuid NOT NULL,
    resource_version bigint NOT NULL,
    response_status integer NOT NULL,
    response_payload text,
    gone boolean NOT NULL DEFAULT false,
    CONSTRAINT uq_mutation UNIQUE(user_id, route, operation_id),
    CONSTRAINT ck_gone_payload CHECK (NOT gone OR response_payload IS NULL)
);
CREATE INDEX ix_receipt_resource ON mutation_receipt(user_id, resource_id);
--rollback DROP TABLE mutation_receipt;
--rollback ALTER TABLE diary_entry DROP CONSTRAINT fk_current_revision;
--rollback DROP TABLE entry_revision;
--rollback DROP TABLE diary_entry;
--rollback DROP TABLE state_rating;
