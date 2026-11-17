--liquibase formatted sql
--changeset diary:008-speech dbms:postgresql
ALTER TABLE user_settings ADD COLUMN speech_enabled boolean NOT NULL DEFAULT false;
ALTER TABLE user_settings ADD COLUMN speech_consent_version varchar(100);
ALTER TABLE user_settings ADD COLUMN speech_consent_accepted_at timestamptz;
ALTER TABLE user_settings ADD CONSTRAINT ck_speech_consent CHECK (NOT speech_enabled OR
    (speech_consent_version IS NOT NULL AND speech_consent_accepted_at IS NOT NULL));
CREATE TABLE transcription (
    id uuid PRIMARY KEY,
    version bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    user_id uuid NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    status varchar(20) NOT NULL CHECK (status IN ('QUEUED','RUNNING','SUCCEEDED','FAILED','CANCELLED','EXPIRED')),
    audio bytea,
    audio_hash varchar(64) NOT NULL,
    duration_millis bigint NOT NULL CHECK (duration_millis BETWEEN 100 AND 120000),
    transcript text CHECK (length(transcript) BETWEEN 1 AND 5000),
    model_id varchar(255),
    attempt integer NOT NULL DEFAULT 0 CHECK (attempt BETWEEN 0 AND 2),
    expires_at timestamptz NOT NULL,
    finished_at timestamptz,
    lease_token uuid,
    lease_until timestamptz,
    error_code varchar(100),
    CHECK ((status='RUNNING' AND lease_token IS NOT NULL AND lease_until IS NOT NULL)
        OR (status<>'RUNNING' AND lease_token IS NULL AND lease_until IS NULL)),
    CHECK ((status IN ('QUEUED','RUNNING') AND audio IS NOT NULL AND octet_length(audio)<=4194304)
        OR (status NOT IN ('QUEUED','RUNNING') AND audio IS NULL)),
    CHECK ((status='SUCCEEDED') = (transcript IS NOT NULL))
);
CREATE UNIQUE INDEX uq_transcription_active ON transcription(user_id) WHERE status IN ('QUEUED','RUNNING');
CREATE INDEX ix_transcription_queue ON transcription(created_at,id) WHERE status IN ('QUEUED','RUNNING');
CREATE INDEX ix_transcription_lease ON transcription(lease_until) WHERE status='RUNNING';
CREATE INDEX ix_transcription_expiry ON transcription(expires_at,id);
CREATE INDEX ix_transcription_rate ON transcription(user_id,created_at);
--rollback DROP TABLE transcription;
--rollback ALTER TABLE user_settings DROP CONSTRAINT ck_speech_consent;
--rollback ALTER TABLE user_settings DROP COLUMN speech_consent_accepted_at;
--rollback ALTER TABLE user_settings DROP COLUMN speech_consent_version;
--rollback ALTER TABLE user_settings DROP COLUMN speech_enabled;

--changeset diary:008-speech-revocation dbms:postgresql splitStatements:false
CREATE FUNCTION revoke_speech_consent() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF NOT NEW.ai_enabled OR NOT NEW.speech_enabled
        OR NEW.speech_consent_version IS DISTINCT FROM OLD.speech_consent_version THEN
        UPDATE transcription SET status='CANCELLED', audio=NULL, transcript=NULL, lease_token=NULL, lease_until=NULL,
            error_code='SPEECH_CONSENT_REVOKED', finished_at=CURRENT_TIMESTAMP,
            version=version+1, updated_at=CURRENT_TIMESTAMP
        WHERE user_id=NEW.user_id AND status IN ('QUEUED','RUNNING','SUCCEEDED');
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER tr_revoke_speech_consent AFTER UPDATE OF ai_enabled,speech_enabled,speech_consent_version ON user_settings
FOR EACH ROW EXECUTE FUNCTION revoke_speech_consent();
--rollback DROP TRIGGER tr_revoke_speech_consent ON user_settings; DROP FUNCTION revoke_speech_consent();

--changeset diary:008-speech-scrub-exports dbms:postgresql splitStatements:false
CREATE FUNCTION scrub_speech_exports() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF OLD.transcript IS NOT NULL AND (TG_OP='DELETE' OR NEW.transcript IS NULL) THEN
        UPDATE mutation_receipt SET response_payload=NULL, gone=true
        WHERE user_id=OLD.user_id AND resource_id IN (SELECT id FROM data_export WHERE user_id=OLD.user_id);
        UPDATE data_export SET archive=NULL, error_code='SPEECH_DATA_REMOVED',
            version=version+1, updated_at=CURRENT_TIMESTAMP
        WHERE user_id=OLD.user_id AND archive IS NOT NULL;
    END IF;
    RETURN NULL;
END;
$$;
CREATE TRIGGER tr_scrub_speech_exports AFTER UPDATE OF transcript OR DELETE ON transcription
FOR EACH ROW EXECUTE FUNCTION scrub_speech_exports();
--rollback DROP TRIGGER tr_scrub_speech_exports ON transcription; DROP FUNCTION scrub_speech_exports();
