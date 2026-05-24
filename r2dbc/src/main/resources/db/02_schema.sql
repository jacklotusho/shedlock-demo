-- =============================================================================
-- 02_schema.sql
-- Run as shedlock_user against shedlock_demo:
--   psql -U shedlock_user -d shedlock_demo -f 02_schema.sql
--
-- NOTE: If using spring.jpa.hibernate.ddl-auto=update or validate,
--       run this script BEFORE starting the Spring Boot app.
--       If using ddl-auto=create or create-drop, Hibernate manages the schema
--       automatically — this script is only needed for production/staging.
-- =============================================================================

-- =============================================================================
-- TASKS table
-- =============================================================================
CREATE TABLE IF NOT EXISTS tasks (
    id            BIGSERIAL       PRIMARY KEY,
    uuid          VARCHAR(64)     NOT NULL,
    sequence      INTEGER         NOT NULL,
    type          INTEGER         NOT NULL,
    description   VARCHAR(255)    NOT NULL,
    state         VARCHAR(20)     NOT NULL DEFAULT 'PENDING',
    processed_by  VARCHAR(255),
    created_at    TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    started_at    TIMESTAMPTZ,
    completed_at  TIMESTAMPTZ,
    error_message VARCHAR(1000),
    retry_count   INTEGER         NOT NULL DEFAULT 0,

    -- Prevent duplicate tasks for the same workflow step
    CONSTRAINT uk_task_uuid_sequence_type UNIQUE (uuid, sequence, type),

    -- State must be one of the valid enum values
    CONSTRAINT chk_task_state CHECK (state IN ('PENDING','RUNNING','COMPLETED','FAILED'))
);

-- Index for scheduler: fast lookup of PENDING tasks ordered by creation time
CREATE INDEX IF NOT EXISTS idx_tasks_state
    ON tasks (state, created_at ASC);

-- Index for workflow status queries: /api/requests/{uuid}
CREATE INDEX IF NOT EXISTS idx_tasks_uuid
    ON tasks (uuid);

COMMENT ON TABLE  tasks                IS 'Workflow task queue processed by provision threads';
COMMENT ON COLUMN tasks.uuid           IS 'Parent SystemRequest UUID';
COMMENT ON COLUMN tasks.sequence       IS 'Step number within the workflow (1-based)';
COMMENT ON COLUMN tasks.type           IS '1=ProvisionVM 2=InstallSoftware 3=ConfigureNetwork 4=HealthCheck';
COMMENT ON COLUMN tasks.state          IS 'State machine: PENDING → RUNNING → COMPLETED | FAILED';
COMMENT ON COLUMN tasks.processed_by   IS 'Hostname of the pod/thread that claimed this task';

-- =============================================================================
-- SHEDLOCK table  (replaces custom scheduler_locks from original proposal)
-- =============================================================================
CREATE TABLE IF NOT EXISTS shedlock (
    name        VARCHAR(64)     NOT NULL,
    lock_until  TIMESTAMPTZ     NOT NULL,
    locked_at   TIMESTAMPTZ     NOT NULL,
    locked_by   VARCHAR(255)    NOT NULL,

    CONSTRAINT pk_shedlock PRIMARY KEY (name)
);

COMMENT ON TABLE  shedlock            IS 'Distributed lock table managed by ShedLock library';
COMMENT ON COLUMN shedlock.name       IS 'Lock name — matches @SchedulerLock(name=...) annotation';
COMMENT ON COLUMN shedlock.lock_until IS 'Lock expires at this time (lockAtMostFor safety net)';
COMMENT ON COLUMN shedlock.locked_at  IS 'When the lock was acquired';
COMMENT ON COLUMN shedlock.locked_by  IS 'Hostname of the pod holding the lock';
