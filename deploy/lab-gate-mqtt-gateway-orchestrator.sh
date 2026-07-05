#!/usr/bin/env bash
# Gate: 1× MQTT gateway (wildcard subscribe) dispatches ingress to N child sensor instances.
# Publish N distinct topics at total rate R → expect historian ≈ R (1:1 dispatch, not N× fan-out).
set -euo pipefail

LAB_ROOT="${ISPF_LAB_ROOT:-$HOME/ispf}"
COMPOSE_FILE="$LAB_ROOT/lab-test-host-compose.yml"
ENV_FILE="$LAB_ROOT/lab-stress.env"
HTTP_PORT="${ISPF_LAB_HTTP_PORT:-8000}"
DIR="$LAB_ROOT/loadtest"
VENV="${DIR}/venv/bin/python"
SETUP="${DIR}/setup-mqtt-gateway-orchestrator-devices.py"
BASE_URL="http://127.0.0.1:${HTTP_PORT}"
LOG="${LOG:-$LAB_ROOT/loadtest/gate-mqtt-gateway-orchestrator.log}"

DEVICES="${DEVICES:-16}"
PUBLISH_RATE="${PUBLISH_RATE:-10000}"
WARMUP="${WARMUP:-20}"
PHASE="${PHASE:-20}"
INTERVAL_MS="${INTERVAL_MS:-1}"
EMQTT_CPU_LIMIT="${EMQTT_CPU_LIMIT:-2.0}"
SHARD_MAX="${SHARD_MAX:-4}"
LAZY_INSTANCES="${LAZY_INSTANCES:-false}"
PARALLEL_WORKERS="${PARALLEL_WORKERS:-0}"
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

exec > >(tee "$LOG") 2>&1
echo "=== GATE mqtt-gateway orchestrator ${DEVICES} child sensors publish=${PUBLISH_RATE}/s $(date -Is) ==="

TOKEN=$(curl -sf -X POST "${BASE_URL}/api/v1/auth/login" \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin"}' \
  | python3 -c "import json,sys; print(json.load(sys.stdin)['token'])")

platform_metric() {
  local section=$1
  local key=$2
  curl -sf "${BASE_URL}/api/v1/platform/metrics" -H "Authorization: Bearer ${TOKEN}" \
    | python3 -c "
import json, sys
section, key = sys.argv[1], sys.argv[2]
for s in json.load(sys.stdin).get('sections', []):
    if s.get('id') == section:
        print(int(s.get('values', {}).get(key, 0) or 0))
        break
else:
    print(0)
" "$section" "$key"
}

historian_sample_count() { platform_metric variableHistory sampleCount; }
historian_flushed_total() { platform_metric variableHistory flushedTotal; }

mqtt_container_id() {
  docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" ps -q mqtt 2>/dev/null | head -1
}

broker_messages_received_total() {
  local cid
  cid=$(mqtt_container_id)
  if [ -z "$cid" ]; then echo 0; return; fi
  docker exec "$cid" mosquitto_sub -h localhost -t '$SYS/broker/messages/received' -C 1 -W 5 2>/dev/null \
    | tr -d '\r' | awk '{print $NF}' | tail -1
}

count_running_mqtt_drivers() {
  if echo "$gw_status" | python3 -c "import json,sys; d=json.load(sys.stdin); exit(0 if d.get('driverId')=='mqtt' and d.get('status')=='RUNNING' and d.get('connected') else 1)" 2>/dev/null; then
    echo 1
  else
    echo 0
  fi
}

bash "$LAB_ROOT/lab-emqtt-cleanup.sh" 2>/dev/null || true
docker ps -q --filter "label=ispf.emqtt-bench=1" 2>/dev/null | xargs -r docker stop >/dev/null 2>&1 || true

cd "$LAB_ROOT"

if [ "$SKIP_DEVICE_SETUP" != "true" ] && [ -f "$SETUP" ]; then
  echo "Seeding mqtt-gateway + ${DEVICES} child sensors..."
  setup_args=(
    --devices "$DEVICES"
    --telemetry-coalesce-ms 1
    --base-url "$BASE_URL"
    --broker-url "tcp://${MQTT_HOST}:${MQTT_PORT}"
  )
  if [[ "$PARALLEL_WORKERS" -gt 0 ]]; then
    setup_args+=(--parallel-workers "$PARALLEL_WORKERS")
  fi
  if [[ "$LAZY_INSTANCES" == "true" ]] || [[ "$DEVICES" -ge 500 ]]; then
    setup_args+=(--lazy-instances)
  fi
  "$VENV" "$SETUP" "${setup_args[@]}"
  sleep 8
fi

GW_PATH="root.platform.devices.loadtest-mqtt-gateway"
gw_status=$(curl -sf "${BASE_URL}/api/v1/drivers/runtime/status?devicePath=${GW_PATH}" \
  -H "Authorization: Bearer ${TOKEN}" || echo '{}')
echo "gateway driver: $(echo "$gw_status" | python3 -c "import json,sys; d=json.load(sys.stdin); print(d.get('status'), 'connected=', d.get('connected'), 'driverId=', d.get('driverId'))")"
mqtt_running=$(count_running_mqtt_drivers)
echo "running mqtt drivers (loadtest tree): ${mqtt_running} (expect 1 gateway only)"

before_samples=$(historian_sample_count)
before_flushed=$(historian_flushed_total)
before_broker=$(broker_messages_received_total)
before_broker=${before_broker:-0}
before_queue=$(platform_metric automation eventJournalQueueSize)
echo "before: historianSamples=${before_samples} historianFlushed=${before_flushed} brokerRx=${before_broker} eventJournalQueue=${before_queue}"

pub_sec=$((WARMUP + PHASE + 15))
GATEWAY_NUMERIC_TIMESTAMP=true EMQTT_CPU_LIMIT="$EMQTT_CPU_LIMIT" \
  bash "${DIR}/mqtt-emqtt-bench.sh" \
  --host "$MQTT_HOST" --port "$MQTT_PORT" \
  --docker-network "$EMQTT_DOCKER_NETWORK" \
  --devices "$DEVICES" \
  --messages-per-second "$PUBLISH_RATE" \
  --duration-seconds "$pub_sec" \
  --interval-ms "$INTERVAL_MS" \
  --single-container \
  --shard-max "$SHARD_MAX" &
pub_pid=$!
pub_pid=$!

sleep "$WARMUP"
start_samples=$(historian_sample_count)
start_flushed=$(historian_flushed_total)
start_broker=$(broker_messages_received_total)
start_broker=${start_broker:-0}
measure_start=$SECONDS
sleep "$PHASE"
measure_sec=$((SECONDS - measure_start))
wait "$pub_pid" 2>/dev/null || true
sleep 5

end_samples=$(historian_sample_count)
end_flushed=$(historian_flushed_total)
end_broker=$(broker_messages_received_total)
end_broker=${end_broker:-0}
after_queue=$(platform_metric automation eventJournalQueueSize)
delta_samples=$((end_samples - start_samples))
delta_flushed=$((end_flushed - start_flushed))
delta_broker=$((end_broker - start_broker))
if [ "${delta_broker:-0}" -lt 0 ] 2>/dev/null; then delta_broker=0; fi
rate_samples=$(awk -v d="$delta_samples" -v t="$measure_sec" 'BEGIN { if (t > 0) printf "%.1f", d/t; else print "0.0" }')
rate_flushed=$(awk -v d="$delta_flushed" -v t="$measure_sec" 'BEGIN { if (t > 0) printf "%.1f", d/t; else print "0.0" }')
rate_broker=$(awk -v d="$delta_broker" -v t="$measure_sec" 'BEGIN { if (t > 0) printf "%.1f", d/t; else print "0.0" }')
dispatch_ratio=$(awk -v h="$rate_flushed" -v b="$rate_broker" 'BEGIN { if (b > 0) printf "%.2f", h/b; else print "0.0" }')
per_child=$(awk -v h="$rate_flushed" -v n="$DEVICES" 'BEGIN { if (n > 0) printf "%.1f", h/n; else print "0.0" }')

echo "RESULT gateway-orchestrator devices=${DEVICES} publish_target=${PUBLISH_RATE}/s measure=${measure_sec}s"
echo "  broker_rx_per_sec=${rate_broker} (Mosquitto \$SYS; 1 MQTT subscription on gateway)"
echo "  historian_flushed_per_sec=${rate_flushed} (~${per_child}/child) sampleCount_delta=${delta_samples} (${rate_samples}/s)"
echo "  dispatch_ratio=${dispatch_ratio} (historian_flushed/broker; ideal≈1.0 for orchestrator)"
echo "  mqtt_drivers_running=${mqtt_running} eventJournalQueueSize=${after_queue}"
echo "GATE_ROW|gateway|${DEVICES}|${PUBLISH_RATE}|${measure_sec}|${delta_flushed}|${rate_flushed}|${rate_broker}|${dispatch_ratio}|${mqtt_running}|${after_queue}"

docker stats --no-stream --format '  {{.Name}} cpu={{.CPUPerc}} mem={{.MemUsage}}' \
  "$(docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" ps -q ispf-server 2>/dev/null)" \
  "$(docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" ps -q scylla 2>/dev/null)" 2>/dev/null || true
echo "=== DONE gateway orchestrator gate $(date -Is) ==="
