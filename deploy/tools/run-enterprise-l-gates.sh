#!/usr/bin/env bash
# BL-210: orchestrate Enterprise L analytics lab gates.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT"

REPORT_DIR="${ISPF_ANALYTICS_BENCH_DIR:-$ROOT/build/analytics-scale}"
mkdir -p "$REPORT_DIR"
SUMMARY_FILE="$REPORT_DIR/enterprise-l-gates-summary.md"

BASE_URL="${ISPF_BENCH_BASE_URL:-http://localhost:8080}"
RUN_HISTORIAN="${ISPF_ENTERPRISE_L_RUN_HISTORIAN_GATE:-false}"
SKIP_MATERIALIZER="${ISPF_ANALYTICS_BENCH_SKIP_MATERIALIZER_GATE:-true}"

GATE_EXIT=0

echo "# Enterprise L gate run" > "$SUMMARY_FILE"
echo "" >> "$SUMMARY_FILE"
echo "- Date: $(date -u +"%Y-%m-%dT%H:%M:%SZ")" >> "$SUMMARY_FILE"
echo "- Base URL: $BASE_URL" >> "$SUMMARY_FILE"
echo "" >> "$SUMMARY_FILE"

run_gate() {
  local name="$1"
  shift
  echo "==> $name"
  if "$@"; then
    echo "- $name: **PASS**" >> "$SUMMARY_FILE"
  else
    echo "- $name: **FAIL**" >> "$SUMMARY_FILE"
    GATE_EXIT=1
  fi
}

run_gate "Analytics scale gate" bash "$ROOT/deploy/tools/analytics-scale-gate.sh"
run_gate "Materializer lag gate" bash "$ROOT/deploy/tools/analytics-materializer-lag-gate.sh"

if [[ "$RUN_HISTORIAN" == "true" ]]; then
  run_gate "Historian scale benchmark" bash "$ROOT/deploy/tools/historian-scale-benchmark.sh"
else
  echo "- Historian scale benchmark: **SKIPPED** (set ISPF_ENTERPRISE_L_RUN_HISTORIAN_GATE=true)" >> "$SUMMARY_FILE"
fi

echo "" >> "$SUMMARY_FILE"
echo "Detailed reports:" >> "$SUMMARY_FILE"
echo "- $REPORT_DIR/analytics-scale-gate.md" >> "$SUMMARY_FILE"
echo "- $REPORT_DIR/analytics-materializer-lag-gate.md" >> "$SUMMARY_FILE"
if [[ "$RUN_HISTORIAN" == "true" ]]; then
  echo "- $REPORT_DIR/scale-benchmark.md" >> "$SUMMARY_FILE"
fi
echo "" >> "$SUMMARY_FILE"
echo "See examples/analytics-platform/enterprise-l/README.md for seed + CH ingest playbook." >> "$SUMMARY_FILE"

echo ""
cat "$SUMMARY_FILE"
exit "$GATE_EXIT"
