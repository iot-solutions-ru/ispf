#!/bin/bash
# VPS event-journal tuning for peak load (7–8 GiB RAM host). Idempotent upsert + restart.
set -euo pipefail

INSTALL_ROOT="${ISPF_INSTALL_ROOT:-/opt/ispf}"
ENV_FILE="${ISPF_ENV_FILE:-$INSTALL_ROOT/ispf-server.env}"
SERVICE_NAME="${ISPF_SERVICE_NAME:-ispf-server}"

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

echo "=== Event journal peak tuning (VPS) ==="
upsert ISPF_EVENT_JOURNAL_ELASTIC_WRITER true
upsert ISPF_EVENT_JOURNAL_WRITER_THREADS_MIN 4
upsert ISPF_EVENT_JOURNAL_WRITER_THREADS_MAX 32
upsert ISPF_EVENT_JOURNAL_QUEUE_CAPACITY 500000
upsert ISPF_EVENT_JOURNAL_BATCH_SIZE 1000
upsert ISPF_EVENT_JOURNAL_FLUSH_INTERVAL_MS 20
upsert ISPF_EVENT_JOURNAL_CASSANDRA_PARALLEL_BATCHES 8

grep '^ISPF_EVENT_JOURNAL_' "$ENV_FILE" | grep -v PASSWORD | sort

echo "=== Restart $SERVICE_NAME ==="
systemctl restart "$SERVICE_NAME"
for i in $(seq 1 120); do
  if curl -sf http://127.0.0.1:8080/actuator/health/liveness >/dev/null 2>&1; then
    echo "ISPF liveness OK"
    break
  fi
  sleep 2
done
curl -sf http://127.0.0.1:8080/api/v1/info | python3 -m json.tool 2>/dev/null | grep version || true
