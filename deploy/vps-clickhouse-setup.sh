#!/bin/bash
# Deploy ClickHouse on VPS and switch ISPF event journal to clickhouse backend.
set -euo pipefail

INSTALL_ROOT="${ISPF_INSTALL_ROOT:-/opt/ispf}"
ENV_FILE="${ISPF_ENV_FILE:-$INSTALL_ROOT/ispf-server.env}"
COMPOSE_SRC="${1:-$INSTALL_ROOT/docker-compose.clickhouse.yml}"
COMPOSE_FILE="$INSTALL_ROOT/docker-compose.clickhouse.yml"
CLICKHOUSE_URL="${ISPF_EVENT_JOURNAL_CLICKHOUSE_URL:-http://127.0.0.1:8123}"
SERVICE_NAME="${ISPF_SERVICE_NAME:-ispf-server}"

if ! command -v docker >/dev/null 2>&1; then
  echo "Docker is required" >&2
  exit 1
fi

if [ -f "$COMPOSE_SRC" ] && [ "$COMPOSE_SRC" != "$COMPOSE_FILE" ]; then
  install -m 644 "$COMPOSE_SRC" "$COMPOSE_FILE"
elif [ ! -f "$COMPOSE_FILE" ]; then
  echo "Missing compose file: $COMPOSE_SRC" >&2
  exit 1
fi

echo "=== Starting ClickHouse (ispf-clickhouse) ==="
cd "$INSTALL_ROOT"
if docker compose version >/dev/null 2>&1; then
  COMPOSE_CMD=(docker compose -f "$COMPOSE_FILE")
else
  COMPOSE_CMD=(docker-compose -f "$COMPOSE_FILE")
fi
"${COMPOSE_CMD[@]}" up -d --force-recreate clickhouse
docker exec ispf-clickhouse rm -f /etc/clickhouse-server/users.d/default-password.xml 2>/dev/null || true
docker restart ispf-clickhouse >/dev/null 2>&1 || true

echo "=== Waiting for ClickHouse HTTP ==="
for i in $(seq 1 60); do
  if curl -sf "${CLICKHOUSE_URL}/ping" >/dev/null 2>&1; then
    echo "ClickHouse is ready at $CLICKHOUSE_URL"
    break
  fi
  if [ "$i" -eq 60 ]; then
    echo "ClickHouse did not become ready in time" >&2
    "${COMPOSE_CMD[@]}" logs --tail 50 clickhouse >&2 || true
    exit 1
  fi
  sleep 2
done

if [ ! -f "$ENV_FILE" ]; then
  echo "Creating $ENV_FILE"
  touch "$ENV_FILE"
  chmod 600 "$ENV_FILE"
fi

upsert() {
  local key="$1"
  local value="$2"
  local tmp
  tmp="$(mktemp)"
  grep -v "^${key}=" "$ENV_FILE" > "$tmp" || true
  echo "${key}=${value}" >> "$tmp"
  mv "$tmp" "$ENV_FILE"
  chmod 600 "$ENV_FILE"
}

upsert ISPF_EVENT_JOURNAL_STORE clickhouse
upsert ISPF_EVENT_JOURNAL_CLICKHOUSE_URL "$CLICKHOUSE_URL"
upsert ISPF_EVENT_JOURNAL_CLICKHOUSE_DATABASE ispf
upsert ISPF_EVENT_JOURNAL_CLICKHOUSE_TABLE event_history
upsert ISPF_EVENT_JOURNAL_CLICKHOUSE_USERNAME ""
upsert ISPF_EVENT_JOURNAL_CLICKHOUSE_PASSWORD ""

echo "=== Event journal env ==="
grep '^ISPF_EVENT_JOURNAL_' "$ENV_FILE" || true

echo "=== Restarting $SERVICE_NAME ==="
systemctl restart "$SERVICE_NAME"

for i in $(seq 1 90); do
  if curl -sf http://127.0.0.1:8080/actuator/health >/dev/null 2>&1; then
    echo "ISPF health OK"
    break
  fi
  if [ "$i" -eq 90 ]; then
    echo "ISPF failed to start after ClickHouse switch" >&2
    journalctl -u "$SERVICE_NAME" -n 100 --no-pager >&2 || true
    exit 1
  fi
  sleep 2
done

echo "=== Startup log (ClickHouse journal) ==="
journalctl -u "$SERVICE_NAME" -n 200 --no-pager | grep -E 'ClickHouse|event_history Timescale|event journal' || true

echo "=== Verification ==="
curl -sf http://127.0.0.1:8080/api/v1/info
echo
"${COMPOSE_CMD[@]}" ps
echo "=== Done: event journal -> ClickHouse at $CLICKHOUSE_URL ==="
