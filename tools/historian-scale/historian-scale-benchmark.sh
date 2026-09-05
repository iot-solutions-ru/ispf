#!/usr/bin/env bash
# BL-161 — historian aggregate query SLA gate (tracked).
# Seeds ≤1M points in JVM test profile and asserts p95 < 2s (override via env).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"

OUT_DIR="${ISPF_HISTORIAN_SCALE_OUT:-$ROOT/build/historian-scale}"
mkdir -p "$OUT_DIR"
LOG="$OUT_DIR/historian-aggregate-load.log"
REPORT="$OUT_DIR/scale-benchmark.md"

SAMPLE_COUNT="${ISPF_HISTORIAN_AGGREGATE_SAMPLE_COUNT:-1000000}"
P95_CEILING_MS="${ISPF_HISTORIAN_AGGREGATE_P95_CEILING_MS:-2000}"

export ISPF_HISTORIAN_AGGREGATE_SAMPLE_COUNT="$SAMPLE_COUNT"
export ISPF_HISTORIAN_AGGREGATE_P95_CEILING_MS="$P95_CEILING_MS"

echo "==> BL-161 historian aggregate JVM gate (samples=${SAMPLE_COUNT}, p95<=${P95_CEILING_MS}ms)"
set -o pipefail
./gradlew \
  :packages:ispf-server:test \
  --tests com.ispf.server.history.HistorianAggregateQueryLoadTest \
  --no-daemon \
  -Dorg.gradle.workers.max=1 \
  2>&1 | tee "$LOG"

STATUS="PASS"
if ! grep -q "historian_aggregate_query" "$LOG"; then
  STATUS="FAIL"
fi
if grep -Eqi "FAILED|BUILD FAILED" "$LOG"; then
  STATUS="FAIL"
fi

{
  echo "# Historian scale benchmark (BL-161)"
  echo
  echo "| Field | Value |"
  echo "|-------|-------|"
  echo "| Status | **${STATUS}** |"
  echo "| Samples | ${SAMPLE_COUNT} |"
  echo "| p95 ceiling | ${P95_CEILING_MS} ms |"
  echo "| Log | \`${LOG#$ROOT/}\` |"
  echo "| When | $(date -u +%Y-%m-%dT%H:%M:%SZ) |"
  echo
  echo '```'
  grep -E "historian_aggregate_query|BUILD" "$LOG" | tail -n 20 || true
  echo '```'
} > "$REPORT"

echo "Report: $REPORT"
[[ "$STATUS" == "PASS" ]]
