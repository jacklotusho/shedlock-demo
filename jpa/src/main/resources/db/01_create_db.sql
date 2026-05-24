-- =============================================================================
-- 01_create_db.sql
-- Run as postgres superuser:
--   psql -U postgres -f 01_create_db.sql
-- =============================================================================

-- Create database owned by that user
CREATE DATABASE shedlock_demo
    OWNER       = engine
    ENCODING    = 'UTF8'
    LC_COLLATE  = 'en_US.UTF-8'
    LC_CTYPE    = 'en_US.UTF-8'
    TEMPLATE    = template0;

-- Grant all privileges
GRANT ALL PRIVILEGES ON DATABASE shedlock_demo TO engine;

