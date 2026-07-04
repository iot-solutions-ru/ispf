#!/usr/bin/env bash
# Multi-device MQTT → TELEMETRY_ONLY → historian (Scylla variable_samples) lab benchmark.
set -euo pipefail

LAB_ROOT="${ISPF_LAB_ROOT:-$HOME/ispf}"
COMPOSE_FILE="$LAB_ROOT/lab-test-host-compose.yml"
HTTP_PORT="${ISPF_LAB_HTTP_PORT:-8000}"
DIR="$LAB_ROOT/loadtest"
VENV="${DIR}/venv/bin/python"
SETUP="${DIR}/setup-mqtt-historian-devices.py"

DEVICES="${DEVICES:-16}"
RATE_PER_DEVICE="${RATE_PER_DEVICE:-32000}"
VARIABLE="${VARIABLE:-temperature}"
PHASE="${PHASE:-60}"
WARMUP="${WARMUP:-20}"
INTERVAL_MS="${INTERVAL_MS:-1}"
BENCH_NO_L0_COALESCE="${BENCH_NO_L0_COALESCE:-true}"
SKIP_DEVICE_SETUP="${SKIP_DEVICE_SETUP:-false}"
CALLBACK_THREADS="${CALLBACK_THREADS:-}"
CALLBACK_QUEUE_CAPACITY="${CALLBACK_QUEUE_CAPACITY:-}"
TOTAL_RATE=$((DEVICES * RATE_PER_DEVICE))
BASE_URL="http://127.0.0.1:${HTTP_PORT}"
MQTT_HOST="${MQTT_HOST:-mqtt}"
MQTT_PORT="${MQTT_PORT:-1883}"
EMQTT_DOCKER_NETWORK="${EMQTT_DOCKER_NETWORK:-ispf-lab_default}"
EMQTT_SHARD_MAX="${EMQTT_SHARD_MAX:-8}"
export EMQTT_CPU_LIMIT="${EMQTT_CPU_LIMIT:-1.5}"
export NUMERIC_PAYLOAD="${NUMERIC_PAYLOAD:-true}"

if [ ! -x "$VENV" ]; then
  VENV=python3
fi

scylla_cid() {
  docker compose -f "$COMPOSE_FILE" ps -q scylla
}

mqtt_cid() {
  docker compose -f "$COMPOSE_FILE" ps -q mqtt 2>/dev/null || true
}

scylla_sample_count() {
  local cql_out
  cql_out=$(docker exec "$(scylla_cid)" cqlsh -e \
    "SELECT COUNT(*) FROM ispf.variable_samples;" 2>/dev/null || true)
  python3 -c "import re,sys; t=sys.stdin.read(); m=re.search(r'\\n\\s*(\\d+)\\s*\\n', t); print(m.group(1) if m else '0')" <<< "$cql_out"
}

mosquitto_sys_counter() {
  local topic=$1 val cid
  cid=$(mqtt_cid)
  [ -z "$cid" ] && echo 0 && return 0
  val=$(docker exec "$cid" mosquitto_sub -h localhost -t "$topic" -C 1 -W 3 2>/dev/null | tr -d '[:space:]' || true)
  [[ "${val:-}" =~ ^[0-9]+$ ]] && echo "$val" || echo 0
}

AUTH_TOKEN=""

ensure_api_token() {
  [ -n "${AUTH_TOKEN:-}" ] && return 0
  AUTH_TOKEN=$(curl -sf -X POST "${BASE_URL}/api/v1/auth/login" \
    -H 'Content-Type: application/json' \
    -d '{"username":"admin","password":"admin"}' \
    | python3 -c "import json,sys; print(json.load(sys.stdin).get('token',''))")
}

metric_section() {
  local section_id=$1 key=$2
  ensure_api_token
  curl -sf "${BASE_URL}/api/v1/platform/metrics" -H "Authorization: Bearer ${AUTH_TOKEN}" \
    | python3 -c "
import json, sys
sid, key = sys.argv[1], sys.argv[2]
d = json.load(sys.stdin)
for section in d.get('sections', []):
    if section.get('id') == sid:
        v = section.get('values', {}).get(key, 0)
        print(int(v) if isinstance(v, (int, float)) and float(v).is_integer() else v)
        break
else:
    print(0)
" "$section_id" "$key"
}

historian_flushed_total() { metric_section automation variableHistoryFlushedTotal; }
historian_queue_size() { metric_section automation variableHistoryQueueSize; }
historian_sample_count_metric() { metric_section variableHistory sampleCount; }
historian_min_interval_ms() { metric_section variableHistory minIntervalMs; }

wait_historian_queue_drain() {
  local max_wait="${1:-120}"
  local deadline=$((SECONDS + max_wait))
  echo "Waiting for variable-history queue drain (max ${max_wait}s)..." >&2
  while [ "${SECONDS}" -lt "${deadline}" ]; do
    local q
    q=$(historian_queue_size)
    echo "  variableHistoryQueueSize=${q}" >&2
    [ "${q}" -le 1000 ] && return 0
    sleep 5
  done
}

run_emqtt_publisher() {
  local total_rate=$1 pub_sec=$2
  local log_file="${DIR}/emqtt-pub-hist-$$.log"
  bash "${DIR}/mqtt-emqtt-bench.sh" \
    --host "$MQTT_HOST" --port "$MQTT_PORT" \
    --docker-network "$EMQTT_DOCKER_NETWORK" \
    --single-container --shard-max "$EMQTT_SHARD_MAX" \
    --devices "$DEVICES" \
    --messages-per-second "$total_rate" \
    --duration-seconds "$pub_sec" \
    --interval-ms "${INTERVAL_MS}" >"${log_file}" 2>&1 &
  EMQTT_LOG_FILE="${log_file}"
  echo $!
}

pct_efficiency() {
  python3 -c "print(f'{100.0 * float('$1') / max(float('$2'), 1):.1f}')"
}

echo "Historian store: scylla (lab)"
curl -sf "${BASE_URL}/api/v1/info" | python3 -c "import json,sys; d=json.load(sys.stdin); print(f\"ISPF {d.get('version')} ({d.get('environment')})\")" || true
echo "API: ${BASE_URL}"
echo "MQTT: ${MQTT_HOST}:${MQTT_PORT}  NUMERIC_PAYLOAD=${NUMERIC_PAYLOAD}"
echo "Devices: ${DEVICES} × ~${RATE_PER_DEVICE} msg/s = ~${TOTAL_RATE} msg/s"
echo "Warmup ${WARMUP}s, measure ${PHASE}s"
echo "Historian minIntervalMs (platform): $(historian_min_interval_ms)"
echo "L0 coalesce off: ${BENCH_NO_L0_COALESCE} (callbackThreads=${CALLBACK_THREADS:-platform}, callbackQueue=${CALLBACK_QUEUE_CAPACITY:-platform})"

if [ -f "$SETUP" ] && [ "$SKIP_DEVICE_SETUP" != "true" ]; then
  SETUP_ARGS=(--devices "$DEVICES" --telemetry-coalesce-ms 1 --base-url "$BASE_URL" --broker-url "tcp://mqtt:1883")
  if [ "$BENCH_NO_L0_COALESCE" = "true" ]; then
    SETUP_ARGS+=(--bench-no-l0-coalesce)
    if [ -n "${CALLBACK_THREADS:-}" ]; then
      SETUP_ARGS+=(--callback-threads "$CALLBACK_THREADS")
    fi
    if [ -n "${CALLBACK_QUEUE_CAPACITY:-}" ]; then
      SETUP_ARGS+=(--callback-queue-capacity "$CALLBACK_QUEUE_CAPACITY")
    fi
  fi
  "$VENV" "$SETUP" "${SETUP_ARGS[@]}"
fi

sleep 2
bash "${LAB_ROOT}/lab-emqtt-cleanup.sh" 2>/dev/null || true

PUBLISH_SEC=$((WARMUP + PHASE + 10))
pub_pid=$(run_emqtt_publisher "$TOTAL_RATE" "$PUBLISH_SEC")

sleep "${WARMUP}"
start_scylla=$(scylla_sample_count)
start_flushed=$(historian_flushed_total)
start_samples=$(historian_sample_count_metric)
start_mqtt_rx=$(mosquitto_sys_counter '$SYS/broker/messages/received')
start_mqtt_tx=$(mosquitto_sys_counter '$SYS/broker/messages/sent')
echo "Measure start: scylla_samples=${start_scylla} flushed=${start_flushed} metrics_sampleCount=${start_samples} mosquitto_rx=${start_mqtt_rx}"
sleep "${PHASE}"
sleep 5

wait "${pub_pid}" 2>/dev/null || true
sleep 5
wait_historian_queue_drain 45

total_scylla=$(scylla_sample_count)
end_flushed=$(historian_flushed_total)
end_samples=$(historian_sample_count_metric)
end_mqtt_rx=$(mosquitto_sys_counter '$SYS/broker/messages/received')
end_mqtt_tx=$(mosquitto_sys_counter '$SYS/broker/messages/sent')

delta_scylla=$((total_scylla - start_scylla))
delta_flushed=$((end_flushed - start_flushed))
delta_samples=$((end_samples - start_samples))
delta_mqtt_rx=$((end_mqtt_rx - start_mqtt_rx))
delta_mqtt_tx=$((end_mqtt_tx - start_mqtt_tx))
[ "$delta_mqtt_rx" -lt 0 ] && delta_mqtt_rx=0
[ "$delta_mqtt_tx" -lt 0 ] && delta_mqtt_tx=0
[ "$delta_scylla" -lt 0 ] && delta_scylla=0

rate_scylla=$(python3 -c "print(f'{$delta_scylla / max($PHASE, 1):.1f}')")
rate_flushed=$(python3 -c "print(f'{$delta_flushed / max($PHASE, 1):.1f}')")
rate_mqtt_rx=$(python3 -c "print(f'{$delta_mqtt_rx / max($PHASE, 1):.1f}')")
rate_per_dev=$(python3 -c "print(f'{$delta_scylla / max($DEVICES * $PHASE, 1):.1f}')")
eff_target=$(pct_efficiency "$delta_scylla" "$((TOTAL_RATE * PHASE))")
eff_capture="n/a"
[ "$delta_mqtt_tx" -gt 0 ] && eff_capture=$(pct_efficiency "$delta_flushed" "$delta_mqtt_tx")
eff_meta_flushed="n/a"
[ "$delta_flushed" -gt 0 ] && eff_meta_flushed=$(pct_efficiency "$delta_scylla" "$delta_flushed")

echo ""
echo "=== MQTT ${DEVICES} devices → historian (TELEMETRY_ONLY, lab) ==="
echo "Configured MQTT target: ${DEVICES} × ${RATE_PER_DEVICE} = ${TOTAL_RATE} msg/s"
echo "Measure phase: ${PHASE}s"
if [ -f "${EMQTT_LOG_FILE:-}" ]; then
  grep -E '^emqtt-bench|^  configured|^Done \(formula|^ISPF_EMQTT_' "${EMQTT_LOG_FILE}" 2>/dev/null | sed 's/^/  /' || true
fi
echo ""
echo "--- Throughput (measured during phase) ---"
echo "Mosquitto PUBLISH in:     ${rate_mqtt_rx} msg/s  (delta ${delta_mqtt_rx})"
echo "Mosquitto delivered:      $(python3 -c "print(f'{$delta_mqtt_tx / max($PHASE, 1):.1f}')") msg/s  (delta ${delta_mqtt_tx})"
echo "Historian flushed (metrics): ${rate_flushed} samples/s  (delta ${delta_flushed})"
echo "Samples (Scylla COUNT):    ${rate_scylla} samples/s  (delta ${delta_scylla})"
echo "Samples per device (avg):  ${rate_per_dev} samples/s"
echo "Platform sampleCount delta: ${delta_samples}"
echo ""
echo "--- Efficiency ---"
echo "Samples vs configured target: ${eff_target}%  (reference only)"
echo "Historian capture (flushed/delivered): ${eff_capture}%  (expect ~95–100%)"
echo "Scylla COUNT vs flushed: ${eff_meta_flushed}%"
echo "Total variable_samples (Scylla): ${total_scylla}"

docker stats --no-stream --format '{{.Name}} {{.CPUPerc}}' "$(scylla_cid)" ispf-lab-ispf-server-1 2>/dev/null || true
