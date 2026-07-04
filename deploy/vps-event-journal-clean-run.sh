#!/usr/bin/env bash
# Clean prod baseline + multi-device event journal benchmark (Scylla).
set -euo pipefail

INSTALL_ROOT="${ISPF_INSTALL_ROOT:-/opt/ispf}"
LOADTEST="${INSTALL_ROOT}/loadtest"
FACTORY_RESET="${INSTALL_ROOT}/bin/vps-factory-reset.sh"
MULTI_TEST="${LOADTEST}/mqtt-event-journal-multi-test-remote.sh"
LOG="${LOADTEST}/event-journal-8x2000.log"

DEVICES="${DEVICES:-8}"
RATE_PER_DEVICE="${RATE_PER_DEVICE:-2000}"

echo "=== Stop running load tests ==="
pkill -f mqtt-event-journal-multi-test-remote.sh 2>/dev/null || true
pkill -f mqtt-emqtt-bench 2>/dev/null || true
sleep 2

echo "=== Factory reset (PostgreSQL + Scylla, no demo fixtures) ==="
bash "$FACTORY_RESET" --no-fixtures

echo "=== Start benchmark in background ==="
: > "$LOG"
nohup env DEVICES="$DEVICES" RATE_PER_DEVICE="$RATE_PER_DEVICE" \
  bash "$MULTI_TEST" >> "$LOG" 2>&1 &
echo "PID=$!"
echo "Log: $LOG"
echo "Tail: tail -f $LOG"
