--liquibase formatted sql
--changeset diary:003-medications dbms:postgresql
CREATE TABLE medication (
    id uuid PRIMARY KEY,
    version bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    user_id uuid NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    name varchar(200) NOT NULL CHECK (length(trim(name)) > 0),
    form varchar(100),
    archived_at timestamptz,
    UNIQUE (id, user_id)
);
CREATE INDEX ix_medication_user ON medication(user_id, created_at, id);
CREATE TABLE schedule_version (
    id uuid PRIMARY KEY,
    version bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    medication_id uuid NOT NULL REFERENCES medication(id) ON DELETE CASCADE,
    effective_from timestamptz NOT NULL,
    effective_until timestamptz,
    treatment_ends_on date,
    time_zone varchar(100) NOT NULL,
    UNIQUE (id, medication_id),
    UNIQUE (medication_id, effective_from),
    CHECK (effective_until IS NULL OR effective_until > effective_from)
);
CREATE UNIQUE INDEX uq_schedule_open ON schedule_version(medication_id) WHERE effective_until IS NULL;
CREATE TABLE schedule_slot (
    id uuid PRIMARY KEY,
    version bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    medication_id uuid NOT NULL,
    schedule_version_id uuid NOT NULL,
    local_time time NOT NULL,
    days varchar(100) NOT NULL CHECK (days ~ '^(MONDAY|TUESDAY|WEDNESDAY|THURSDAY|FRIDAY|SATURDAY|SUNDAY)(,(MONDAY|TUESDAY|WEDNESDAY|THURSDAY|FRIDAY|SATURDAY|SUNDAY))*$'),
    dose numeric(14,4) NOT NULL CHECK (dose > 0),
    unit varchar(20) NOT NULL CHECK (unit IN ('MG','G','ML','TABLET','DROP','UNIT')),
    FOREIGN KEY (schedule_version_id, medication_id) REFERENCES schedule_version(id, medication_id) ON DELETE CASCADE,
    UNIQUE (id, medication_id),
    UNIQUE (schedule_version_id, local_time, days),
    CHECK (extract(second FROM local_time) = 0)
);
CREATE INDEX ix_slot_version ON schedule_slot(schedule_version_id, local_time, id);
CREATE TABLE intake_event (
    id uuid PRIMARY KEY,
    version bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    user_id uuid NOT NULL,
    medication_id uuid NOT NULL,
    schedule_slot_id uuid,
    planned_at timestamptz,
    kind varchar(20) NOT NULL CHECK (kind IN ('PLANNED','UNPLANNED')),
    status varchar(20) NOT NULL CHECK (status IN ('TAKEN','SKIPPED')),
    actual_at timestamptz,
    actual_dose numeric(14,4) CHECK (actual_dose > 0),
    unit varchar(20) CHECK (unit IN ('MG','G','ML','TABLET','DROP','UNIT')),
    FOREIGN KEY (medication_id, user_id) REFERENCES medication(id, user_id) ON DELETE CASCADE,
    FOREIGN KEY (schedule_slot_id, medication_id) REFERENCES schedule_slot(id, medication_id) ON DELETE CASCADE,
    UNIQUE (schedule_slot_id, planned_at),
    CHECK ((kind='PLANNED' AND schedule_slot_id IS NOT NULL AND planned_at IS NOT NULL)
        OR (kind='UNPLANNED' AND schedule_slot_id IS NULL AND planned_at IS NULL AND status='TAKEN')),
    CHECK ((status='TAKEN' AND actual_at IS NOT NULL AND actual_dose IS NOT NULL AND unit IS NOT NULL)
        OR (status='SKIPPED' AND actual_at IS NULL AND actual_dose IS NULL AND unit IS NULL))
);
CREATE INDEX ix_intake_user_time ON intake_event(user_id, (coalesce(planned_at, actual_at)), id);
--rollback DROP TABLE intake_event;
--rollback DROP TABLE schedule_slot;
--rollback DROP TABLE schedule_version;
--rollback DROP TABLE medication;
