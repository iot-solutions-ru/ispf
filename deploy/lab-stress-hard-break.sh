#!/usr/bin/env bash
# Push 16×50k MQTT until API/login fails or Scylla restarts / goes down.
set -euo pipefail

LAB_ROOT="${ISPF_LAB_ROOT:-$HOME/ispf}"
COMPOSE_FILE="$LAB_ROOT/lab-test-host-compose.yml"
ENV_FILE="$LAB_ROOT/lab-stress.env"
HTTP_PORT="${ISPF_LAB_HTTP_PORT:-8000}"
REPORT="$LAB_ROOT/loadtest/stress-hard-break-report.txt"
WATCH="$LAB_ROOT/loadtest/stress-hard-break-watch.log"
BENCH_LOG="$LAB_ROOT/loadtest/stress-hard-break-bench.log"

DEVICES="${HARD_DEVICES:-16}"
RATE_PER_DEVICE="${HARD_RATE_PER_DEVICE:-50000}"
WARMUP="${HARD_WARMUP:-30}"
PHASE="${HARD_PHASE:-120}"
CALLBACK_THREADS="${HARD_CALLBACK_THREADS:-64}"
INTERVAL_MS="${HARD_INTERVAL_MS:-1}"
EMQTT_SHARD_MAX="${EMQTT_SHARD_MAX:-8}"

if [ -f "$ENV_FILE" ]; then
  set -a
  # shellcheck disable=SC1090
  source "$ENV_FILE"
  set +a
fi

cd "$LAB_ROOT"
mkdir -p "$LAB_ROOT/loadtest"

scylla_cid() {
  docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" ps -q scylla 2>/dev/null || true
}

scylla_restarts() {
  local cid
  cid="$(scylla_cid)"
  if [ -z "$cid" ]; then
    echo "0"
    return
  fi
  docker inspect --format='{{.RestartCount}}' "$cid" 2>/dev/null || echo "0"
}

scylla_state() {
  docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" ps scylla --format '{{.State}}' 2>/dev/null || echo "unknown"
}

api_ok() {
  curl -sf --max-time 5 "http://127.0.0.1:${HTTP_PORT}/api/v1/info" >/dev/null 2>&1
}

login_ok() {
  curl -sf --max-time 5 -X POST "http://127.0.0.1:${HTTP_PORT}/api/v1/auth/login" \
    -H 'Content-Type: application/json' \
    -d '{"username":"admin","password":"admin"}' >/dev/null 2>&1
}

aggregate=$((DEVICES * RATE_PER_DEVICE))
start_restarts="$(scylla_restarts)"
break_reason=""

: > "$WATCH"
{
  echo "=== HARD BREAK RUN $(date -Is) ==="
  echo "Host: $(nproc) CPUs, $(free -h | awk '/^Mem:/{print $2}') RAM"
  echo "Scylla: smp=${SCYLLA_SMP:-?} memory=${SCYLLA_MEMORY:-?}  restarts_before=${start_restarts}"
  echo "Java: ${ISPF_JAVA_OPTS:-default}"
  echo "Journal writers: ${ISPF_EVENT_JOURNAL_WRITER_THREADS:-?} batch=${ISPF_EVENT_JOURNAL_BATCH_SIZE:-?} flush_ms=${ISPF_EVENT_JOURNAL_FLUSH_INTERVAL_MS:-?}"
  echo "Target: ${DEVICES} x ${RATE_PER_DEVICE} msg/s = ${aggregate} MQTT/s"
  echo "Measure: warmup=${WARMUP}s phase=${PHASE}s interval_ms=${INTERVAL_MS} emqtt_shards=${EMQTT_SHARD_MAX}"
  echo ""
} | tee "$REPORT"

if ! api_ok || ! login_ok; then
  echo "ABORT: API/login not ready before benchmark" | tee -a "$REPORT"
  exit 2
fi

(
  while true; do
    echo "--- $(date -Is) ---" >> "$LAB_ROOT/loadtest/stress-hard-break-stats.log"
    docker stats --no-stream --format '{{.Name}} {{.CPUPerc}} {{.MemUsage}}' \
      $(docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" ps -q 2>/dev/null) \
      >> "$LAB_ROOT/loadtest/stress-hard-break-stats.log" 2>/dev/null || true
    sleep 3
  done
) &
stats_pid=$!

(
  while true; do
    ts="$(date -Is)"
    api="OK"
    login="OK"
    restarts="$(scylla_restarts)"
    state="$(scylla_state)"
    load="$(uptime | sed 's/.*load average://')"
    api_ok || api="DOWN"
    login_ok || login="FAIL"
    echo "${ts} api=${api} login=${login} scylla=${state} restarts=${restarts} load=${load}" >> "$WATCH"
    if [ "$api" = "DOWN" ] || [ "$login" = "FAIL" ]; then
      echo "${ts} BREAK api_or_login_down" >> "$WATCH"
      bash "$LAB_ROOT/lab-emqtt-cleanup.sh" 2>/dev/null || true
      exit 0
    fi
    if [ "$state" != "running" ]; then
      echo "${ts} BREAK scylla_state=${state}" >> "$WATCH"
      bash "$LAB_ROOT/lab-emqtt-cleanup.sh" 2>/dev/null || true
      exit 0
    fi
    if [ "$restarts" -gt "$start_restarts" ]; then
      echo "${ts} BREAK scylla_restarted restarts=${restarts}" >> "$WATCH"
      bash "$LAB_ROOT/lab-emqtt-cleanup.sh" 2>/dev/null || true
      exit 0
    fi
    sleep 2
  done
) &
watch_pid=$!

set +e
DEVICES="$DEVICES" RATE_PER_DEVICE="$RATE_PER_DEVICE" \
  WARMUP="$WARMUP" PHASE="$PHASE" INTERVAL_MS="$INTERVAL_MS" \
  CALLBACK_THREADS="$CALLBACK_THREADS" BENCH_NO_L0_COALESCE=true \
  EMQTT_SHARD_MAX="$EMQTT_SHARD_MAX" \
  ISPF_LAB_ROOT="$LAB_ROOT" ISPF_LAB_HTTP_PORT="$HTTP_PORT" \
  bash "$LAB_ROOT/lab-mqtt-event-journal-multi-test.sh" 2>&1 | tee "$BENCH_LOG" | tee -a "$REPORT"
bench_code=${PIPESTATUS[0]}
set -e

kill "$stats_pid" "$watch_pid" 2>/dev/null || true
wait "$stats_pid" 2>/dev/null || true
wait "$watch_pid" 2>/dev/null || true

end_restarts="$(scylla_restarts)"
if grep -q 'BREAK' "$WATCH" 2>/dev/null; then
  break_reason="$(grep 'BREAK' "$WATCH" | tail -1)"
fi
if [ "$end_restarts" -gt "$start_restarts" ] && [ -z "$break_reason" ]; then
  break_reason="scylla_restart_count ${start_restarts} -> ${end_restarts}"
fi
if ! api_ok || ! login_ok; then
  if [ -z "$break_reason" ]; then
    break_reason="API/login down after benchmark"
  fi
fi

eps=$(grep 'Events/s total (scylla):' "$BENCH_LOG" | tail -1 | awk '{print $NF}' || echo "0")
eff=$(grep 'Efficiency vs MQTT target:' "$BENCH_LOG" | tail -1 | awk '{print $NF}' || echo "0")
journal_delta=$(grep 'Journal events written:' "$BENCH_LOG" | tail -1 | awk '{print $4}' || echo "0")

{
  echo ""
  echo "=== HARD BREAK RESULT $(date -Is) ==="
  echo "Benchmark exit: ${bench_code}"
  echo "Journal events/s: ${eps}  efficiency: ${eff}  events_written: ${journal_delta}"
  echo "Scylla restarts: ${start_restarts} -> ${end_restarts}  state=$(scylla_state)"
  echo "API after: $(api_ok && echo OK || echo DOWN)  login: $(login_ok && echo OK || echo FAIL)"
  if [ -n "$break_reason" ]; then
    echo "HARD BREAK: ${break_reason}"
  else
    echo "Completed full run without detected hard break (system survived ${aggregate} MQTT/s target)."
  fi
  echo ""
  echo "=== watch tail ==="
  tail -15 "$WATCH"
  echo ""
  echo "=== docker stats tail ==="
  tail -12 "$LAB_ROOT/loadtest/stress-hard-break-stats.log" 2>/dev/null || true
  echo ""
  uptime
} | tee -a "$REPORT"

if [ -n "$break_reason" ]; then
  exit 1
fi
exit "$bench_code"
