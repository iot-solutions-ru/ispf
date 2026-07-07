#!/bin/bash
# Reconcile flyway_schema_history after migration squash or edited scripts.
# 1) Squash history to V1 when incremental migrations were removed (ADR-0037 greenfield).
# 2) Flyway repair — sync checksums with migrations embedded in the server jar.
set -euo pipefail

JAR_PATH="${1:-}"
INSTALL_ROOT="${ISPF_INSTALL_ROOT:-/opt/ispf}"
ENV_FILE="${ISPF_ENV_FILE:-$INSTALL_ROOT/ispf-server.env}"

if [ -z "$JAR_PATH" ] || [ ! -f "$JAR_PATH" ]; then
  echo "Usage: $0 /opt/ispf/ispf-server.jar" >&2
  exit 1
fi

if [ ! -f "$ENV_FILE" ]; then
  echo "Missing env file: $ENV_FILE" >&2
  exit 1
fi

if ! command -v docker >/dev/null 2>&1; then
  echo "docker not found; skipping Flyway repair" >&2
  exit 0
fi

read_env_var() {
  local key="$1"
  local line
  line="$(grep -E "^${key}=" "$ENV_FILE" | tail -1 || true)"
  if [ -z "$line" ]; then
    return 1
  fi
  printf '%s' "${line#*=}"
}

URL="$(read_env_var SPRING_DATASOURCE_URL || true)"
USER="$(read_env_var SPRING_DATASOURCE_USERNAME || true)"
PASS="$(read_env_var SPRING_DATASOURCE_PASSWORD || true)"
METADATA_KIND="$(read_env_var ISPF_METADATA_DB_KIND || true)"
METADATA_KIND="${METADATA_KIND:-postgresql}"

if [ -z "$URL" ] || [ -z "$USER" ] || [ -z "$PASS" ]; then
  echo "SPRING_DATASOURCE_URL/USERNAME/PASSWORD required in $ENV_FILE" >&2
  exit 1
fi

case "$METADATA_KIND" in
  ""|postgresql|postgres|pg) MIG_SUBDIR="postgresql" ;;
  h2) MIG_SUBDIR="h2" ;;
  mssql|sqlserver|sql-server) MIG_SUBDIR="mssql" ;;
  mysql|mariadb) MIG_SUBDIR="mysql" ;;
  oracle) MIG_SUBDIR="oracle" ;;
  *)
    echo "Unsupported ISPF_METADATA_DB_KIND for repair: $METADATA_KIND" >&2
    exit 1
    ;;
esac

TMP_MIG="$(mktemp -d)"
trap 'rm -rf "$TMP_MIG"' EXIT

unzip -j -qo "$JAR_PATH" "BOOT-INF/classes/db/migration/${MIG_SUBDIR}/*.sql" -d "$TMP_MIG"

if [ -z "$(find "$TMP_MIG" -maxdepth 1 -name 'V*.sql' -print -quit)" ]; then
  echo "No migrations found in $JAR_PATH (db/migration/${MIG_SUBDIR})" >&2
  exit 1
fi

echo "Flyway repair: $(find "$TMP_MIG" -maxdepth 1 -name 'V*.sql' | wc -l) migration(s) from $(basename "$JAR_PATH") (${MIG_SUBDIR})"

# Squash legacy incremental history (V2+) when only V1 baseline remains in the jar.
if [ "$MIG_SUBDIR" = "postgresql" ] && [ "$(find "$TMP_MIG" -maxdepth 1 -name 'V*.sql' | wc -l)" -eq 1 ]; then
  echo "Squashing flyway_schema_history to V1 baseline (if legacy rows exist)..."
  docker run --rm --network host \
    -e PGPASSWORD="$PASS" \
    postgres:16-alpine \
    psql "$URL" -U "$USER" -v ON_ERROR_STOP=1 -c "
      DO \$\$
      BEGIN
        IF EXISTS (
          SELECT 1 FROM information_schema.tables
          WHERE table_schema = current_schema() AND table_name = 'flyway_schema_history'
        ) THEN
          DELETE FROM flyway_schema_history WHERE version IS DISTINCT FROM '1';
          UPDATE flyway_schema_history
          SET script = 'V1__baseline.sql', description = 'baseline'
          WHERE version = '1';
        END IF;
      END \$\$;
    "
fi

docker run --rm --network host \
  -v "$TMP_MIG:/flyway/sql:ro" \
  flyway/flyway:11-alpine \
  -url="$URL" \
  -user="$USER" \
  -password="$PASS" \
  -locations="filesystem:/flyway/sql" \
  repair

echo "Flyway repair complete"
