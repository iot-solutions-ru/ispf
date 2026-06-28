#!/bin/bash
# Sync flyway_schema_history.checksum with migrations embedded in the server jar.
# Safe to run before every rollout when migration files were edited after prod apply.
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

if [ -z "$URL" ] || [ -z "$USER" ] || [ -z "$PASS" ]; then
  echo "SPRING_DATASOURCE_URL/USERNAME/PASSWORD required in $ENV_FILE" >&2
  exit 1
fi

TMP_MIG="$(mktemp -d)"
trap 'rm -rf "$TMP_MIG"' EXIT

unzip -j -qo "$JAR_PATH" 'BOOT-INF/classes/db/migration/*.sql' -d "$TMP_MIG"

if [ -z "$(find "$TMP_MIG" -maxdepth 1 -name 'V*.sql' -print -quit)" ]; then
  echo "No migrations found in $JAR_PATH" >&2
  exit 1
fi

echo "Flyway repair: $(find "$TMP_MIG" -maxdepth 1 -name 'V*.sql' | wc -l) migrations from $(basename "$JAR_PATH")"

docker run --rm --network host \
  -v "$TMP_MIG:/flyway/sql:ro" \
  flyway/flyway:11-alpine \
  -url="$URL" \
  -user="$USER" \
  -password="$PASS" \
  -locations="filesystem:/flyway/sql" \
  repair

echo "Flyway repair complete"
