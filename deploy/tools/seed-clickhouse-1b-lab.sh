#!/usr/bin/env bash
# BL-210: bulk seed ClickHouse variable_samples for Enterprise L 1B gate (lab only).
set -euo pipefail

CH_URL="${ISPF_VARIABLE_HISTORY_CLICKHOUSE_URL:-http://127.0.0.1:8123}"
CH_USER="${ISPF_VARIABLE_HISTORY_CLICKHOUSE_USERNAME:-}"
CH_PASS="${ISPF_VARIABLE_HISTORY_CLICKHOUSE_PASSWORD:-}"
TARGET="${ISPF_ANALYTICS_BENCH_CH_MIN_SAMPLES:-1000000000}"
TAG_MOD="${ISPF_ANALYTICS_BENCH_CATALOG_MIN_TAGS:-50000}"
PAD=$(( ${#TAG_MOD} < 5 ? 5 : ${#TAG_MOD} ))
# Keep synthetic timestamps inside historian TTL (default 90d) so rows are not deleted during ingest.
RETENTION_SEC="${ISPF_ANALYTICS_BENCH_CH_RETENTION_SEC:-$((75 * 86400))}"
BATCH="${ISPF_ANALYTICS_BENCH_CH_SEED_BATCH:-1000000}"

ch_curl() {
  if [[ -n "$CH_PASS" ]]; then
    curl -sf -u "${CH_USER:-default}:${CH_PASS}" "$@"
  else
    curl -sf "$@"
  fi
}

ch_insert() {
  local query="$1"
  if [[ -n "$CH_PASS" ]]; then
    curl -sf --max-time 7200 -u "${CH_USER:-default}:${CH_PASS}" --data-binary "$query" "${CH_URL}/"
  else
    curl -sf --max-time 7200 --data-binary "$query" "${CH_URL}/"
  fi
}

ch_count() {
  ch_curl "${CH_URL}/?query=SELECT%20count()%20FROM%20ispf.variable_samples" 2>/dev/null | tr -d '[:space:]' || echo 0
}

echo "==> ClickHouse 1B seed target=${TARGET} url=${CH_URL}"

EXISTING="$(ch_count)"
echo "==> Existing rows: ${EXISTING:-0}"
if [[ "${EXISTING:-0}" -ge "$TARGET" ]]; then
  echo "==> Already >= ${TARGET} rows — skip"
  exit 0
fi

echo "==> Inserting until count >= ${TARGET} (batch=${BATCH}, retention_window=${RETENTION_SEC}s)..."

while [[ "$(ch_count)" -lt "$TARGET" ]]; do
  BEFORE="$(ch_count)"
  NEED=$((TARGET - BEFORE))
  CHUNK="$BATCH"
  if [[ "$CHUNK" -gt "$NEED" ]]; then
    CHUNK="$NEED"
  fi
  SEQ_START="$BEFORE"
  echo "==> Batch seq_start=${SEQ_START} size=${CHUNK} count=${BEFORE}/${TARGET} ($(date -u +%H:%M:%S))"
  QUERY=$(cat <<SQL
INSERT INTO ispf.variable_samples (object_path, variable_name, field_name, sampled_at, value_double)
SELECT
  concat('root.platform.devices.analytics-scale-lab.tag-', lpad(toString((number % ${TAG_MOD}) + 1), ${PAD}, '0')),
  'temperature',
  'value',
  now64(3) - toIntervalSecond(1 + ((${SEQ_START} + number) % ${RETENTION_SEC})),
  randCanonical()
FROM numbers(${CHUNK})
SQL
)
  if ! ch_insert "$QUERY"; then
    echo "ERROR: ClickHouse insert failed at seq_start=${SEQ_START} size=${CHUNK}" >&2
    echo "==> Partial total_rows=$(ch_count)" >&2
    exit 1
  fi
  AFTER="$(ch_count)"
  DELTA=$((AFTER - BEFORE))
  echo "==> Progress: count=${AFTER}/${TARGET} (delta=+${DELTA})"
  if [[ "$DELTA" -le 0 ]]; then
    echo "ERROR: insert made no progress (ttl or auth issue?)" >&2
    exit 1
  fi
done

FINAL="$(ch_count)"
echo "==> Final row count: ${FINAL}"
if [[ "${FINAL:-0}" -lt "$TARGET" ]]; then
  echo "ERROR: count ${FINAL} < ${TARGET}" >&2
  exit 1
fi
echo "==> ClickHouse 1B seed OK"
