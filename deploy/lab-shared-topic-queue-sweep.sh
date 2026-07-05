#!/usr/bin/env bash
# Shared MQTT topic fan-out: N devices subscribe to one topic, low publish rate → N× journal writes.
set -euo pipefail

LAB_ROOT="${ISPF_LAB_ROOT:-$HOME/ispf}"
COMPOSE_FILE="$LAB_ROOT/lab-test-host-compose.yml"
ENV_FILE="$LAB_ROOT/lab-stress.env"
HTTP_PORT="${ISPF_LAB_HTTP_PORT:-8000}"
DIR="$LAB_ROOT/loadtest"
VENV="${DIR}/venv/bin/python"
SETUP="${DIR}/setup-mqtt-event-journal-devices.py"
BASE_URL="http://127.0.0.1:${HTTP_PORT}"
LOG="${LOG:-$LAB_ROOT/loadtest/journal-shared-topic-sweep.log}"

DEVICES="${DEVICES:-100}"
SHARED_TOPIC="${SHARED_TOPIC:-ispf/loadtest/shared/temperature}"
# Publish rate on the single shared topic (broker PUBLISH/s). Expected journal ≈ rate × DEVICES.
SWEEP_PUBLISH_RATES="${SWEEP_PUBLISH_RATES:-1000 5000 10000}"
WARMUP="${WARMUP:-20}"
PHASE="${PHASE:-20}"
INTERVAL_MS="${INTERVAL_MS:-1}"
EMQTT_CPU_LIMIT="${EMQTT_CPU_LIMIT:-2.0}"
SHARED_TOPIC_SHARDS="${SHARED_TOPIC_SHARDS:-4}"
QUEUE_POLL_SEC="${QUEUE_POLL_SEC:-2}"
QUEUE_STOP_THRESHOLD="${QUEUE_STOP_THRESHOLD:-500}"
QUEUE_STOP_CONSECUTIVE="${QUEUE_STOP_CONSECUTIVE:-2}"
STOP_ON_QUEUE="${STOP_ON_QUEUE:-true}"
SKIP_DEVICE_SETUP="${SKIP_DEVICE_SETUP:-false}"

MQTT_HOST="${MQTT_HOST:-mqtt}"
MQTT_PORT="${MQTT_PORT:-1883}"
EMQTT_DOCKER_NETWORK="${EMQTT_DOCKER_NETWORK:-ispf-lab_default}"

_launch_cpu="${EMQTT_CPU_LIMIT:-}"
_launch_shards="${SHARED_TOPIC_SHARDS:-}"
if [ -f "$ENV_FILE" ]; then
  set -a
  # shellcheck disable=SC1090
  source "$ENV_FILE"
  set +a
fi
if [ -n "$_launch_cpu" ]; then
  EMQTT_CPU_LIMIT="$_launch_cpu"
fi
if [ -n "$_launch_shards" ]; then
  SHARED_TOPIC_SHARDS="$_launch_shards"
fi
EMQTT_CPU_LIMIT="${EMQTT_CPU_LIMIT:-2.0}"
SHARED_TOPIC_SHARDS="${SHARED_TOPIC_SHARDS:-4}"

if [ ! -x "$VENV" ]; then
  VENV=python3
fi

exec > >(tee -a "$LOG") 2>&1
echo "=== Shared-topic fan-out sweep $(date -Is) ==="
echo "DEVICES=${DEVICES} topic=${SHARED_TOPIC}"
echo "publish_rates=${SWEEP_PUBLISH_RATES} (broker msg/s; expect journal ≈ rate×${DEVICES})"
echo "emqtt_shards=${SHARED_TOPIC_SHARDS} cpu_limit=${EMQTT_CPU_LIMIT}/shard warmup=${WARMUP}s phase=${PHASE}s queue_threshold=${QUEUE_STOP_THRESHOLD} LOG=${LOG}"

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

journal_queue_size() { automation_metric eventJournalQueueSize; }
events_fired_total() { automation_metric eventsFiredTotal; }
journal_sync_fallback_total() { automation_metric eventJournalSyncFallbackTotal; }

mqtt_container_id() {
  docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" ps -q mqtt 2>/dev/null | head -1
}

broker_messages_received_total() {
  local cid
  cid=$(mqtt_container_id)
  if [ -z "$cid" ]; then
    echo 0
    return
  fi
  docker exec "$cid" mosquitto_sub -h localhost -t '$SYS/broker/messages/received' -C 1 -W 5 2>/dev/null \
    | tr -d '\r' | awk '{print $NF}' | tail -1
}

wait_queue_empty() {
  local max_wait="${1:-120}"
  echo "Waiting for event-journal queue drain (max ${max_wait}s)..."
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
  return 1
}

stop_emqtt_publishers() {
  docker ps -q --filter "label=ispf.emqtt-bench=1" 2>/dev/null | xargs -r docker stop >/dev/null 2>&1 || true
}

run_shared_topic_publisher() {
  local publish_rate=$1
  local pub_sec=$2
  SHARED_TOPIC_SHARDS="$SHARED_TOPIC_SHARDS" EMQTT_CPU_LIMIT="$EMQTT_CPU_LIMIT" \
    bash "${DIR}/mqtt-emqtt-bench.sh" \
    --host "$MQTT_HOST" --port "$MQTT_PORT" \
    --docker-network "$EMQTT_DOCKER_NETWORK" \
    --topic "$SHARED_TOPIC" \
    --shared-topic-shards "$SHARED_TOPIC_SHARDS" \
    --messages-per-second "$publish_rate" \
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
          echo "QUEUE DETECTED eventJournalQueueSize=${q} at ${elapsed}s"
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
stop_emqtt_publishers
bash "${LAB_ROOT}/lab-emqtt-cleanup.sh" 2>/dev/null || true

if [ "$SKIP_DEVICE_SETUP" != "true" ] && [ -f "$SETUP" ]; then
  echo "Seeding ${DEVICES} devices on shared topic ${SHARED_TOPIC}..."
  "$VENV" "$SETUP" \
    --devices "$DEVICES" \
    --shared-topic "$SHARED_TOPIC" \
    --telemetry-coalesce-ms 1 \
    --base-url "$BASE_URL" \
    --broker-url "tcp://mqtt:1883" \
    --bench-no-l0-coalesce
  sleep 5
fi

curl -sf "${BASE_URL}/api/v1/info" -H "Authorization: Bearer $(curl -sf -X POST "${BASE_URL}/api/v1/auth/login" \
  -H 'Content-Type: application/json' -d '{"username":"admin","password":"admin"}' | python3 -c 'import json,sys; print(json.load(sys.stdin)["token"])')" \
  | python3 -c "import json,sys; d=json.load(sys.stdin); print('version', d.get('version'))" || true

wait_queue_empty 120 || true
sleep 5

SWEEP_ABORTED=false
PEAK_FIRED=0

for publish_rate in $SWEEP_PUBLISH_RATES; do
  if [ "$SWEEP_ABORTED" = "true" ]; then
    echo "skip publish_rate=${publish_rate} (sweep stopped)"
    continue
  fi

  expected_journal=$((publish_rate * DEVICES))
  echo ""
  echo "========== STEP publish=${publish_rate}/s × ${DEVICES} subscribers → expect ~${expected_journal}/s journal =========="
  bash "${LAB_ROOT}/lab-emqtt-cleanup.sh" 2>/dev/null || true

  before_queue=$(journal_queue_size)
  before_fired=$(events_fired_total)
  before_fallback=$(journal_sync_fallback_total)
  before_broker=$(broker_messages_received_total)
  before_broker=${before_broker:-0}
  echo "before: eventsFired=${before_fired} queue=${before_queue} syncFallback=${before_fallback} brokerRx=${before_broker}"

  pub_sec=$((WARMUP + PHASE + 15))
  pub_pid=$(run_shared_topic_publisher "$publish_rate" "$pub_sec")
  sleep "${WARMUP}"

  start_fired=$(events_fired_total)
  start_broker=$(broker_messages_received_total)
  start_broker=${start_broker:-0}
  measure_start=$SECONDS
  monitor_load_until_queue_or_done "$PHASE"
  measure_sec=$((SECONDS - measure_start))
  if [ "$SWEEP_QUEUE_DETECTED" != "true" ]; then
    wait "$pub_pid" 2>/dev/null || true
    measure_sec=$PHASE
  fi
  sleep 3

  end_fired=$(events_fired_total)
  end_broker=$(broker_messages_received_total)
  end_broker=${end_broker:-0}
  after_queue=$(journal_queue_size)
  after_fallback=$(journal_sync_fallback_total)
  delta_fired=$((end_fired - start_fired))
  delta_broker=$((end_broker - start_broker))
  if [ "${delta_broker:-0}" -lt 0 ] 2>/dev/null; then
    delta_broker=0
  fi
  delta_fallback=$((after_fallback - before_fallback))
  rate_fired=$(awk -v d="$delta_fired" -v t="$measure_sec" 'BEGIN { if (t > 0) printf "%.1f", d/t; else print "0.0" }')
  rate_broker=$(awk -v d="$delta_broker" -v t="$measure_sec" 'BEGIN { if (t > 0) printf "%.1f", d/t; else print "0.0" }')
  rate_per_dev=$(awk -v r="$rate_fired" -v n="$DEVICES" 'BEGIN { if (n > 0) printf "%.1f", r/n; else print "0.0" }')
  fanout_cfg=$(awk -v j="$rate_fired" -v p="$publish_rate" 'BEGIN { if (p > 0) printf "%.2f", j/p; else print "0.0" }')
  fanout=$(awk -v j="$rate_fired" -v b="$rate_broker" 'BEGIN { if (b > 0) printf "%.2f", j/b; else print "0.0" }')

  echo "RESULT publish=${publish_rate}/s expected_journal=${expected_journal}/s measure=${measure_sec}s"
  echo "  broker_rx_per_sec=${rate_broker} (measured Mosquitto \$SYS)"
  echo "  eventsFired_delta=${delta_fired} eventsFired_per_sec=${rate_fired} (~${rate_per_dev}/device)"
  echo "  fanout_ratio=${fanout} (journal/broker; ideal=${DEVICES}) fanout_cfg=${fanout_cfg} (journal/configured)"
  echo "  eventJournalQueueSize=${after_queue} syncFallback_delta=${delta_fallback}"
  docker_peak_stats
  echo "SWEEP_ROW|${publish_rate}|${DEVICES}|${expected_journal}|${measure_sec}|${delta_fired}|${rate_fired}|${rate_per_dev}|${fanout}|${after_queue}|${delta_fallback}|${rate_broker}|${fanout_cfg}"

  if awk -v r="$rate_fired" -v p="$PEAK_FIRED" 'BEGIN { exit (r+0 > p+0) ? 0 : 1 }'; then
    PEAK_FIRED=$rate_fired
  fi

  if [ "$SWEEP_QUEUE_DETECTED" = "true" ]; then
    echo "=== SWEEP STOPPED: queue=${SWEEP_QUEUE_SIZE} at publish=${publish_rate}/s (~${rate_fired}/s journal), elapsed=${SWEEP_QUEUE_AT_ELAPSED}s ==="
    SWEEP_ABORTED=true
    break
  fi

  if [ "${after_queue:-0}" -ge "${QUEUE_STOP_THRESHOLD}" ] 2>/dev/null; then
    SWEEP_ABORTED=true
    break
  fi

  echo "cooldown 20s..."
  sleep 20
done

echo ""
echo "=== SWEEP SUMMARY (${DEVICES} devices, shared topic ${SHARED_TOPIC}) ==="
python3 - "$LOG" "$DEVICES" <<'PY'
import sys
rows = []
devices = int(sys.argv[2])
try:
    with open(sys.argv[1], encoding="utf-8", errors="replace") as f:
        for line in f:
            if line.startswith("SWEEP_ROW|"):
                p = line.strip().split("|")
                if len(p) >= 11:
                    rows.append(p)
except OSError:
    pass
print("publish/s  expect_j  sec   journal/s  /device  fanout  broker/s  fan_cfg  queue  fb")
for p in rows:
    broker = p[11] if len(p) > 11 else "?"
    fan_cfg = p[12] if len(p) > 12 else "?"
    print(f"{p[1]:>8}  {p[3]:>8}  {p[4]:>3}  {p[6]:>9}  {p[7]:>7}  {p[8]:>6}  {broker:>8}  {fan_cfg:>7}  {p[9]:>5}  {p[10]}")
if rows:
    best = max(rows, key=lambda r: float(r[6] or 0))
    print(f"peak journal: publish={best[1]}/s → {best[6]} events/s (fanout={best[8]}, ideal={devices})")
    q_rows = [r for r in rows if int(r[9] or 0) > 0]
    if q_rows:
        first = q_rows[0]
        print(f"first queue>0 after step: publish={first[1]}/s journal={first[6]}/s queue={first[9]}")
PY

docker stats --no-stream --format '{{.Name}} {{.CPUPerc}} {{.MemUsage}}' \
  $(docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" ps -q 2>/dev/null) 2>/dev/null || true
echo "=== DONE $(date -Is) ==="
