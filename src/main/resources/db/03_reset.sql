-- =============================================================================
-- 03_reset.sql  (DEV / TEST only — never run in production)
-- Truncates all data and resets sequences for a clean test run.
--   psql -U shedlock_user -d shedlock_demo -f 03_reset.sql
-- =============================================================================

TRUNCATE TABLE tasks    RESTART IDENTITY CASCADE;
TRUNCATE TABLE shedlock;
