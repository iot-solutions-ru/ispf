#!/usr/bin/env bash
# BL-210: materializer lag gate — rollup head vs historian SLO.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT"

REPORT_DIR="${ISPF_ANALYTICS_BENCH_DIR:-$ROOT/build/analytics-scale}"
mkdir -p "$REPORT_DIR"
REPORT_FILE="$REPORT_DIR/analytics-materializer-lag-gate.md"

BASE_URL="${ISPF_BENCH_BASE_URL:-http://localhost:8080}"
MATERIALIZER_BASE_URL="${ISPF_BENCH_MATERIALIZER_BASE_URL:-$BASE_URL}"
MATERIALIZER_DOCKER_NETWORK="${ISPF_BENCH_MATERIALIZER_DOCKER_NETWORK:-}"
MAX_LAG_SEC="${ISPF_ANALYTICS_BENCH_MATERIALIZER_MAX_LAG_SEC:-300}"
SKIP_GATE="${ISPF_ANALYTICS_BENCH_SKIP_MATERIALIZER_GATE:-true}"
BENCH_TOKEN="${ISPF_BENCH_TOKEN:-}"
BENCH_USER="${ISPF_BENCH_USER:-admin}"
BENCH_PASSWORD="${ISPF_BENCH_PASSWORD:-admin}"

GATE_EXIT=0

bench_auth_header() {
  if [[ -z "$BENCH_TOKEN" ]]; then
    BENCH_TOKEN="$(curl -sf "${BASE_URL}/api/v1/auth/login" \
      -H "Content-Type: application/json" \
      -d "{\"username\":\"${BENCH_USER}\",\"password\":\"${BENCH_PASSWORD}\"}" \
      | sed -n 's/.*"token"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p')"
  fi
  if [[ -z "$BENCH_TOKEN" ]]; then
    echo "Failed to obtain auth token (set ISPF_BENCH_TOKEN or ISPF_BENCH_USER/PASSWORD)" >&2
    return 1
  fi
  printf 'Authorization: Bearer %s' "$BENCH_TOKEN"
}

echo "# Analytics materializer lag gate (BL-210)" > "$REPORT_FILE"
echo "" >> "$REPORT_FILE"
echo "- Date: $(date -u +"%Y-%m-%dT%H:%M:%SZ")" >> "$REPORT_FILE"
echo "- Base URL: $BASE_URL" >> "$REPORT_FILE"
echo "- Max lag ceiling: ${MAX_LAG_SEC}s" >> "$REPORT_FILE"
echo "" >> "$REPORT_FILE"

if ! curl -sf "${BASE_URL}/api/v1/info" >/dev/null; then
  echo "- Server unreachable at $BASE_URL" >> "$REPORT_FILE"
  echo "Start ISPF or set ISPF_BENCH_BASE_URL." >&2
  exit 1
fi

if [[ "$SKIP_GATE" == "true" ]]; then
  echo "- Materializer lag gate: **SKIPPED** (set ISPF_ANALYTICS_BENCH_SKIP_MATERIALIZER_GATE=false after enabling materializer)" >> "$REPORT_FILE"
  cat "$REPORT_FILE"
  exit 0
fi

fetch_materializer_status() {
  local auth="$1"
  local body=""
  if body="$(curl -sf -H "$auth" "${BASE_URL}/api/v1/platform/analytics/rollups/materializer/status" 2>/dev/null)"; then
    if printf '%s' "$body" | grep -q '"enabled"[[:space:]]*:[[:space:]]*true'; then
      printf '%s' "$body"
      return 0
    fi
  fi
  if [[ -n "$MATERIALIZER_DOCKER_NETWORK" && "$MATERIALIZER_BASE_URL" != "$BASE_URL" ]]; then
    if body="$(docker run --rm --network "$MATERIALIZER_DOCKER_NETWORK" curlimages/curl:8.5.0 -sf \
      -H "$auth" "${MATERIALIZER_BASE_URL}/api/v1/platform/analytics/rollups/materializer/status" 2>/dev/null)"; then
      printf '%s' "$body"
      return 0
    fi
  fi
  if [[ "$MATERIALIZER_BASE_URL" != "$BASE_URL" ]]; then
    if body="$(curl -sf -H "$auth" "${MATERIALIZER_BASE_URL}/api/v1/platform/analytics/rollups/materializer/status" 2>/dev/null)"; then
      printf '%s' "$body"
      return 0
    fi
  fi
  if [[ -n "${body:-}" ]]; then
    printf '%s' "$body"
    return 0
  fi
  return 1
}

AUTH_HDR="$(bench_auth_header)"
STATUS_BODY=""
ENABLED=""
for attempt in $(seq 1 20); do
  if STATUS_BODY="$(fetch_materializer_status "$AUTH_HDR" 2>/dev/null)"; then
    ENABLED="$(printf '%s' "$STATUS_BODY" | sed -n 's/.*"enabled"[[:space:]]*:[[:space:]]*\(true\|false\).*/\1/p' | head -1)"
    if [[ "$ENABLED" == "true" ]]; then
      break
    fi
  fi
  sleep 2
done

if [[ -z "$STATUS_BODY" ]]; then
  echo "- Materializer status endpoint: **FAILED** (tried ${BASE_URL}/api/v1/platform/analytics/rollups/materializer/status)" >> "$REPORT_FILE"
  cat "$REPORT_FILE"
  exit 1
fi

echo '```json' >> "$REPORT_FILE"
echo "$STATUS_BODY" >> "$REPORT_FILE"
echo '```' >> "$REPORT_FILE"

MAX_LAG_MS="$(printf '%s' "$STATUS_BODY" | sed -n 's/.*"maxLagMs"[[:space:]]*:[[:space:]]*\([0-9][0-9]*\).*/\1/p' | head -1)"
SLO_SEC="$(printf '%s' "$STATUS_BODY" | sed -n 's/.*"maxLagSecondsSlo"[[:space:]]*:[[:space:]]*\([0-9][0-9]*\).*/\1/p' | head -1)"

if [[ "$ENABLED" != "true" ]]; then
  echo "- Materializer lag gate: **FAIL** (materializer not enabled on this replica)" >> "$REPORT_FILE"
  GATE_EXIT=1
else
  CEILING_MS=$((MAX_LAG_SEC * 1000))
  if [[ -n "$SLO_SEC" ]]; then
    CEILING_MS=$((SLO_SEC * 1000))
  fi
  if [[ "${MAX_LAG_MS:-0}" -gt "$CEILING_MS" ]]; then
    echo "- Materializer lag gate: **FAIL** (maxLagMs=${MAX_LAG_MS:-0} > ${CEILING_MS}ms)" >> "$REPORT_FILE"
    GATE_EXIT=1
  else
    echo "- Materializer lag gate: **PASS** (maxLagMs=${MAX_LAG_MS:-0})" >> "$REPORT_FILE"
  fi
fi

echo "" >> "$REPORT_FILE"
echo "Report written to $REPORT_FILE"
cat "$REPORT_FILE"
exit "$GATE_EXIT"
