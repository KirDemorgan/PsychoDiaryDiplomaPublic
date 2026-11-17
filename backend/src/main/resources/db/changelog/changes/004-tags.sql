--liquibase formatted sql
--changeset diary:004-tags dbms:postgresql
CREATE TABLE tag_review (
    id uuid PRIMARY KEY,
    version bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    entry_id uuid NOT NULL,
    revision integer NOT NULL,
    complete boolean NOT NULL,
    FOREIGN KEY (entry_id, revision) REFERENCES entry_revision(entry_id, revision) ON DELETE CASCADE,
    UNIQUE (id, entry_id, revision)
);
ALTER TABLE diary_entry ADD COLUMN current_review_id uuid;
ALTER TABLE diary_entry ADD CONSTRAINT fk_current_review
    FOREIGN KEY (current_review_id, id, revision) REFERENCES tag_review(id, entry_id, revision)
    DEFERRABLE INITIALLY DEFERRED;
ALTER TABLE diary_entry ADD CONSTRAINT ck_complete_review CHECK (NOT review_complete OR current_review_id IS NOT NULL);
CREATE TABLE entry_tag (
    id uuid PRIMARY KEY,
    version bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    review_id uuid NOT NULL,
    entry_id uuid NOT NULL,
    revision integer NOT NULL,
    label_code varchar(30) NOT NULL CHECK (label_code IN ('SLEEP_DEFICIT','WORK_STUDY','SOCIAL_EVENT','CONFLICT',
        'PHYSICAL_ACTIVITY','CAFFEINE','ANXIETY','CALM','FATIGUE','NAUSEA','HEADACHE','TREMOR')),
    quote text NOT NULL CHECK (length(quote) > 0),
    start_index integer NOT NULL CHECK (start_index >= 0),
    end_index integer NOT NULL CHECK (end_index > start_index AND end_index <= 10000),
    presence varchar(20) NOT NULL CHECK (presence IN ('PRESENT','NEGATED','UNCERTAIN')),
    temporal varchar(20) NOT NULL CHECK (temporal IN ('CURRENT','PAST','FUTURE','UNSPECIFIED')),
    subject varchar(20) NOT NULL CHECK (subject IN ('SELF','OTHER','UNSPECIFIED')),
    applies_to_local_date date,
    origin varchar(20) NOT NULL CHECK (origin IN ('MANUAL','AI')),
    FOREIGN KEY (review_id, entry_id, revision) REFERENCES tag_review(id, entry_id, revision) ON DELETE CASCADE,
    UNIQUE (review_id, label_code, start_index, end_index)
);
CREATE INDEX ix_review_entry ON tag_review(entry_id, revision, created_at, id);
CREATE INDEX ix_tag_review ON entry_tag(review_id, start_index, id);
CREATE INDEX ix_tag_day ON entry_tag(applies_to_local_date, label_code)
    WHERE presence='PRESENT' AND subject='SELF' AND temporal<>'FUTURE';
--rollback DROP TABLE entry_tag;
--rollback ALTER TABLE diary_entry DROP CONSTRAINT ck_complete_review;
--rollback ALTER TABLE diary_entry DROP CONSTRAINT fk_current_review;
--rollback ALTER TABLE diary_entry DROP COLUMN current_review_id;
--rollback DROP TABLE tag_review;
