#!/usr/bin/env bash
# BL-210: orchestrate Enterprise L analytics lab gates (tracked in git).
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT"

REPORT_DIR="${ISPF_ANALYTICS_BENCH_DIR:-$ROOT/build/analytics-scale}"
mkdir -p "$REPORT_DIR"
SUMMARY_FILE="$REPORT_DIR/enterprise-l-gates-summary.md"

BASE_URL="${ISPF_ANALYTICS_BENCH_BASE_URL:-${ISPF_BENCH_BASE_URL:-http://127.0.0.1:8080}}"
RUN_HISTORIAN="${ISPF_ENTERPRISE_L_RUN_HISTORIAN_GATE:-false}"

GATE_EXIT=0

{
  echo "# Enterprise L gate run"
  echo
  echo "- Date: $(date -u +"%Y-%m-%dT%H:%M:%SZ")"
  echo "- Base URL: $BASE_URL"
  echo
} > "$SUMMARY_FILE"

run_gate() {
  local name="$1"
  shift
  echo "==> $name"
  if "$@"; then
    echo "- ${name}: **PASS**" >> "$SUMMARY_FILE"
  else
    echo "- ${name}: **FAIL**" >> "$SUMMARY_FILE"
    GATE_EXIT=1
  fi
}

export ISPF_ANALYTICS_BENCH_BASE_URL="$BASE_URL"
export ISPF_ANALYTICS_BENCH_SKIP_CATALOG_GATE="${ISPF_ANALYTICS_BENCH_SKIP_CATALOG_GATE:-false}"
export ISPF_ANALYTICS_BENCH_SKIP_CH_GATE="${ISPF_ANALYTICS_BENCH_SKIP_CH_GATE:-true}"

run_gate "Analytics scale gate" bash "$ROOT/tools/historian-scale/analytics-scale-gate.sh"

if [[ "$RUN_HISTORIAN" == "true" ]]; then
  run_gate "Historian scale benchmark" bash "$ROOT/tools/historian-scale/historian-scale-benchmark.sh"
else
  echo "- Historian scale benchmark: **SKIPPED** (set ISPF_ENTERPRISE_L_RUN_HISTORIAN_GATE=true)" >> "$SUMMARY_FILE"
fi

{
  echo
  echo "Seed catalog (Enterprise L):"
  echo
  echo '```bash'
  echo "python3 tools/historian-scale/seed-analytics-scale-catalog.py \\"
  echo "  --base-url \"$BASE_URL\" --tags 50000 --batch 200 --workers 12"
  echo '```'
  echo
  echo "Detailed reports:"
  echo "- $REPORT_DIR/analytics-scale-gate.md"
  echo "- $ROOT/build/historian-scale/scale-benchmark.md (if historian gate enabled)"
  echo
  echo "Playbook: examples/analytics-platform/enterprise-l/README.md"
} >> "$SUMMARY_FILE"

echo
cat "$SUMMARY_FILE"
exit "$GATE_EXIT"
