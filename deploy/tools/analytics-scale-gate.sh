#!/usr/bin/env bash
# BL-210: analytics platform scale gate — multi-tag query SLO, catalog size, optional CH row count.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT"

REPORT_DIR="${ISPF_ANALYTICS_BENCH_DIR:-$ROOT/build/analytics-scale}"
mkdir -p "$REPORT_DIR"
REPORT_FILE="$REPORT_DIR/analytics-scale-gate.md"

BASE_URL="${ISPF_BENCH_BASE_URL:-http://localhost:8080}"
SLA_ITERATIONS="${ISPF_ANALYTICS_BENCH_ITERATIONS:-10}"
MULTI_TAG_P95_MS="${ISPF_ANALYTICS_BENCH_MULTI_TAG_P95_MS:-3000}"
CATALOG_MIN_TAGS="${ISPF_ANALYTICS_BENCH_CATALOG_MIN_TAGS:-50000}"
CH_URL="${ISPF_VARIABLE_HISTORY_CLICKHOUSE_URL:-}"
CH_USER="${ISPF_VARIABLE_HISTORY_CLICKHOUSE_USERNAME:-}"
CH_PASS="${ISPF_VARIABLE_HISTORY_CLICKHOUSE_PASSWORD:-}"
CH_DATABASE="${ISPF_VARIABLE_HISTORY_CLICKHOUSE_DATABASE:-ispf}"
CH_TABLE="${ISPF_VARIABLE_HISTORY_CLICKHOUSE_TABLE:-variable_samples}"
CH_MIN_SAMPLES="${ISPF_ANALYTICS_BENCH_CH_MIN_SAMPLES:-1000000000}"
SKIP_CATALOG_GATE="${ISPF_ANALYTICS_BENCH_SKIP_CATALOG_GATE:-true}"
SKIP_CH_GATE="${ISPF_ANALYTICS_BENCH_SKIP_CH_GATE:-true}"
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

echo "# Analytics scale gate (BL-210)" > "$REPORT_FILE"
echo "" >> "$REPORT_FILE"
echo "- Date: $(date -u +"%Y-%m-%dT%H:%M:%SZ")" >> "$REPORT_FILE"
echo "- Base URL: $BASE_URL" >> "$REPORT_FILE"
echo "- Multi-tag p95 ceiling: ${MULTI_TAG_P95_MS}ms" >> "$REPORT_FILE"
echo "" >> "$REPORT_FILE"

echo "==> Server health"
if ! curl -sf "${BASE_URL}/api/v1/info" >/dev/null; then
  echo "- Server unreachable at $BASE_URL" >> "$REPORT_FILE"
  echo "Start ISPF or set ISPF_BENCH_BASE_URL." >&2
  exit 1
fi
VERSION="$(curl -sf "${BASE_URL}/api/v1/info" | sed -n 's/.*"version"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p')"
echo "- Server version: ${VERSION:-unknown}" >> "$REPORT_FILE"

echo "==> Analytics SLO targets"
if SLO_BODY="$(curl -sf -H "$(bench_auth_header)" "${BASE_URL}/api/v1/platform/analytics/analytics-slo")"; then
  echo "- Analytics SLO endpoint: **OK**" >> "$REPORT_FILE"
  echo '```json' >> "$REPORT_FILE"
  echo "$SLO_BODY" >> "$REPORT_FILE"
  echo '```' >> "$REPORT_FILE"
else
  echo "- Analytics SLO endpoint: **FAILED**" >> "$REPORT_FILE"
  GATE_EXIT=1
fi

echo "==> Catalog tag count"
TAG_PREFIX="${ISPF_ANALYTICS_BENCH_TAG_PREFIX:-root.platform.devices.analytics-scale-lab}"
CATALOG_MODE="${ISPF_ANALYTICS_BENCH_CATALOG_MODE:-history-enabled}"
TAG_COUNT=""
ANALYTICS_RULE_COUNT=""

if [[ "$CATALOG_MODE" == "history-enabled" ]]; then
  PG_COMPOSE="${ISPF_ANALYTICS_BENCH_CATALOG_PG_COMPOSE:-}"
  if [[ -z "$PG_COMPOSE" && -f "$ROOT/lab-cluster-compose.yml" ]]; then
    PG_COMPOSE="$ROOT/lab-cluster-compose.yml"
  fi
  if [[ -n "$PG_COMPOSE" && -f "$PG_COMPOSE" ]]; then
    TAG_COUNT="$(docker compose -f "$PG_COMPOSE" exec -T postgres psql -U ispf -d ispf -tAc \
      "SELECT COUNT(*) FROM object_nodes WHERE path LIKE '${TAG_PREFIX}.tag-%';" \
      2>/dev/null | tr -d '[:space:]' || true)"
    if [[ -z "${TAG_COUNT:-}" || "${TAG_COUNT:-0}" -eq 0 ]]; then
      TAG_COUNT="$(docker compose -f "$PG_COMPOSE" exec -T postgres psql -U ispf -d ispf -tAc \
        "SELECT COUNT(*) FROM object_variables WHERE object_path LIKE '${TAG_PREFIX}.%' AND history_enabled = true;" \
        2>/dev/null | tr -d '[:space:]' || true)"
    fi
  fi
  if CATALOG_BODY="$(curl -sf -H "$(bench_auth_header)" "${BASE_URL}/api/v1/platform/analytics/tags?path=${TAG_PREFIX}")"; then
    ANALYTICS_RULE_COUNT="$(printf '%s' "$CATALOG_BODY" | sed -n 's/.*"count"[[:space:]]*:[[:space:]]*\([0-9][0-9]*\).*/\1/p' | head -1)"
  fi
  echo "- History-enabled points (${TAG_PREFIX}): **${TAG_COUNT:-0}**" >> "$REPORT_FILE"
  echo "- Analytics historian rules (${TAG_PREFIX}): **${ANALYTICS_RULE_COUNT:-0}**" >> "$REPORT_FILE"
else
  if CATALOG_BODY="$(curl -sf -H "$(bench_auth_header)" "${BASE_URL}/api/v1/platform/analytics/tags?path=${TAG_PREFIX}")"; then
    TAG_COUNT="$(printf '%s' "$CATALOG_BODY" | sed -n 's/.*"count"[[:space:]]*:[[:space:]]*\([0-9][0-9]*\).*/\1/p' | head -1)"
    echo "- Analytics catalog (${TAG_PREFIX}): **${TAG_COUNT:-0}** historian rules" >> "$REPORT_FILE"
  else
    echo "- Analytics catalog: **FAILED**" >> "$REPORT_FILE"
    GATE_EXIT=1
  fi
fi

if [[ "$SKIP_CATALOG_GATE" != "true" ]]; then
  if [[ "${TAG_COUNT:-0}" -lt "$CATALOG_MIN_TAGS" ]]; then
    echo "- Catalog gate: **FAIL** (${TAG_COUNT:-0} < ${CATALOG_MIN_TAGS})" >> "$REPORT_FILE"
    GATE_EXIT=1
  else
    echo "- Catalog gate: **PASS** (≥ ${CATALOG_MIN_TAGS})" >> "$REPORT_FILE"
  fi
else
  echo "- Catalog gate: **SKIPPED** (set ISPF_ANALYTICS_BENCH_SKIP_CATALOG_GATE=false after seed)" >> "$REPORT_FILE"
fi

TAG_PREFIX="${ISPF_ANALYTICS_BENCH_TAG_PREFIX:-root.platform.devices.analytics-scale-lab}"
TAG_VARIABLE="${ISPF_ANALYTICS_BENCH_TAG_VARIABLE:-temperature}"
TAG_COUNT="${ISPF_ANALYTICS_BENCH_TAG_COUNT:-10}"

echo "==> Multi-tag query SLO (${TAG_COUNT} tags × 7d × 1h)"
# UTC day boundaries: lab nginx returns 403 when "to" embeds live clock time (HH:MM:SS now).
FROM_ISO="$(date -u -d '7 days ago' +"%Y-%m-%dT00:00:00Z" 2>/dev/null || date -u -v-7d +"%Y-%m-%dT00:00:00Z")"
TO_ISO="$(date -u +"%Y-%m-%dT00:00:00Z")"

QUERY_BODY="$(python3 - <<PY
import json
prefix = "${TAG_PREFIX}"
variable = "${TAG_VARIABLE}"
count = int("${TAG_COUNT}")
pad = max(5, len(str(count)))
tags = []
for index in range(1, count + 1):
    name = f"tag-{index:0{pad}d}"
    tags.append({
        "path": f"{prefix}.{name}",
        "variable": variable,
        "field": "value",
        "label": name,
    })
print(json.dumps({
    "tags": tags,
    "from": "${FROM_ISO}",
    "to": "${TO_ISO}",
    "bucket": "1h",
    "agg": "avg",
    "maxBuckets": 168,
}))
PY
)"

LATENCIES_FILE="$(mktemp)"
: > "$LATENCIES_FILE"
for _ in $(seq 1 "$SLA_ITERATIONS"); do
  START_NS="$(date +%s%N 2>/dev/null || python -c 'import time; print(int(time.time()*1e9))')"
  if curl -sf -X POST "${BASE_URL}/api/v1/platform/analytics/query" \
      -H "$(bench_auth_header)" \
      -H "Content-Type: application/json" \
      -d "$QUERY_BODY" >/dev/null; then
    END_NS="$(date +%s%N 2>/dev/null || python -c 'import time; print(int(time.time()*1e9))')"
    ELAPSED_MS=$(( (END_NS - START_NS) / 1000000 ))
    echo "$ELAPSED_MS" >> "$LATENCIES_FILE"
  else
    echo "- Multi-tag query gate: **FAILED** (HTTP error)" >> "$REPORT_FILE"
    GATE_EXIT=1
    break
  fi
done

if [[ -s "$LATENCIES_FILE" ]]; then
  SORTED="$(sort -n "$LATENCIES_FILE")"
  BENCH_N="$(wc -l < "$LATENCIES_FILE" | tr -d ' ')"
  BENCH_P50="$(echo "$SORTED" | awk -v n="$BENCH_N" 'NR==int((n+1)/2){print; exit}')"
  BENCH_P95="$(echo "$SORTED" | awk -v n="$BENCH_N" 'NR==int(n*0.95){print; exit}')"
  BENCH_MAX="$(echo "$SORTED" | tail -1)"
  echo "- Multi-tag query (${BENCH_N} runs): p50=${BENCH_P50:-n/a}ms p95=${BENCH_P95:-n/a}ms max=${BENCH_MAX:-n/a}ms" >> "$REPORT_FILE"
  if [[ "${BENCH_P95:-0}" -gt "$MULTI_TAG_P95_MS" ]]; then
    echo "- Multi-tag query gate: **FAIL** (p95 ${BENCH_P95}ms > ${MULTI_TAG_P95_MS}ms)" >> "$REPORT_FILE"
    GATE_EXIT=1
  else
    echo "- Multi-tag query gate: **PASS**" >> "$REPORT_FILE"
  fi
fi
rm -f "$LATENCIES_FILE"

echo "==> ClickHouse sample count (optional)"
if [[ "$SKIP_CH_GATE" == "true" || -z "$CH_URL" ]]; then
  echo "- ClickHouse 1B gate: **SKIPPED** (set ISPF_VARIABLE_HISTORY_CLICKHOUSE_URL + ISPF_ANALYTICS_BENCH_SKIP_CH_GATE=false)" >> "$REPORT_FILE"
else
  COUNT_QUERY="SELECT count() FROM ${CH_DATABASE}.${CH_TABLE} FORMAT TabSeparated"
  COUNT_URL="${CH_URL}/?query=$(python3 -c "import urllib.parse; print(urllib.parse.quote('''${COUNT_QUERY}'''))")"
  if [[ -n "$CH_PASS" ]]; then
    CH_COUNT="$(curl -sf -u "${CH_USER:-default}:${CH_PASS}" "$COUNT_URL" 2>/dev/null | tr -d '[:space:]')"
  else
    CH_COUNT="$(curl -sf "$COUNT_URL" 2>/dev/null | tr -d '[:space:]')"
  fi
  if [[ -n "${CH_COUNT:-}" ]]; then
    echo "- ClickHouse rows (${CH_DATABASE}.${CH_TABLE}): **${CH_COUNT}**" >> "$REPORT_FILE"
    if [[ "${CH_COUNT:-0}" -lt "$CH_MIN_SAMPLES" ]]; then
      echo "- ClickHouse 1B gate: **FAIL** (< ${CH_MIN_SAMPLES})" >> "$REPORT_FILE"
      GATE_EXIT=1
    else
      echo "- ClickHouse 1B gate: **PASS**" >> "$REPORT_FILE"
    fi
  else
    echo "- ClickHouse count query: **FAILED**" >> "$REPORT_FILE"
    GATE_EXIT=1
  fi
fi

echo "" >> "$REPORT_FILE"
echo "See [examples/analytics-platform/enterprise-l/README.md](../../examples/analytics-platform/enterprise-l/README.md) for full Scenario C walkthrough." >> "$REPORT_FILE"
echo "" >> "$REPORT_FILE"
echo "Report written to $REPORT_FILE"
cat "$REPORT_FILE"
exit "$GATE_EXIT"
