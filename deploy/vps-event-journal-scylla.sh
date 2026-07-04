#!/bin/bash
# Switch ISPF event journal to Scylla/Cassandra (ispf-scylla must be running).
set -euo pipefail

INSTALL_ROOT="${ISPF_INSTALL_ROOT:-/opt/ispf}"
ENV_FILE="${ISPF_ENV_FILE:-$INSTALL_ROOT/ispf-server.env}"
SERVICE_NAME="${ISPF_SERVICE_NAME:-ispf-server}"
CONTACT_POINTS="${ISPF_EVENT_JOURNAL_CASSANDRA_CONTACT_POINTS:-127.0.0.1}"
CASSANDRA_PORT="${ISPF_EVENT_JOURNAL_CASSANDRA_PORT:-9042}"
LOCAL_DC="${ISPF_EVENT_JOURNAL_CASSANDRA_LOCAL_DATACENTER:-datacenter1}"
KEYSPACE="${ISPF_EVENT_JOURNAL_CASSANDRA_KEYSPACE:-ispf}"
TABLE="${ISPF_EVENT_JOURNAL_CASSANDRA_TABLE:-event_history}"
STORE="${ISPF_EVENT_JOURNAL_STORE:-scylla}"

docker ps --filter name=ispf-scylla --format '{{.Names}}' | grep -q ispf-scylla \
  || { echo "ispf-scylla container not running — run vps-scylla-setup.sh first" >&2; exit 1; }

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

strip_clickhouse() {
  local tmp
  tmp="$(mktemp)"
  grep -v '^ISPF_EVENT_JOURNAL_CLICKHOUSE_' "$ENV_FILE" > "$tmp" || true
  mv "$tmp" "$ENV_FILE"
  chmod 600 "$ENV_FILE"
}

echo "=== Enable event journal -> ${STORE} ==="
strip_clickhouse
upsert ISPF_EVENT_JOURNAL_STORE "$STORE"
upsert ISPF_EVENT_JOURNAL_CASSANDRA_CONTACT_POINTS "$CONTACT_POINTS"
upsert ISPF_EVENT_JOURNAL_CASSANDRA_PORT "$CASSANDRA_PORT"
upsert ISPF_EVENT_JOURNAL_CASSANDRA_LOCAL_DATACENTER "$LOCAL_DC"
upsert ISPF_EVENT_JOURNAL_CASSANDRA_KEYSPACE "$KEYSPACE"
upsert ISPF_EVENT_JOURNAL_CASSANDRA_TABLE "$TABLE"

grep '^ISPF_EVENT_JOURNAL_' "$ENV_FILE" | sed 's/PASSWORD=.*/PASSWORD=***/'

echo "=== Restart $SERVICE_NAME ==="
systemctl restart "$SERVICE_NAME"
for i in $(seq 1 180); do
  if curl -sf http://127.0.0.1:8080/actuator/health/liveness >/dev/null 2>&1; then
    echo "ISPF liveness OK"
    break
  fi
  if [ "$i" -eq 180 ]; then
    echo "WARN: liveness wait timed out — check journalctl -u $SERVICE_NAME" >&2
    journalctl -u "$SERVICE_NAME" -n 40 --no-pager
    exit 1
  fi
  sleep 2
done

echo "=== Startup log ==="
journalctl -u ispf-server -n 200 --no-pager \
  | grep -E 'Cassandra event journal ready|ClickHouse event journal' \
  | tail -3 || true

echo "=== Done: ISPF_EVENT_JOURNAL_STORE=${STORE} ==="
