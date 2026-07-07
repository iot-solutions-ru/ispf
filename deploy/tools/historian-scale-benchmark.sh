#!/usr/bin/env bash
# BL-162: historian petabyte-path scale benchmark skeleton.
# References docs/CLICKHOUSE_PROD_PLAYBOOK.md for prod cutover steps.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT"

PLAYBOOK="${ROOT}/docs/CLICKHOUSE_PROD_PLAYBOOK.md"
REPORT_DIR="${ISPF_HISTORIAN_BENCH_DIR:-$ROOT/build/historian-scale}"
mkdir -p "$REPORT_DIR"
REPORT_FILE="$REPORT_DIR/scale-benchmark.md"

BASE_URL="${ISPF_BENCH_BASE_URL:-http://localhost:8080}"
DURATION_SEC="${ISPF_HISTORIAN_BENCH_DURATION_SEC:-60}"
TARGET_EVENTS_PER_MIN="${ISPF_HISTORIAN_BENCH_TARGET_EVENTS_PER_MIN:-10000000}"

echo "# Historian scale benchmark (BL-162 skeleton)" > "$REPORT_FILE"
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

echo "==> Placeholder ingest benchmark (${DURATION_SEC}s)"
echo "- Ingest benchmark: **not implemented** — wire event fire + historian dual-write per playbook." >> "$REPORT_FILE"
echo "- Next steps:" >> "$REPORT_FILE"
echo "  1. Enable ClickHouse per ${PLAYBOOK#${ROOT}/}" >> "$REPORT_FILE"
echo "  2. Run dual-write validation (\`ISPF_VARIABLE_HISTORY_STORE=jdbc\` + CH secondary)" >> "$REPORT_FILE"
echo "  3. Replay lab load script against \`POST /api/v1/events/fire\`" >> "$REPORT_FILE"
echo "  4. Query aggregate SLA (\`GET .../variables/history/aggregate\`) — see BL-161 SLO" >> "$REPORT_FILE"

echo "" >> "$REPORT_FILE"
echo "Report written to $REPORT_FILE"
cat "$REPORT_FILE"
