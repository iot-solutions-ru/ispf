#!/usr/bin/env bash
# BL-145 agent store-forward soak (lab accelerated / field wall-clock).
#
# Polls GET /api/v1/agent/store-forward/stats and writes a report under
# build/agent-store-forward-soak/. Disconnect/replay of the tunnel is covered by
# FederationStoreForwardIntegrationTest; this script is the operator/lab journal.
#
# Usage:
#   export ISPF_SOAK_BASE_URL=http://127.0.0.1:8080
#   export ISPF_SOAK_TOKEN=<bearer>   # or ISPF_SOAK_USER / ISPF_SOAK_PASSWORD
#   bash deploy/tools/agent-store-forward-soak.sh
#
# Lab default: ISPF_SOAK_WALL_MINUTES=30 (≈30 sim-days at 86400×).
# Field GA:    ISPF_SOAK_WALL_MINUTES=43200 (30 real days).
# Smoke:       ISPF_SOAK_WALL_MINUTES=0 (single poll + report).
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]:-$0}")/../.." && pwd)"
OUT_DIR="${ISPF_SOAK_OUT_DIR:-$ROOT/build/agent-store-forward-soak}"
BASE_URL="${ISPF_SOAK_BASE_URL:-${ISPF_BASE_URL:-http://127.0.0.1:8080}}"
BASE_URL="${BASE_URL%/}"
WALL_MINUTES="${ISPF_SOAK_WALL_MINUTES:-30}"
POLL_SEC="${ISPF_SOAK_POLL_SEC:-10}"
SIM_DAYS="${ISPF_SOAK_SIM_DAYS:-30}"
TOKEN="${ISPF_SOAK_TOKEN:-}"
USER="${ISPF_SOAK_USER:-admin}"
PASS="${ISPF_SOAK_PASSWORD:-admin}"
DATA_DIR="${ISPF_DATA_DIR:-}"
AGENT_ID="${ISPF_SOAK_OUTBOUND_AGENT_ID:-}"

mkdir -p "$OUT_DIR"
CSV="$OUT_DIR/soak-samples.csv"
REPORT="$OUT_DIR/soak-report.md"
STARTED_AT="$(date -u +%Y-%m-%dT%H:%M:%SZ)"

if [[ -z "$TOKEN" ]]; then
  echo "==> Login ${USER}@${BASE_URL}"
  TOKEN=$(curl -fsS -X POST "$BASE_URL/api/v1/auth/login" \
    -H "Content-Type: application/json" \
    -d "{\"username\":\"$USER\",\"password\":\"$PASS\"}" \
    | python3 -c "import json,sys; print(json.load(sys.stdin)['token'])")
fi

auth_hdr=(-H "Authorization: Bearer $TOKEN")

fetch_stats() {
  curl -fsS "${auth_hdr[@]}" "$BASE_URL/api/v1/agent/store-forward/stats"
}

echo "ts_utc,http,enabled,persistToDisk,totalPending,totalBytes,totalDropped" >"$CSV"

deadline_epoch=$(( $(date +%s) + WALL_MINUTES * 60 ))
sample=0
max_pending=0
max_dropped=0
last_json=""
fail=0

echo "==> BL-145 soak start wallMinutes=$WALL_MINUTES pollSec=$POLL_SEC simDays=$SIM_DAYS base=$BASE_URL"

while true; do
  sample=$((sample + 1))
  ts="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  http=000
  body=""
  if body=$(curl -sS -w '\n%{http_code}' "${auth_hdr[@]}" "$BASE_URL/api/v1/agent/store-forward/stats"); then
    http=$(printf '%s' "$body" | tail -n1)
    last_json=$(printf '%s' "$body" | sed '$d')
  else
    http=000
    last_json="{}"
    fail=1
  fi

  read -r enabled persist pending bytes dropped <<<"$(
    printf '%s' "$last_json" | python3 -c '
import json,sys
try:
    d=json.load(sys.stdin)
except Exception:
    d={}
print(
    str(d.get("enabled", "")).lower(),
    str(d.get("persistToDisk", "")).lower(),
    int(d.get("totalPending") or 0),
    int(d.get("totalBytes") or 0),
    int(d.get("totalDropped") or 0),
)
'
  )"

  echo "$ts,$http,$enabled,$persist,$pending,$bytes,$dropped" >>"$CSV"
  if [[ "$pending" =~ ^[0-9]+$ ]] && (( pending > max_pending )); then max_pending=$pending; fi
  if [[ "$dropped" =~ ^[0-9]+$ ]] && (( dropped > max_dropped )); then max_dropped=$dropped; fi

  echo "  sample#$sample http=$http pending=$pending dropped=$dropped"

  if (( WALL_MINUTES == 0 )); then
    break
  fi
  now=$(date +%s)
  if (( now >= deadline_epoch )); then
    break
  fi
  sleep "$POLL_SEC"
done

ENDED_AT="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
persist_file_note="not checked"
if [[ -n "$DATA_DIR" ]]; then
  PF="$DATA_DIR/agent/store-forward-buffer.json"
  if [[ -f "$PF" ]]; then
    persist_file_note="present ($PF)"
  else
    persist_file_note="missing ($PF) — ok if buffer empty / persist disabled"
  fi
fi

gate="PASS"
if (( fail != 0 )); then
  gate="FAIL (stats HTTP errors)"
elif (( max_dropped > 0 )); then
  gate="WARN (totalDropped observed > 0 — review DROP policy / capacity)"
fi

{
  echo "# Agent store-forward soak report (BL-145)"
  echo
  echo "| Field | Value |"
  echo "|-------|-------|"
  echo "| Started (UTC) | $STARTED_AT |"
  echo "| Ended (UTC) | $ENDED_AT |"
  echo "| Base URL | \`$BASE_URL\` |"
  echo "| Wall minutes | $WALL_MINUTES |"
  echo "| Sim days (label) | $SIM_DAYS |"
  echo "| Poll sec | $POLL_SEC |"
  echo "| Samples | $sample |"
  echo "| Max totalPending | $max_pending |"
  echo "| Max totalDropped | $max_dropped |"
  echo "| Persist file | $persist_file_note |"
  echo "| Outbound agent id | \`${AGENT_ID:-—}\` |"
  echo "| Gate | **$gate** |"
  echo
  echo "## Claim boundary"
  echo
  echo "- **Lab Done:** stats API healthy + journal artifacts; disconnect/replay proven by \`FederationStoreForwardIntegrationTest\` + disk unit test."
  echo "- **Field GA:** same script with \`ISPF_SOAK_WALL_MINUTES=43200\` on a named edge node; zero loss after reconnect."
  echo
  echo "## Last stats JSON"
  echo
  echo '```json'
  printf '%s\n' "$last_json" | python3 -m json.tool 2>/dev/null || printf '%s\n' "$last_json"
  echo '```'
  echo
  echo "CSV: \`$CSV\`"
} >"$REPORT"

echo "==> Wrote $REPORT"
echo "gate=$gate"
if [[ "$gate" == FAIL* ]]; then
  exit 1
fi
