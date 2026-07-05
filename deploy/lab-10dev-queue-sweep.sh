#!/usr/bin/env bash
# 10-device event journal sweep — increase MQTT rate until eventJournalQueueSize > 0.
set -euo pipefail

LAB_ROOT="${ISPF_LAB_ROOT:-$HOME/ispf}"
COMPOSE_FILE="$LAB_ROOT/lab-test-host-compose.yml"
ENV_FILE="$LAB_ROOT/lab-stress.env"
HTTP_PORT="${ISPF_LAB_HTTP_PORT:-8000}"
DIR="$LAB_ROOT/loadtest"
VENV="${DIR}/venv/bin/python"
SETUP="${DIR}/setup-mqtt-event-journal-devices.py"
BASE_URL="http://127.0.0.1:${HTTP_PORT}"
LOG="${LOG:-$LAB_ROOT/loadtest/journal-10dev-queue-sweep.log}"

DEVICES="${DEVICES:-10}"
SWEEP_RATES="${SWEEP_RATES:-6000 8000 10000 11000 12000 13000 14000 15000 16000 18000 20000 25000 30000 35000 40000}"
WARMUP="${WARMUP:-15}"
PHASE="${PHASE:-60}"
INTERVAL_MS="${INTERVAL_MS:-1}"
EMQTT_SHARD_MAX="${EMQTT_SHARD_MAX:-8}"
EMQTT_CPU_LIMIT="${EMQTT_CPU_LIMIT:-2.5}"
QUEUE_POLL_SEC="${QUEUE_POLL_SEC:-2}"
QUEUE_STOP_THRESHOLD="${QUEUE_STOP_THRESHOLD:-500}"
QUEUE_STOP_CONSECUTIVE="${QUEUE_STOP_CONSECUTIVE:-2}"
STOP_ON_QUEUE="${STOP_ON_QUEUE:-true}"
SKIP_DEVICE_SETUP="${SKIP_DEVICE_SETUP:-false}"

MQTT_HOST="${MQTT_HOST:-mqtt}"
MQTT_PORT="${MQTT_PORT:-1883}"
EMQTT_DOCKER_NETWORK="${EMQTT_DOCKER_NETWORK:-ispf-lab_default}"

if [ -f "$ENV_FILE" ]; then
  set -a
  # shellcheck disable=SC1090
  source "$ENV_FILE"
  set +a
fi

if [ ! -x "$VENV" ]; then
  VENV=python3
fi

exec > >(tee -a "$LOG") 2>&1
echo "=== 10-device queue sweep $(date -Is) ==="
echo "DEVICES=${DEVICES} rates=${SWEEP_RATES} warmup=${WARMUP}s phase=${PHASE}s STOP_ON_QUEUE=${STOP_ON_QUEUE} queue_threshold=${QUEUE_STOP_THRESHOLD}"
echo "LOG=${LOG}"

AUTH_TOKEN=""

ensure_api_token() {
  if [ -n "${AUTH_TOKEN:-}" ]; then
    return 0
  fi
  AUTH_TOKEN=$(curl -sf -X POST "${BASE_URL}/api/v1/auth/login" \
    -H 'Content-Type: application/json' \
    -d '{"username":"admin","password":"admin"}' \
    | python3 -c "import json,sys; print(json.load(sys.stdin).get('token',''))")
}

automation_metric() {
  local key=$1
  ensure_api_token
  curl -sf "${BASE_URL}/api/v1/platform/metrics" \
    -H "Authorization: Bearer ${AUTH_TOKEN}" \
    | python3 -c "
import json, sys
key = sys.argv[1]
d = json.load(sys.stdin)
for section in d.get('sections', []):
    if section.get('id') == 'automation':
        print(int(section.get('values', {}).get(key, 0) or 0))
        break
else:
    print(0)
" "$key"
}

journal_queue_size() {
  automation_metric eventJournalQueueSize
}

events_fired_total() {
  automation_metric eventsFiredTotal
}

journal_sync_fallback_total() {
  automation_metric eventJournalSyncFallbackTotal
}

wait_queue_empty() {
  local max_wait="${1:-120}"
  echo "Waiting for event-journal queue drain (max ${max_wait}s, target<=${QUEUE_STOP_THRESHOLD})..."
  local deadline=$((SECONDS + max_wait))
  while [ "$SECONDS" -lt "$deadline" ]; do
    local q
    q=$(journal_queue_size)
    echo "  eventJournalQueueSize=${q}"
    if [ "${q:-0}" -le "${QUEUE_STOP_THRESHOLD}" ] 2>/dev/null; then
      return 0
    fi
    sleep 5
  done
  echo "  queue still ${q} after ${max_wait}s"
  return 1
}

stop_emqtt_publishers() {
  docker ps -q --filter "label=ispf.emqtt-bench=1" 2>/dev/null | xargs -r docker stop >/dev/null 2>&1 || true
}

run_emqtt_publisher() {
  local total_rate=$1
  local pub_sec=$2
  bash "${DIR}/mqtt-emqtt-bench.sh" \
    --host "$MQTT_HOST" --port "$MQTT_PORT" \
    --docker-network "$EMQTT_DOCKER_NETWORK" \
    --single-container --shard-max "$EMQTT_SHARD_MAX" \
    --devices "$DEVICES" \
    --messages-per-second "$total_rate" \
    --duration-seconds "$pub_sec" \
    --interval-ms "${INTERVAL_MS}" >/dev/null 2>&1 &
  echo $!
}

SWEEP_QUEUE_DETECTED=false
SWEEP_QUEUE_CONSEC=0

monitor_load_until_queue_or_done() {
  local max_sec=$1
  local elapsed=0
  local poll="${QUEUE_POLL_SEC}"
  SWEEP_QUEUE_CONSEC=0
  while [ "$elapsed" -lt "$max_sec" ]; do
    if [ "$STOP_ON_QUEUE" = "true" ]; then
      local q
      q=$(journal_queue_size)
      if [ "${q:-0}" -ge "${QUEUE_STOP_THRESHOLD}" ] 2>/dev/null; then
        SWEEP_QUEUE_CONSEC=$((SWEEP_QUEUE_CONSEC + 1))
        echo "  queue=${q} at ${elapsed}s (consecutive=${SWEEP_QUEUE_CONSEC}/${QUEUE_STOP_CONSECUTIVE})"
        if [ "$SWEEP_QUEUE_CONSEC" -ge "$QUEUE_STOP_CONSECUTIVE" ]; then
          echo "QUEUE DETECTED eventJournalQueueSize=${q} at ${elapsed}s (threshold=${QUEUE_STOP_THRESHOLD})"
          stop_emqtt_publishers
          SWEEP_QUEUE_DETECTED=true
          SWEEP_QUEUE_AT_ELAPSED=$elapsed
          SWEEP_QUEUE_SIZE=$q
          return 0
        fi
      else
        SWEEP_QUEUE_CONSEC=0
      fi
    fi
    sleep "$poll"
    elapsed=$((elapsed + poll))
  done
  SWEEP_QUEUE_DETECTED=false
}

scylla_cid() {
  docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" ps -q scylla 2>/dev/null || true
}

docker_peak_stats() {
  docker stats --no-stream --format '  docker {{.Name}} cpu={{.CPUPerc}} mem={{.MemUsage}}' \
    "$(docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" ps -q ispf-server 2>/dev/null)" \
    "$(scylla_cid)" 2>/dev/null || true
}

cd "$LAB_ROOT"
bash "${LAB_ROOT}/lab-emqtt-cleanup.sh" 2>/dev/null || true

if [ "$SKIP_DEVICE_SETUP" != "true" ] && [ -f "$SETUP" ]; then
  echo "Seeding ${DEVICES} MQTT devices (EVENT_JOURNAL_ONLY, bench-no-l0-coalesce)..."
  "$VENV" "$SETUP" \
    --devices "$DEVICES" \
    --telemetry-coalesce-ms 1 \
    --base-url "$BASE_URL" \
    --broker-url "tcp://mqtt:1883" \
    --bench-no-l0-coalesce
  sleep 3
fi

curl -sf "${BASE_URL}/api/v1/info" -H "Authorization: Bearer $(curl -sf -X POST "${BASE_URL}/api/v1/auth/login" \
  -H 'Content-Type: application/json' -d '{"username":"admin","password":"admin"}' | python3 -c 'import json,sys; print(json.load(sys.stdin)["token"])')" \
  | python3 -c "import json,sys; d=json.load(sys.stdin); print('version', d.get('version'))" || true

wait_queue_empty 120 || true
sleep 5

SWEEP_ABORTED=false
PEAK_RATE=0
PEAK_FIRED=0

for rate in $SWEEP_RATES; do
  if [ "$SWEEP_ABORTED" = "true" ]; then
    echo "skip rate=${rate} (sweep stopped on queue)"
    continue
  fi

  aggregate=$((DEVICES * rate))
  echo ""
  echo "========== STEP rate=${rate}/device aggregate=${aggregate} msg/s =========="
  bash "${LAB_ROOT}/lab-emqtt-cleanup.sh" 2>/dev/null || true

  before_queue=$(journal_queue_size)
  before_fired=$(events_fired_total)
  before_fallback=$(journal_sync_fallback_total)
  echo "before: eventsFired=${before_fired} queue=${before_queue} syncFallback=${before_fallback}"
  if [ "${before_queue:-0}" -ge "${QUEUE_STOP_THRESHOLD}" ] 2>/dev/null; then
    echo "Queue already ${before_queue} before step — waiting to drain..."
    wait_queue_empty 120 || SWEEP_ABORTED=true
    if [ "$SWEEP_ABORTED" = "true" ]; then
      break
    fi
  fi

  pub_sec=$((WARMUP + PHASE + 15))
  pub_pid=$(run_emqtt_publisher "$aggregate" "$pub_sec")
  sleep "${WARMUP}"

  start_fired=$(events_fired_total)
  measure_start=$SECONDS
  monitor_load_until_queue_or_done "$PHASE"
  measure_sec=$((SECONDS - measure_start))
  if [ "$SWEEP_QUEUE_DETECTED" != "true" ]; then
    wait "$pub_pid" 2>/dev/null || true
    measure_sec=$PHASE
  fi
  sleep 3

  end_fired=$(events_fired_total)
  after_queue=$(journal_queue_size)
  after_fallback=$(journal_sync_fallback_total)
  delta_fired=$((end_fired - start_fired))
  delta_fallback=$((after_fallback - before_fallback))
  rate_fired=$(awk -v d="$delta_fired" -v t="$measure_sec" 'BEGIN { if (t > 0) printf "%.1f", d/t; else print "0.0" }')

  echo "RESULT rate=${rate}/dev aggregate=${aggregate} measure=${measure_sec}s"
  echo "  eventsFired_delta=${delta_fired} eventsFired_per_sec=${rate_fired}"
  echo "  eventJournalQueueSize=${after_queue} syncFallback_delta=${delta_fallback}"
  docker_peak_stats
  echo "SWEEP_ROW|${rate}|${aggregate}|${measure_sec}|${delta_fired}|${rate_fired}|${after_queue}|${delta_fallback}"

  if awk -v r="$rate_fired" -v p="$PEAK_FIRED" 'BEGIN { exit (r+0 > p+0) ? 0 : 1 }'; then
    PEAK_RATE=$rate
    PEAK_FIRED=$rate_fired
  fi

  if [ "$SWEEP_QUEUE_DETECTED" = "true" ]; then
    echo "=== SWEEP STOPPED: queue appeared at rate=${rate}/device (${aggregate} aggregate), queue=${SWEEP_QUEUE_SIZE}, elapsed=${SWEEP_QUEUE_AT_ELAPSED}s ==="
    SWEEP_ABORTED=true
    break
  fi

  if [ "${after_queue:-0}" -ge "${QUEUE_STOP_THRESHOLD}" ] 2>/dev/null; then
    echo "=== SWEEP STOPPED: queue=${after_queue} after step rate=${rate}/device ==="
    SWEEP_ABORTED=true
    break
  fi

  echo "cooldown 15s..."
  sleep 15
done

echo ""
echo "=== SWEEP SUMMARY (10 devices, stop on queue) ==="
python3 - "$LOG" <<'PY'
import sys
rows = []
try:
    with open(sys.argv[1], encoding="utf-8", errors="replace") as f:
        for line in f:
            if line.startswith("SWEEP_ROW|"):
                p = line.strip().split("|")
                if len(p) >= 8:
                    rows.append(p)
except OSError:
    pass
print("rate/dev  aggregate  sec   fired/s   queue  fb")
for p in rows:
    print(f"{p[1]:>7}  {p[2]:>9}  {p[3]:>3}  {p[5]:>8}  {p[6]:>5}  {p[7]}")
if rows:
    best = max(rows, key=lambda r: float(r[5] or 0))
    print(f"peak throughput: step rate={best[1]}/dev → {best[5]} events/s (queue={best[6]})")
    q_rows = [r for r in rows if int(r[6] or 0) > 0]
    if q_rows:
        first = q_rows[0]
        print(f"first queue: rate={first[1]}/dev aggregate={first[2]} queue={first[6]} at {first[5]} events/s")
PY

docker stats --no-stream --format '{{.Name}} {{.CPUPerc}} {{.MemUsage}}' \
  $(docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" ps -q 2>/dev/null) 2>/dev/null || true
echo "=== DONE $(date -Is) ==="
