--liquibase formatted sql
--changeset diary:005-reports dbms:postgresql
CREATE TABLE report (
    id uuid PRIMARY KEY,
    version bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    user_id uuid NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    from_date date NOT NULL,
    to_date date NOT NULL CHECK (to_date >= from_date),
    time_zone varchar(100) NOT NULL,
    source_data_revision bigint NOT NULL CHECK (source_data_revision >= 0),
    input_hash varchar(64) NOT NULL,
    snapshot_json text NOT NULL,
    status varchar(20) NOT NULL CHECK (status IN ('QUEUED','RUNNING','READY','FAILED','CANCELLED')),
    error_code varchar(100),
    CHECK (to_date - from_date < 366)
);
CREATE INDEX ix_report_user ON report(user_id, created_at DESC, id DESC);
--rollback DROP TABLE report;
