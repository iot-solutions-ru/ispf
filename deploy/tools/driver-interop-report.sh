#!/usr/bin/env bash
# BL-141: run top-20 driver interop tests and emit a pass/latency summary.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT"

MODULES=(
  ispf-driver-virtual
  ispf-driver-mqtt
  ispf-driver-modbus
  ispf-driver-modbus-rtu
  ispf-driver-modbus-udp
  ispf-driver-flexible
  ispf-driver-opcua
  ispf-driver-opcua-server
  ispf-driver-snmp
  ispf-driver-bacnet
  ispf-driver-s7
  ispf-driver-http
  ispf-driver-iec104
  ispf-driver-iec104-server
  ispf-driver-dnp3
  ispf-driver-dlms
  ispf-driver-ethernet-ip
  ispf-driver-opc-da
  ispf-driver-opc-bridge
  ispf-driver-gps-tracker
)

CI_SUMMARY=false
if [[ "${1:-}" == "--ci-summary" ]]; then
  CI_SUMMARY=true
fi

REPORT_DIR="${ISPF_INTEROP_REPORT_DIR:-$ROOT/build/driver-interop}"
mkdir -p "$REPORT_DIR"
REPORT_FILE="$REPORT_DIR/interop-report.tsv"
SUMMARY_FILE="$REPORT_DIR/interop-summary.md"

echo -e "module\tresult\tduration_ms\ttests_run" >"$REPORT_FILE"

PASS=0
FAIL=0

for module in "${MODULES[@]}"; do
  echo "=== $module ==="
  START_MS=$(date +%s%3N)
  LOG_FILE="$REPORT_DIR/${module}.log"
  if ./gradlew ":packages:${module}:test" --no-daemon >"$LOG_FILE" 2>&1; then
    RESULT=pass
    PASS=$((PASS + 1))
  else
    RESULT=fail
    FAIL=$((FAIL + 1))
  fi
  END_MS=$(date +%s%3N)
  DURATION_MS=$((END_MS - START_MS))
  TESTS_RUN=$(grep -Eo '[0-9]+ tests? completed' "$LOG_FILE" | tail -1 | awk '{print $1}' || true)
  TESTS_RUN="${TESTS_RUN:-0}"
  echo -e "${module}\t${RESULT}\t${DURATION_MS}\t${TESTS_RUN}" >>"$REPORT_FILE"
  echo "${module}: ${RESULT} (${DURATION_MS} ms, ${TESTS_RUN} tests)"
done

{
  echo "# Driver interop report"
  echo
  echo "Generated: $(date -u +"%Y-%m-%dT%H:%M:%SZ")"
  echo
  echo "| Module | Result | Duration (ms) | Tests |"
  echo "| ------ | ------ | ------------- | ----- |"
  tail -n +2 "$REPORT_FILE" | while IFS=$'\t' read -r module result duration tests; do
    echo "| \`${module}\` | ${result} | ${duration} | ${tests} |"
  done
  echo
  echo "Pass: **${PASS}** / Fail: **${FAIL}** / Total: **$((PASS + FAIL))**"
} >"$SUMMARY_FILE"

echo
echo "Report: $SUMMARY_FILE"

if $CI_SUMMARY && [[ -n "${GITHUB_STEP_SUMMARY:-}" ]]; then
  cat "$SUMMARY_FILE" >>"$GITHUB_STEP_SUMMARY"
fi

if [[ "$FAIL" -gt 0 ]]; then
  exit 1
fi
