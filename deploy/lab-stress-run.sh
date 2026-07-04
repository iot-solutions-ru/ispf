#!/usr/bin/env bash
# Maximum event-journal stress on lab host (Scylla + ISPF tuned via lab-stress.env).
set -euo pipefail

LAB_ROOT="${ISPF_LAB_ROOT:-$HOME/ispf}"
COMPOSE_FILE="$LAB_ROOT/lab-test-host-compose.yml"
ENV_FILE="$LAB_ROOT/lab-stress.env"
HTTP_PORT="${ISPF_LAB_HTTP_PORT:-8000}"
LOG="$LAB_ROOT/loadtest/stress-run.log"

if [ -f "$ENV_FILE" ]; then
  set -a
  # shellcheck disable=SC1090
  source "$ENV_FILE"
  set +a
fi

DEVICES="${STRESS_DEVICES:-16}"
RATE_PER_DEVICE="${STRESS_RATE_PER_DEVICE:-5000}"
CALLBACK_THREADS="${STRESS_CALLBACK_THREADS:-32}"
WARMUP="${STRESS_WARMUP:-20}"
PHASE="${STRESS_PHASE:-90}"

cd "$LAB_ROOT"

echo "=== STRESS RUN $(date -Is) ===" | tee "$LOG"
echo "Scylla smp=${SCYLLA_SMP:-?} memory=${SCYLLA_MEMORY:-?}" | tee -a "$LOG"
echo "Java: ${ISPF_JAVA_OPTS:-default}" | tee -a "$LOG"
echo "Target: ${DEVICES} x ${RATE_PER_DEVICE} msg/s = $((DEVICES * RATE_PER_DEVICE)) aggregate" | tee -a "$LOG"

(
  while true; do
    echo "--- $(date -Is) ---" >> "$LAB_ROOT/loadtest/stress-docker-stats.log"
    docker stats --no-stream --format '{{.Name}} {{.CPUPerc}} {{.MemUsage}}' \
      $(docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" ps -q 2>/dev/null) \
      >> "$LAB_ROOT/loadtest/stress-docker-stats.log" 2>/dev/null || true
    sleep 5
  done
) &
stats_pid=$!
trap 'kill $stats_pid 2>/dev/null || true' EXIT

DEVICES="$DEVICES" RATE_PER_DEVICE="$RATE_PER_DEVICE" \
  WARMUP="$WARMUP" PHASE="$PHASE" \
  CALLBACK_THREADS="$CALLBACK_THREADS" BENCH_NO_L0_COALESCE=true \
  ISPF_LAB_ROOT="$LAB_ROOT" ISPF_LAB_HTTP_PORT="$HTTP_PORT" \
  bash "$LAB_ROOT/lab-mqtt-event-journal-multi-test.sh" 2>&1 | tee -a "$LOG"

kill $stats_pid 2>/dev/null || true
trap - EXIT

echo "=== docker stats tail ===" | tee -a "$LOG"
tail -20 "$LAB_ROOT/loadtest/stress-docker-stats.log" | tee -a "$LOG"
echo "=== host load ===" | tee -a "$LOG"
uptime | tee -a "$LOG"
echo "Log: $LOG" | tee -a "$LOG"
