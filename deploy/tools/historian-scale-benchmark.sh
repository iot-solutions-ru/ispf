#!/usr/bin/env bash
# BL-162: historian petabyte-path scale benchmark with local REST smoke + 1M aggregate SLA gate.
# References docs/en/clickhouse-prod-playbook.md for prod cutover steps.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT"

PLAYBOOK="${ROOT}/docs/en/clickhouse-prod-playbook.md"
REPORT_DIR="${ISPF_HISTORIAN_BENCH_DIR:-$ROOT/build/historian-scale}"
mkdir -p "$REPORT_DIR"
REPORT_FILE="$REPORT_DIR/scale-benchmark.md"

BASE_URL="${ISPF_BENCH_BASE_URL:-http://localhost:8080}"
DURATION_SEC="${ISPF_HISTORIAN_BENCH_DURATION_SEC:-60}"
TARGET_EVENTS_PER_MIN="${ISPF_HISTORIAN_BENCH_TARGET_EVENTS_PER_MIN:-10000000}"
HIST_PATH="${ISPF_HISTORIAN_BENCH_PATH:-root.platform.devices.demo-sensor-01}"
HIST_VAR="${ISPF_HISTORIAN_BENCH_VARIABLE:-temperature}"
FROM_ISO="${ISPF_HISTORIAN_BENCH_FROM:-}"
TO_ISO="${ISPF_HISTORIAN_BENCH_TO:-}"
AGGREGATE_MAX_POINTS="${ISPF_HISTORIAN_BENCH_AGGREGATE_MAX_POINTS:-1000000}"
AGGREGATE_MAX_LATENCY_MS="${ISPF_HISTORIAN_BENCH_AGGREGATE_MAX_LATENCY_MS:-2000}"
SLA_ITERATIONS="${ISPF_HISTORIAN_BENCH_SLA_ITERATIONS:-5}"
SKIP_SLA_GATE="${ISPF_HISTORIAN_BENCH_SKIP_SLA_GATE:-false}"

GATE_EXIT=0

echo "# Historian scale benchmark (BL-162)" > "$REPORT_FILE"
echo "" >> "$REPORT_FILE"
echo "- Date: $(date -u +"%Y-%m-%dT%H:%M:%SZ")" >> "$REPORT_FILE"
echo "- Base URL: $BASE_URL" >> "$REPORT_FILE"
echo "- Target: ${TARGET_EVENTS_PER_MIN} events/min lab path" >> "$REPORT_FILE"
echo "- Playbook: [CLICKHOUSE_PROD_PLAYBOOK.md](${PLAYBOOK#${ROOT}/})" >> "$REPORT_FILE"
echo "" >> "$REPORT_FILE"

echo "==> Checking server health"
if curl -sf "${BASE_URL}/api/v1/info" >/dev/null; then
  VERSION="$(curl -sf "${BASE_URL}/api/v1/info" | sed -n 's/.*"version"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p')"
  echo "- Server version: ${VERSION:-unknown}" >> "$REPORT_FILE"
else
  echo "- Server unreachable at $BASE_URL" >> "$REPORT_FILE"
  echo "Start ISPF server or set ISPF_BENCH_BASE_URL." >&2
  exit 1
fi

echo "==> REST historian smoke (path=$HIST_PATH var=$HIST_VAR)"
RANGE_ARGS=""
if [[ -n "$FROM_ISO" ]]; then
  RANGE_ARGS+="&from=${FROM_ISO}"
fi
if [[ -n "$TO_ISO" ]]; then
  RANGE_ARGS+="&to=${TO_ISO}"
fi

HIST_QUERY="${BASE_URL}/api/v1/objects/by-path/variables/history?path=${HIST_PATH}&name=${HIST_VAR}&field=value&limit=5${RANGE_ARGS}"
if HIST_BODY="$(curl -sf "$HIST_QUERY")"; then
  SAMPLE_COUNT="$(printf '%s' "$HIST_BODY" | sed -n 's/.*"samples"[[:space:]]*:[[:space:]]*\[\([^]]*\)\].*/\1/p' | awk -F',' '{print NF}')"
  echo "- History query: **OK** (${SAMPLE_COUNT:-0} samples in response head)" >> "$REPORT_FILE"
else
  echo "- History query: **FAILED** ($HIST_QUERY)" >> "$REPORT_FILE"
  exit 1
fi

AGG_QUERY="${BASE_URL}/api/v1/objects/by-path/variables/history/aggregate?path=${HIST_PATH}&name=${HIST_VAR}&field=value&bucket=1h&limit=10${RANGE_ARGS}"
if curl -sf "$AGG_QUERY" >/dev/null; then
  echo "- History aggregate (1h bucket): **OK**" >> "$REPORT_FILE"
else
  echo "- History aggregate: **FAILED**" >> "$REPORT_FILE"
  exit 1
fi

CSV_TMP="$(mktemp)"
CSV_QUERY="${BASE_URL}/api/v1/objects/by-path/variables/history/export?path=${HIST_PATH}&name=${HIST_VAR}&field=value&format=csv&limit=100${RANGE_ARGS}"
if curl -sf "$CSV_QUERY" -o "$CSV_TMP"; then
  CSV_LINES="$(wc -l < "$CSV_TMP" | tr -d ' ')"
  echo "- CSV export stream: **OK** (${CSV_LINES} lines)" >> "$REPORT_FILE"
else
  echo "- CSV export stream: **FAILED**" >> "$REPORT_FILE"
  rm -f "$CSV_TMP"
  exit 1
fi
rm -f "$CSV_TMP"

if curl -sf "${BASE_URL}/api/v1/platform/analytics/templates" >/dev/null; then
  TEMPLATE_COUNT="$(curl -sf "${BASE_URL}/api/v1/platform/analytics/templates" | grep -o '"templateId"' | wc -l | tr -d ' ')"
  echo "- Analytics templates: **OK** (${TEMPLATE_COUNT:-0} templates)" >> "$REPORT_FILE"
else
  echo "- Analytics templates: **FAILED**" >> "$REPORT_FILE"
  exit 1
fi

echo "==> Placeholder ingest benchmark (${DURATION_SEC}s)"
echo "- Ingest benchmark: **not implemented** — wire event fire + historian dual-write per playbook." >> "$REPORT_FILE"
echo "- Next steps:" >> "$REPORT_FILE"
echo "  1. Enable ClickHouse per ${PLAYBOOK#${ROOT}/}" >> "$REPORT_FILE"
echo "  2. Run dual-write validation (\`ISPF_VARIABLE_HISTORY_STORE=jdbc\` + CH secondary)" >> "$REPORT_FILE"
echo "  3. Replay lab load script against \`POST /api/v1/events/fire\`" >> "$REPORT_FILE"
echo "  4. Query aggregate SLA (\`GET .../variables/history/aggregate\`) — see BL-161 SLO" >> "$REPORT_FILE"

echo "==> 1M aggregate SLA gate (BL-161 / BL-162)"
if [[ "$SKIP_SLA_GATE" == "true" ]]; then
  echo "- 1M aggregate SLA gate: **SKIPPED** (ISPF_HISTORIAN_BENCH_SKIP_SLA_GATE=true)" >> "$REPORT_FILE"
else
  SLA_URL="${BASE_URL}/api/v1/platform/analytics/historian-sla"
  if SLA_BODY="$(curl -sf "$SLA_URL")"; then
    echo "- Historian SLA endpoint: **OK**" >> "$REPORT_FILE"
    P50="$(printf '%s' "$SLA_BODY" | sed -n 's/.*"aggregate"[^{]*{[^}]*"p50LatencyMs"[[:space:]]*:[[:space:]]*\([0-9][0-9]*\).*/\1/p' | head -1)"
    P95="$(printf '%s' "$SLA_BODY" | sed -n 's/.*"aggregate"[^{]*{[^}]*"p95LatencyMs"[[:space:]]*:[[:space:]]*\([0-9][0-9]*\).*/\1/p' | head -1)"
    echo "- Platform aggregate p50/p95 (window): ${P50:-n/a}ms / ${P95:-n/a}ms" >> "$REPORT_FILE"
  else
    echo "- Historian SLA endpoint: **FAILED** ($SLA_URL)" >> "$REPORT_FILE"
    GATE_EXIT=1
  fi

  WIDE_AGG_QUERY="${BASE_URL}/api/v1/objects/by-path/variables/history/aggregate?path=${HIST_PATH}&name=${HIST_VAR}&field=value&bucket=1h&limit=2000${RANGE_ARGS}"
  LATENCIES_FILE="$(mktemp)"
  : > "$LATENCIES_FILE"
  FAIL_COUNT=0
  for _ in $(seq 1 "$SLA_ITERATIONS"); do
    START_NS="$(date +%s%N)"
    if curl -sf "$WIDE_AGG_QUERY" >/dev/null; then
      END_NS="$(date +%s%N)"
      ELAPSED_MS=$(( (END_NS - START_NS) / 1000000 ))
      echo "$ELAPSED_MS" >> "$LATENCIES_FILE"
      if [[ "$ELAPSED_MS" -gt "$AGGREGATE_MAX_LATENCY_MS" ]]; then
        FAIL_COUNT=$((FAIL_COUNT + 1))
      fi
    else
      echo "- 1M aggregate SLA gate: **FAILED** (aggregate query error)" >> "$REPORT_FILE"
      GATE_EXIT=1
      FAIL_COUNT="$SLA_ITERATIONS"
      break
    fi
  done

  if [[ "$GATE_EXIT" -eq 0 ]]; then
    SORTED="$(sort -n "$LATENCIES_FILE")"
    BENCH_N="$(wc -l < "$LATENCIES_FILE" | tr -d ' ')"
    BENCH_P50="$(echo "$SORTED" | awk -v n="$BENCH_N" 'NR==int((n+1)/2){print; exit}')"
    BENCH_P95="$(echo "$SORTED" | awk -v n="$BENCH_N" 'NR==int(n*0.95){print; exit}')"
    BENCH_MAX="$(echo "$SORTED" | tail -1)"
    echo "- Bench aggregate (${BENCH_N} runs, ≤${AGGREGATE_MAX_POINTS} pts SLO): p50=${BENCH_P50:-n/a}ms p95=${BENCH_P95:-n/a}ms max=${BENCH_MAX:-n/a}ms (ceiling ${AGGREGATE_MAX_LATENCY_MS}ms)" >> "$REPORT_FILE"
    if [[ "${BENCH_P95:-0}" -gt "$AGGREGATE_MAX_LATENCY_MS" ]]; then
      echo "- 1M aggregate SLA gate: **FAIL** (p95 ${BENCH_P95}ms > ${AGGREGATE_MAX_LATENCY_MS}ms)" >> "$REPORT_FILE"
      GATE_EXIT=1
    else
      echo "- 1M aggregate SLA gate: **PASS**" >> "$REPORT_FILE"
    fi
  fi
  rm -f "$LATENCIES_FILE"
fi

echo "" >> "$REPORT_FILE"
echo "See also: [deploy/tools/analytics-scale-gate.sh](analytics-scale-gate.sh) (BL-210 multi-tag SLO)." >> "$REPORT_FILE"
echo "" >> "$REPORT_FILE"
echo "Report written to $REPORT_FILE"
cat "$REPORT_FILE"

exit "$GATE_EXIT"
