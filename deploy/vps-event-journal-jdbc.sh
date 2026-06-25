#!/bin/bash
# Revert ISPF event journal to JDBC/Timescale (disable ClickHouse backend).
set -euo pipefail

ENV_FILE="${ISPF_ENV_FILE:-/opt/ispf/ispf-server.env}"
SERVICE_NAME="${ISPF_SERVICE_NAME:-ispf-server}"

if [ ! -f "$ENV_FILE" ]; then
  echo "No env file: $ENV_FILE" >&2
  exit 1
fi

tmp="$(mktemp)"
grep -v '^ISPF_EVENT_JOURNAL_STORE=' "$ENV_FILE" | grep -v '^ISPF_EVENT_JOURNAL_CLICKHOUSE_' > "$tmp" || true
echo "ISPF_EVENT_JOURNAL_STORE=jdbc" >> "$tmp"
mv "$tmp" "$ENV_FILE"
chmod 600 "$ENV_FILE"

echo "=== Restarting $SERVICE_NAME (jdbc event journal) ==="
systemctl restart "$SERVICE_NAME"

for i in $(seq 1 60); do
  if curl -sf http://127.0.0.1:8080/actuator/health >/dev/null 2>&1; then
    echo "ISPF health OK — event journal reverted to jdbc"
    exit 0
  fi
  sleep 2
done

echo "ISPF failed to start" >&2
journalctl -u "$SERVICE_NAME" -n 80 --no-pager >&2 || true
exit 1
