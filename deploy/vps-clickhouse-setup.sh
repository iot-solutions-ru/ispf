#!/bin/bash
# Deploy ClickHouse on VPS and switch ISPF event journal to clickhouse backend.
set -euo pipefail

INSTALL_ROOT="${ISPF_INSTALL_ROOT:-/opt/ispf}"
ENV_FILE="${ISPF_ENV_FILE:-$INSTALL_ROOT/ispf-server.env}"
CH_PASSWORD_FILE="$INSTALL_ROOT/clickhouse-password.txt"
CLICKHOUSE_URL="${ISPF_EVENT_JOURNAL_CLICKHOUSE_URL:-http://127.0.0.1:8123}"
SERVICE_NAME="${ISPF_SERVICE_NAME:-ispf-server}"
CH_IMAGE="${ISPF_CLICKHOUSE_IMAGE:-clickhouse/clickhouse-server:24.8}"

if ! command -v docker >/dev/null 2>&1; then
  echo "Docker is required" >&2
  exit 1
fi

mkdir -p "$INSTALL_ROOT"

if [ ! -f "$CH_PASSWORD_FILE" ]; then
  openssl rand -hex 16 > "$CH_PASSWORD_FILE"
  chmod 600 "$CH_PASSWORD_FILE"
fi
CH_PASSWORD="$(tr -d '\r\n' < "$CH_PASSWORD_FILE")"

echo "=== Starting ClickHouse (ispf-clickhouse) ==="
docker rm -f ispf-clickhouse 2>/dev/null || true
docker run -d --name ispf-clickhouse --restart unless-stopped \
  -p 127.0.0.1:8123:8123 \
  -p 127.0.0.1:9000:9000 \
  -e CLICKHOUSE_DB=ispf \
  -e CLICKHOUSE_USER=default \
  -e CLICKHOUSE_PASSWORD="$CH_PASSWORD" \
  -e CLICKHOUSE_DEFAULT_ACCESS_MANAGEMENT=1 \
  -v ispf_clickhouse-data:/var/lib/clickhouse \
  "$CH_IMAGE"

echo "=== Waiting for ClickHouse HTTP ==="
for i in $(seq 1 60); do
  if curl -sf "${CLICKHOUSE_URL}/ping" >/dev/null 2>&1; then
    echo "ClickHouse is ready at $CLICKHOUSE_URL"
    break
  fi
  if [ "$i" -eq 60 ]; then
    echo "ClickHouse did not become ready in time" >&2
    docker logs --tail 50 ispf-clickhouse >&2 || true
    exit 1
  fi
  sleep 2
done

if ! curl -sf -u "default:${CH_PASSWORD}" "${CLICKHOUSE_URL}/?database=default&query=SELECT+1" >/dev/null; then
  echo "ClickHouse auth check failed" >&2
  exit 1
fi

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
upsert ISPF_EVENT_JOURNAL_CLICKHOUSE_USERNAME default
upsert ISPF_EVENT_JOURNAL_CLICKHOUSE_PASSWORD "$CH_PASSWORD"

echo "=== Event journal env ==="
grep '^ISPF_EVENT_JOURNAL_' "$ENV_FILE" | sed 's/PASSWORD=.*/PASSWORD=***/'

echo "=== Restarting $SERVICE_NAME ==="
systemctl restart "$SERVICE_NAME"

for i in $(seq 1 120); do
  if curl -sf http://127.0.0.1:8080/actuator/health >/dev/null 2>&1; then
    echo "ISPF health OK"
    break
  fi
  if [ "$i" -eq 120 ]; then
    echo "ISPF failed to start after ClickHouse switch" >&2
    journalctl -u "$SERVICE_NAME" -n 100 --no-pager >&2 || true
    exit 1
  fi
  sleep 2
done

echo "=== Startup log (ClickHouse journal) ==="
journalctl -u "$SERVICE_NAME" -n 200 --no-pager | grep -E 'ClickHouse event journal|TimescaleDB hypertable skipped for event_history' || true

echo "=== Verification ==="
curl -sf http://127.0.0.1:8080/api/v1/info
echo
docker exec ispf-clickhouse clickhouse-client --password "$CH_PASSWORD" -q "SHOW TABLES FROM ispf"
echo "=== Done: event journal -> ClickHouse at $CLICKHOUSE_URL ==="
