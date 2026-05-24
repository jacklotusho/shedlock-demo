-- Auto-executed by Spring Boot on startup (H2 default profile)
-- Creates the shedlock table that JdbcTemplateLockProvider requires.
-- For PostgreSQL profile, use db/02_schema.sql instead.

CREATE TABLE IF NOT EXISTS shedlock (
    name        VARCHAR(64)  NOT NULL,
    lock_until  TIMESTAMP    NOT NULL,
    locked_at   TIMESTAMP    NOT NULL,
    locked_by   VARCHAR(255) NOT NULL,
    PRIMARY KEY (name)
);
