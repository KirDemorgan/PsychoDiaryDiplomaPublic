--liquibase formatted sql
--changeset diary:007-ai-tasks dbms:postgresql
ALTER TABLE diary_entry ADD CONSTRAINT uq_entry_owner UNIQUE(id,user_id);
ALTER TABLE report ADD CONSTRAINT uq_report_owner UNIQUE(id,user_id);
CREATE TABLE ai_task (
    id uuid PRIMARY KEY,
    version bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    user_id uuid NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    kind varchar(20) NOT NULL CHECK (kind IN ('ENTRY','REPORT')),
    entry_id uuid,
    report_id uuid,
    revision integer,
    source_data_revision bigint NOT NULL CHECK (source_data_revision >= 0),
    input_hash varchar(64) NOT NULL,
    status varchar(20) NOT NULL CHECK (status IN ('QUEUED','RUNNING','SUCCEEDED','FAILED','CANCELLED','STALE')),
    attempt integer NOT NULL DEFAULT 0 CHECK (attempt BETWEEN 0 AND 2),
    finished_at timestamptz,
    lease_until timestamptz,
    lease_token uuid,
    model_id varchar(255),
    prompt_version varchar(255) NOT NULL,
    schema_version varchar(255) NOT NULL,
    error_code varchar(100),
    FOREIGN KEY (entry_id,user_id) REFERENCES diary_entry(id,user_id) ON DELETE CASCADE,
    FOREIGN KEY (entry_id,revision) REFERENCES entry_revision(entry_id,revision) ON DELETE CASCADE,
    FOREIGN KEY (report_id,user_id) REFERENCES report(id,user_id) ON DELETE CASCADE,
    UNIQUE (id,entry_id,revision),
    CHECK ((kind='ENTRY' AND entry_id IS NOT NULL AND revision IS NOT NULL AND report_id IS NULL)
        OR (kind='REPORT' AND report_id IS NOT NULL AND entry_id IS NULL AND revision IS NULL)),
    CHECK ((status='RUNNING' AND lease_token IS NOT NULL AND lease_until IS NOT NULL)
        OR (status<>'RUNNING' AND lease_token IS NULL AND lease_until IS NULL))
);
CREATE INDEX ix_ai_task_queue ON ai_task(status,created_at,id);
CREATE INDEX ix_ai_task_lease ON ai_task(lease_until) WHERE status='RUNNING';
CREATE INDEX ix_ai_task_entry ON ai_task(user_id,entry_id,revision,created_at DESC,id DESC);
CREATE UNIQUE INDEX uq_ai_active_entry ON ai_task(entry_id,revision) WHERE kind='ENTRY' AND status IN ('QUEUED','RUNNING');
CREATE UNIQUE INDEX uq_ai_active_report ON ai_task(report_id) WHERE kind='REPORT' AND status IN ('QUEUED','RUNNING');
CREATE TABLE analysis_candidate (
    id uuid PRIMARY KEY,
    version bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    task_id uuid NOT NULL,
    entry_id uuid NOT NULL,
    revision integer NOT NULL,
    label_code varchar(30) NOT NULL CHECK (label_code IN ('SLEEP_DEFICIT','WORK_STUDY','SOCIAL_EVENT','CONFLICT',
        'PHYSICAL_ACTIVITY','CAFFEINE','ANXIETY','CALM','FATIGUE','NAUSEA','HEADACHE','TREMOR')),
    quote text NOT NULL CHECK (length(quote)>0),
    start_index integer NOT NULL CHECK (start_index>=0),
    end_index integer NOT NULL CHECK (end_index>start_index AND end_index<=10000),
    presence varchar(20) NOT NULL CHECK (presence IN ('PRESENT','NEGATED','UNCERTAIN')),
    temporal varchar(20) NOT NULL CHECK (temporal IN ('CURRENT','PAST','FUTURE','UNSPECIFIED')),
    subject varchar(20) NOT NULL CHECK (subject IN ('SELF','OTHER','UNSPECIFIED')),
    FOREIGN KEY (task_id,entry_id,revision) REFERENCES ai_task(id,entry_id,revision) ON DELETE CASCADE,
    UNIQUE(id,entry_id,revision),
    UNIQUE(task_id,label_code,start_index,end_index)
);
CREATE INDEX ix_candidate_task ON analysis_candidate(task_id,start_index,id);
ALTER TABLE entry_tag ADD COLUMN candidate_id uuid;
ALTER TABLE entry_tag ADD CONSTRAINT fk_tag_candidate FOREIGN KEY (candidate_id,entry_id,revision)
    REFERENCES analysis_candidate(id,entry_id,revision) ON DELETE CASCADE;
ALTER TABLE entry_tag ADD CONSTRAINT ck_tag_origin CHECK ((origin='AI') = (candidate_id IS NOT NULL));
CREATE TABLE review_candidate_decision (
    id uuid PRIMARY KEY,
    version bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    review_id uuid NOT NULL,
    candidate_id uuid NOT NULL,
    entry_id uuid NOT NULL,
    revision integer NOT NULL,
    action varchar(20) NOT NULL CHECK (action IN ('ACCEPT','EDIT','REJECT','PENDING')),
    applies_to_local_date date,
    FOREIGN KEY (review_id,entry_id,revision) REFERENCES tag_review(id,entry_id,revision) ON DELETE CASCADE,
    FOREIGN KEY (candidate_id,entry_id,revision) REFERENCES analysis_candidate(id,entry_id,revision) ON DELETE CASCADE,
    UNIQUE(review_id,candidate_id)
);
--rollback DROP TABLE review_candidate_decision;
--rollback ALTER TABLE entry_tag DROP CONSTRAINT ck_tag_origin;
--rollback ALTER TABLE entry_tag DROP CONSTRAINT fk_tag_candidate;
--rollback ALTER TABLE entry_tag DROP COLUMN candidate_id;
--rollback DROP TABLE analysis_candidate;
--rollback DROP TABLE ai_task;
--rollback ALTER TABLE report DROP CONSTRAINT uq_report_owner;
--rollback ALTER TABLE diary_entry DROP CONSTRAINT uq_entry_owner;

--changeset diary:007-ai-invalidation dbms:postgresql splitStatements:false
CREATE FUNCTION invalidate_entry_ai() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.revision <> OLD.revision OR (NEW.review_complete AND NOT OLD.review_complete) THEN
        UPDATE ai_task SET status=CASE WHEN NEW.revision<>OLD.revision THEN 'STALE' ELSE 'CANCELLED' END,
            error_code=CASE WHEN NEW.revision<>OLD.revision THEN 'SOURCE_DATA_CHANGED' ELSE 'REVIEW_ALREADY_COMPLETED' END,
            lease_token=NULL, lease_until=NULL, finished_at=CURRENT_TIMESTAMP,
            version=version+1, updated_at=CURRENT_TIMESTAMP
        WHERE entry_id=NEW.id AND status IN ('QUEUED','RUNNING');
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER tr_invalidate_entry_ai AFTER UPDATE OF revision,review_complete ON diary_entry
FOR EACH ROW EXECUTE FUNCTION invalidate_entry_ai();
CREATE FUNCTION invalidate_report_ai() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    UPDATE ai_task SET status='STALE', error_code='SOURCE_DATA_CHANGED', lease_token=NULL, lease_until=NULL,
        finished_at=CURRENT_TIMESTAMP, version=version+1, updated_at=CURRENT_TIMESTAMP
    WHERE user_id=NEW.id AND kind='REPORT' AND source_data_revision<>NEW.data_revision AND status IN ('QUEUED','RUNNING');
    RETURN NEW;
END;
$$;
CREATE TRIGGER tr_invalidate_report_ai AFTER UPDATE OF data_revision ON app_user
FOR EACH ROW EXECUTE FUNCTION invalidate_report_ai();
CREATE FUNCTION cancel_disabled_ai() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF NOT NEW.ai_enabled THEN
        UPDATE ai_task SET status='CANCELLED', error_code='AI_DISABLED', lease_token=NULL, lease_until=NULL,
            finished_at=CURRENT_TIMESTAMP, version=version+1, updated_at=CURRENT_TIMESTAMP
        WHERE user_id=NEW.user_id AND status IN ('QUEUED','RUNNING');
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER tr_cancel_disabled_ai AFTER UPDATE OF ai_enabled ON user_settings
FOR EACH ROW EXECUTE FUNCTION cancel_disabled_ai();
--rollback DROP TRIGGER tr_cancel_disabled_ai ON user_settings; DROP FUNCTION cancel_disabled_ai(); DROP TRIGGER tr_invalidate_report_ai ON app_user; DROP FUNCTION invalidate_report_ai(); DROP TRIGGER tr_invalidate_entry_ai ON diary_entry; DROP FUNCTION invalidate_entry_ai();
