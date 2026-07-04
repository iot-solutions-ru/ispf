#!/bin/bash
# Switch ISPF variable historian to ClickHouse (event journal CH must already be running).
set -euo pipefail

INSTALL_ROOT="${ISPF_INSTALL_ROOT:-/opt/ispf}"
ENV_FILE="${ISPF_ENV_FILE:-$INSTALL_ROOT/ispf-server.env}"
CH_PASSWORD_FILE="${ISPF_CLICKHOUSE_PASSWORD_FILE:-$INSTALL_ROOT/clickhouse-password.txt}"
CLICKHOUSE_URL="${ISPF_VARIABLE_HISTORY_CLICKHOUSE_URL:-${ISPF_EVENT_JOURNAL_CLICKHOUSE_URL:-http://127.0.0.1:8123}}"
SERVICE_NAME="${ISPF_SERVICE_NAME:-ispf-server}"

if [ ! -f "$CH_PASSWORD_FILE" ]; then
  echo "Missing $CH_PASSWORD_FILE — run vps-clickhouse-setup.sh first" >&2
  exit 1
fi
CH_PASSWORD="$(tr -d '\r\n' < "$CH_PASSWORD_FILE")"

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

echo "=== Enable variable historian -> ClickHouse ==="
upsert ISPF_VARIABLE_HISTORY_STORE clickhouse
upsert ISPF_VARIABLE_HISTORY_CLICKHOUSE_URL "$CLICKHOUSE_URL"
upsert ISPF_VARIABLE_HISTORY_CLICKHOUSE_DATABASE ispf
upsert ISPF_VARIABLE_HISTORY_CLICKHOUSE_TABLE variable_samples
upsert ISPF_VARIABLE_HISTORY_CLICKHOUSE_USERNAME default
upsert ISPF_VARIABLE_HISTORY_CLICKHOUSE_PASSWORD "$CH_PASSWORD"

grep '^ISPF_VARIABLE_HISTORY_' "$ENV_FILE" | sed 's/PASSWORD=.*/PASSWORD=***/'

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
  | grep -E 'ClickHouse variable history ready|TimescaleDB hypertable skipped for variable_samples' \
  | tail -3 || true

echo "=== Done: ISPF_VARIABLE_HISTORY_STORE=clickhouse ==="
