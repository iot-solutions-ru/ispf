#!/usr/bin/env bash
# BL-145: Agent store-forward field soak — accelerated 30-day buffer replay timeline.
# Polls GET /api/v1/agent/store-forward/stats and optionally cycles outbound tunnel disconnect/reconnect.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT"

BASE_URL="${ISPF_SOAK_BASE_URL:-http://localhost:8080}"
TOKEN="${ISPF_SOAK_TOKEN:-}"
USER="${ISPF_SOAK_USER:-admin}"
PASS="${ISPF_SOAK_PASS:-admin}"
SIM_DAYS="${ISPF_SOAK_SIM_DAYS:-30}"
WALL_MINUTES="${ISPF_SOAK_WALL_MINUTES:-30}"
POLL_SEC="${ISPF_SOAK_POLL_SEC:-10}"
OUTBOUND_AGENT_ID="${ISPF_SOAK_OUTBOUND_AGENT_ID:-}"
EVENT_PATH="${ISPF_SOAK_EVENT_PATH:-root.platform.devices.demo-sensor-01}"
EVENT_VAR="${ISPF_SOAK_EVENT_VAR:-temperature}"
DATA_DIR="${ISPF_DATA_DIR:-}"
REPORT_DIR="${ISPF_SOAK_REPORT_DIR:-$ROOT/build/agent-store-forward-soak}"
mkdir -p "$REPORT_DIR"
REPORT_FILE="$REPORT_DIR/soak-report.md"
CSV_FILE="$REPORT_DIR/soak-metrics.csv"

WALL_SECONDS=$((WALL_MINUTES * 60))
if (( WALL_SECONDS <= 0 )); then
  echo "ISPF_SOAK_WALL_MINUTES must be > 0" >&2
  exit 1
fi

log() {
  echo "==> $*"
}

auth_header() {
  if [[ -z "$TOKEN" ]]; then
    TOKEN="$(curl -sf "${BASE_URL}/api/v1/auth/login" \
      -H 'Content-Type: application/json' \
      -d "{\"username\":\"${USER}\",\"password\":\"${PASS}\"}" \
      | sed -n 's/.*"token"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p')"
  fi
  if [[ -z "$TOKEN" ]]; then
    echo "Failed to obtain auth token (set ISPF_SOAK_TOKEN or ISPF_SOAK_USER/PASS)" >&2
    exit 1
  fi
  printf 'Authorization: Bearer %s' "$TOKEN"
}

fetch_stats() {
  curl -sf "${BASE_URL}/api/v1/agent/store-forward/stats" -H "$(auth_header)"
}

json_field() {
  local json="$1"
  local field="$2"
  printf '%s' "$json" | sed -n "s/.*\"${field}\"[[:space:]]*:[[:space:]]*\([0-9][0-9]*\).*/\1/p" | head -n1
}

json_bool() {
  local json="$1"
  local field="$2"
  printf '%s' "$json" | sed -n "s/.*\"${field}\"[[:space:]]*:[[:space:]]*\(true\|false\).*/\1/p" | head -n1
}

sim_day_for_elapsed() {
  local elapsed="$1"
  awk -v elapsed="$elapsed" -v sim="$SIM_DAYS" -v wall="$WALL_SECONDS" \
    'BEGIN { printf "%.2f", (elapsed / wall) * sim }'
}

maybe_cycle_tunnel() {
  local sim_day="$1"
  if [[ -z "$OUTBOUND_AGENT_ID" ]]; then
    return 0
  fi
  local day_int
  day_int="$(printf '%.0f' "$sim_day")"
  if (( day_int % 7 != 0 )); then
    return 0
  fi
  log "Sim day ${day_int}: outbound tunnel disconnect/reconnect cycle"
  curl -sf -X PUT "${BASE_URL}/api/v1/federation/outbound/agents/${OUTBOUND_AGENT_ID}" \
    -H "$(auth_header)" \
    -H 'Content-Type: application/json' \
    -d "{\"name\":\"soak-agent\",\"hubBaseUrl\":\"${BASE_URL}\",\"pathPrefix\":\"root.platform\",\"enabled\":false}" \
    >/dev/null || true
  sleep 2
  for _ in 1 2 3; do
    curl -sf -X PATCH \
      "${BASE_URL}/api/v1/objects/by-path/variables/value?path=${EVENT_PATH}&name=${EVENT_VAR}" \
      -H "$(auth_header)" \
      -H 'Content-Type: application/json' \
      -d '{"schema":{"name":"temperature","fields":[{"name":"value","type":"DOUBLE"}]},"rows":[{"value":42.0}]}' \
      >/dev/null || true
    sleep 1
  done
  curl -sf -X PUT "${BASE_URL}/api/v1/federation/outbound/agents/${OUTBOUND_AGENT_ID}" \
    -H "$(auth_header)" \
    -H 'Content-Type: application/json' \
    -d "{\"name\":\"soak-agent\",\"hubBaseUrl\":\"${BASE_URL}\",\"pathPrefix\":\"root.platform\",\"enabled\":true}" \
    >/dev/null || true
}

check_persist_file() {
  if [[ -z "$DATA_DIR" ]]; then
    return 0
  fi
  local file="${DATA_DIR%/}/agent/store-forward-buffer.json"
  if [[ -f "$file" ]]; then
    echo "- Persist file present: \`${file}\` ($(wc -c < "$file" | tr -d ' ') bytes)" >> "$REPORT_FILE"
  else
    echo "- Persist file missing: \`${file}\`" >> "$REPORT_FILE"
  fi
}

{
  echo "# Agent store-forward soak (BL-145)"
  echo ""
  echo "- Started: $(date -u +"%Y-%m-%dT%H:%M:%SZ")"
  echo "- Base URL: ${BASE_URL}"
  echo "- Simulated days: ${SIM_DAYS}"
  echo "- Wall duration: ${WALL_MINUTES} min (${WALL_SECONDS}s)"
  echo "- Poll interval: ${POLL_SEC}s"
  echo "- Outbound agent: ${OUTBOUND_AGENT_ID:-none}"
  echo ""
} > "$REPORT_FILE"
echo "sim_day,wall_elapsed_sec,total_pending,total_bytes,total_dropped,enabled,persist_to_disk" > "$CSV_FILE"

log "Checking server health"
curl -sf "${BASE_URL}/api/v1/info" >/dev/null

log "Baseline store-forward stats"
BASE_STATS="$(fetch_stats)"
echo "- Enabled: $(json_bool "$BASE_STATS" enabled)" >> "$REPORT_FILE"
echo "- Persist to disk: $(json_bool "$BASE_STATS" persistToDisk)" >> "$REPORT_FILE"
check_persist_file

START=$SECONDS
LAST_DAY=-1
MAX_PENDING=0
MAX_DROPPED=0

while (( SECONDS - START < WALL_SECONDS )); do
  ELAPSED=$((SECONDS - START))
  SIM_DAY="$(sim_day_for_elapsed "$ELAPSED")"
  DAY_INT="$(printf '%.0f' "$SIM_DAY")"

  if (( DAY_INT > LAST_DAY )); then
    maybe_cycle_tunnel "$SIM_DAY"
    LAST_DAY=$DAY_INT
  fi

  STATS="$(fetch_stats)"
  PENDING="$(json_field "$STATS" totalPending)"
  BYTES="$(json_field "$STATS" totalBytes)"
  DROPPED="$(json_field "$STATS" totalDropped)"
  ENABLED="$(json_bool "$STATS" enabled)"
  PERSIST="$(json_bool "$STATS" persistToDisk)"

  PENDING="${PENDING:-0}"
  BYTES="${BYTES:-0}"
  DROPPED="${DROPPED:-0}"

  if (( PENDING > MAX_PENDING )); then MAX_PENDING=$PENDING; fi
  if (( DROPPED > MAX_DROPPED )); then MAX_DROPPED=$DROPPED; fi

  echo "${SIM_DAY},${ELAPSED},${PENDING},${BYTES},${DROPPED},${ENABLED},${PERSIST}" >> "$CSV_FILE"
  log "simDay=${SIM_DAY} pending=${PENDING} bytes=${BYTES} dropped=${DROPPED}"

  sleep "$POLL_SEC"
done

FINAL_STATS="$(fetch_stats)"
{
  echo ""
  echo "## Summary"
  echo "- Completed: $(date -u +"%Y-%m-%dT%H:%M:%SZ")"
  echo "- Peak pending: ${MAX_PENDING}"
  echo "- Peak dropped: ${MAX_DROPPED}"
  echo "- Final pending: $(json_field "$FINAL_STATS" totalPending)"
  echo "- Final dropped: $(json_field "$FINAL_STATS" totalDropped)"
  echo "- Metrics CSV: \`${CSV_FILE#${ROOT}/}\`"
  echo ""
  echo "Pass criteria: \`totalDropped\` stable after reconnect cycles; \`totalPending\` drains to 0 when tunnel connected."
} >> "$REPORT_FILE"

log "Soak complete — report: $REPORT_FILE"
