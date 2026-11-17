--liquibase formatted sql
--changeset diary:006-data-exports dbms:postgresql
CREATE TABLE data_export (
    id uuid PRIMARY KEY,
    version bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    user_id uuid NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    source_data_revision bigint NOT NULL CHECK (source_data_revision >= 0),
    expires_at timestamptz NOT NULL,
    archive bytea,
    error_code varchar(100),
    CHECK (expires_at > created_at)
);
CREATE INDEX ix_export_user ON data_export(user_id, created_at DESC);
CREATE INDEX ix_export_expiry ON data_export(expires_at) WHERE archive IS NOT NULL;
ALTER TABLE report ALTER COLUMN snapshot_json DROP NOT NULL;
--rollback ALTER TABLE report ALTER COLUMN snapshot_json SET NOT NULL;
--rollback DROP TABLE data_export;

--changeset diary:006-scrub-artifacts dbms:postgresql splitStatements:false
CREATE FUNCTION scrub_diary_artifacts() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.data_revision <> OLD.data_revision THEN
        UPDATE mutation_receipt SET response_payload = NULL, gone = true
        WHERE user_id = NEW.id AND resource_id IN (
            SELECT id FROM report WHERE user_id = NEW.id AND source_data_revision <> NEW.data_revision
            UNION ALL SELECT id FROM data_export WHERE user_id = NEW.id AND source_data_revision <> NEW.data_revision
        );
        UPDATE report SET snapshot_json = NULL, error_code = 'SOURCE_DATA_CHANGED',
            status = CASE WHEN status IN ('QUEUED','RUNNING') THEN 'CANCELLED' ELSE status END,
            version = version + 1, updated_at = CURRENT_TIMESTAMP
        WHERE user_id = NEW.id AND source_data_revision <> NEW.data_revision AND snapshot_json IS NOT NULL;
        UPDATE data_export SET archive = NULL, error_code = 'SOURCE_DATA_CHANGED',
            version = version + 1, updated_at = CURRENT_TIMESTAMP
        WHERE user_id = NEW.id AND source_data_revision <> NEW.data_revision AND archive IS NOT NULL;
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER tr_scrub_diary_artifacts AFTER UPDATE OF data_revision ON app_user
FOR EACH ROW EXECUTE FUNCTION scrub_diary_artifacts();
--rollback DROP TRIGGER tr_scrub_diary_artifacts ON app_user; DROP FUNCTION scrub_diary_artifacts();
