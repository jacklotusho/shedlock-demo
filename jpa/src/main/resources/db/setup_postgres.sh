#!/usr/bin/env bash
# =============================================================================
# setup_postgres.sh
# One-shot script: creates DB, user, and schema for shedlock-demo.
#
# Usage:
#   chmod +x setup_postgres.sh
#   ./setup_postgres.sh                          # defaults
#   PGHOST=myhost PGPORT=5433 ./setup_postgres.sh
# =============================================================================
set -euo pipefail

# ── Config (override via env vars) ───────────────────────────────────────────
PGHOST="${PGHOST:-localhost}"
PGPORT="${PGPORT:-5432}"
PGADMIN_USER="${PGADMIN_USER:-postgres}"
DB_NAME="shedlock_demo"
DB_USER="shedlock_user"
DB_PASS="shedlock_pass"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

echo "========================================"
echo "  ShedLock Demo — PostgreSQL Setup"
echo "========================================"
echo "  Host : $PGHOST:$PGPORT"
echo "  DB   : $DB_NAME"
echo "  User : $DB_USER"
echo "========================================"

# ── Helper ────────────────────────────────────────────────────────────────────
run_psql_admin() {
    psql -h "$PGHOST" -p "$PGPORT" -U "$PGADMIN_USER" "$@"
}

run_psql_app() {
    PGPASSWORD="$DB_PASS" psql -h "$PGHOST" -p "$PGPORT" -U "$DB_USER" -d "$DB_NAME" "$@"
}

# ── Step 1: Create user (skip if exists) ─────────────────────────────────────
echo ""
echo "Step 1/3 — Creating user '$DB_USER'..."
run_psql_admin -c "
DO \$\$
BEGIN
  IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = '$DB_USER') THEN
    CREATE USER $DB_USER WITH PASSWORD '$DB_PASS';
    RAISE NOTICE 'User $DB_USER created.';
  ELSE
    RAISE NOTICE 'User $DB_USER already exists — skipping.';
  END IF;
END
\$\$;"

# ── Step 2: Create database (skip if exists) ──────────────────────────────────
echo ""
echo "Step 2/3 — Creating database '$DB_NAME'..."
DB_EXISTS=$(run_psql_admin -tAc "SELECT 1 FROM pg_database WHERE datname='$DB_NAME'")
if [ "$DB_EXISTS" = "1" ]; then
    echo "Database '$DB_NAME' already exists — skipping creation."
else
    run_psql_admin -c "
    CREATE DATABASE $DB_NAME
        OWNER       = $DB_USER
        ENCODING    = 'UTF8'
        LC_COLLATE  = 'en_US.UTF-8'
        LC_CTYPE    = 'en_US.UTF-8'
        TEMPLATE    = template0;"
    echo "Database '$DB_NAME' created."
fi
run_psql_admin -c "GRANT ALL PRIVILEGES ON DATABASE $DB_NAME TO $DB_USER;"

# ── Step 3: Apply schema ───────────────────────────────────────────────────────
echo ""
echo "Step 3/3 — Applying schema..."
SCHEMA_FILE="$SCRIPT_DIR/02_schema.sql"
if [ ! -f "$SCHEMA_FILE" ]; then
    echo "ERROR: schema file not found at $SCHEMA_FILE"
    exit 1
fi
run_psql_app -f "$SCHEMA_FILE"

# ── Done ──────────────────────────────────────────────────────────────────────
echo ""
echo "========================================"
echo "  ✅ Setup complete!"
echo ""
echo "  Start the app with:"
echo "    mvn spring-boot:run -Dspring-boot.run.profiles=postgres"
echo ""
echo "  Or set env var:"
echo "    SPRING_PROFILES_ACTIVE=postgres mvn spring-boot:run"
echo ""
echo "  Reset data for a clean test run:"
echo "    psql -U $DB_USER -d $DB_NAME -f 03_reset.sql"
echo "========================================"
