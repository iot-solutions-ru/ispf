#!/usr/bin/env bash
# Multi-device event journal benchmark on VPS prod (Scylla + Mosquitto loadtest).
set -euo pipefail

DIR="${ISPF_LOADTEST_DIR:-/opt/ispf/loadtest}"
ENV_FILE="${ISPF_ENV_FILE:-/opt/ispf/ispf-server.env}"
VENV="${DIR}/venv/bin/python"
SETUP="${DIR}/setup-mqtt-event-journal-devices.py"
CLEANUP="${DIR}/vps-emqtt-cleanup.sh"

DEVICES="${DEVICES:-16}"
RATE_PER_DEVICE="${RATE_PER_DEVICE:-32000}"
EVENT="${EVENT:-messageReceived}"
PHASE="${PHASE:-60}"
WARMUP="${WARMUP:-20}"
INTERVAL_MS="${INTERVAL_MS:-1}"
BENCH_NO_L0_COALESCE="${BENCH_NO_L0_COALESCE:-true}"
SKIP_DEVICE_SETUP="${SKIP_DEVICE_SETUP:-false}"
CALLBACK_THREADS="${CALLBACK_THREADS:-64}"
CALLBACK_QUEUE_CAPACITY="${CALLBACK_QUEUE_CAPACITY:-500000}"
TOTAL_RATE=$((DEVICES * RATE_PER_DEVICE))
PUBLISH_SEC=$((WARMUP + PHASE + 10))
BASE_URL="${BASE_URL:-http://127.0.0.1:8080}"
MQTT_HOST="${MQTT_HOST:-127.0.0.1}"
MQTT_PORT="${MQTT_PORT:-1883}"
EMQTT_SHARD_MAX="${EMQTT_SHARD_MAX:-8}"
export EMQTT_CPU_LIMIT="${EMQTT_CPU_LIMIT:-1.5}"
SCYLLA_CONTAINER="${SCYLLA_CONTAINER:-ispf-scylla}"
MQTT_CONTAINER="${MQTT_CONTAINER:-ispf-mqtt-loadtest}"

if [ ! -x "$VENV" ]; then
  VENV=python3
fi

journal_store=scylla
if [ -f "$ENV_FILE" ]; then
  journal_store=$(grep '^ISPF_EVENT_JOURNAL_STORE=' "$ENV_FILE" 2>/dev/null | cut -d= -f2- || echo scylla)
fi

meta_total_count() {
  local cql_out
  cql_out=$(docker exec "$SCYLLA_CONTAINER" cqlsh -e \
    "SELECT total_count FROM ispf.event_journal_meta WHERE id='total';" 2>/dev/null || true)
  python3 -c "import re,sys; t=sys.stdin.read(); m=re.search(r'\\n\\s*(\\d+)\\s*\\n', t); print(m.group(1) if m else '0')" <<< "$cql_out"
}

mosquitto_sys_counter() {
  local topic=$1
  local val
  val=$(docker exec "$MQTT_CONTAINER" mosquitto_sub -h localhost -t "$topic" -C 1 -W 3 2>/dev/null \
    | tr -d '[:space:]' || true)
  if [[ "${val:-}" =~ ^[0-9]+$ ]]; then
    echo "$val"
  else
    echo 0
  fi
}

mosquitto_messages_received() { mosquitto_sys_counter '$SYS/broker/messages/received'; }
mosquitto_messages_sent() { mosquitto_sys_counter '$SYS/broker/messages/sent'; }

emqtt_formula_rate() {
  python3 -c "
import math
devices = int('$DEVICES')
total = int('$1')
interval_ms = int('${INTERVAL_MS}')
per_topic = total / max(devices, 1)
clients = max(1, int(math.ceil(per_topic * interval_ms / 1000.0)))
actual_per = clients * (1000.0 / interval_ms)
print(f'{actual_per * devices:.1f}')
"
}

parse_emqtt_log_formula_rate() {
  local log_file=$1
  if [ ! -f "$log_file" ]; then
    emqtt_formula_rate "$TOTAL_RATE"
    return 0
  fi
  local parsed
  parsed=$(grep -E '^ISPF_EMQTT_FORMULA_RATE=' "$log_file" 2>/dev/null | tail -1 | cut -d= -f2- || true)
  if [[ "${parsed:-}" =~ ^[0-9]+(\.[0-9]+)?$ ]]; then
    echo "$parsed"
  else
    emqtt_formula_rate "$TOTAL_RATE"
  fi
}

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

events_fired_total() { automation_metric eventsFiredTotal; }
journal_flushed_total() { automation_metric eventJournalFlushedTotal; }
journal_queue_size() { automation_metric eventJournalQueueSize; }

wait_journal_queue_drain() {
  local max_wait="${1:-120}"
  local deadline=$((SECONDS + max_wait))
  echo "Waiting for event-journal queue drain (max ${max_wait}s)..." >&2
  while [ "${SECONDS}" -lt "${deadline}" ]; do
    local q
    q=$(journal_queue_size)
    echo "  eventJournalQueueSize=${q}" >&2
    if [ "${q}" -le 1000 ]; then
      return 0
    fi
    sleep 5
  done
  echo "  queue still ${q} after ${max_wait}s; continuing" >&2
}

run_emqtt_publisher() {
  local total_rate=$1
  local pub_sec=$2
  local log_file="${DIR}/emqtt-pub-$$.log"
  bash "${DIR}/mqtt-emqtt-bench.sh" \
    --host "$MQTT_HOST" --port "$MQTT_PORT" \
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

print_throughput_report() {
  local phase_sec=$1 delta_meta=$2 delta_fired=$3 delta_flushed=$4
  local delta_mqtt_rx=$5 delta_mqtt_tx=$6 formula_rate=$7
  local emqtt_log=${8:-}

  local rate_meta rate_fired rate_flushed rate_mqtt_rx rate_mqtt_tx rate_per_device
  rate_meta=$(python3 -c "print(f'{$delta_meta / max($phase_sec, 1):.1f}')")
  rate_fired=$(python3 -c "print(f'{$delta_fired / max($phase_sec, 1):.1f}')")
  rate_flushed=$(python3 -c "print(f'{$delta_flushed / max($phase_sec, 1):.1f}')")
  rate_mqtt_rx=$(python3 -c "print(f'{$delta_mqtt_rx / max($phase_sec, 1):.1f}')")
  rate_mqtt_tx=$(python3 -c "print(f'{$delta_mqtt_tx / max($phase_sec, 1):.1f}')")
  rate_per_device=$(python3 -c "print(f'{$delta_meta / max($DEVICES * $phase_sec, 1):.1f}')")

  local eff_target eff_formula eff_broker eff_fired_broker eff_fired_sent
  eff_target=$(pct_efficiency "$delta_meta" "$((TOTAL_RATE * phase_sec))")
  eff_formula=$(pct_efficiency "$delta_meta" "$(python3 -c "print(int(float('$formula_rate') * $phase_sec))")")
  if [ "$delta_mqtt_rx" -gt 0 ]; then
    eff_broker=$(pct_efficiency "$delta_meta" "$delta_mqtt_rx")
    eff_fired_broker=$(pct_efficiency "$delta_fired" "$delta_mqtt_rx")
  else
    eff_broker="n/a"; eff_fired_broker="n/a"
  fi
  if [ "$delta_mqtt_tx" -gt 0 ]; then
    eff_fired_sent=$(pct_efficiency "$delta_fired" "$delta_mqtt_tx")
  else
    eff_fired_sent="n/a"
  fi

  echo ""
  echo "=== MQTT ${DEVICES} devices → internal event journal (VPS prod) ==="
  echo "Journal store: ${journal_store}"
  echo "Configured MQTT target: ${DEVICES} × ${RATE_PER_DEVICE} = ${TOTAL_RATE} msg/s"
  echo "emqtt formula estimate: ${formula_rate} msg/s (clients×interval math — not measured)"
  if [ -n "$emqtt_log" ] && [ -f "$emqtt_log" ]; then
    echo "emqtt log: ${emqtt_log}"
    grep -E '^emqtt-bench|^  configured|^Done \(formula|^ISPF_EMQTT_' "$emqtt_log" 2>/dev/null | sed 's/^/  /' || true
  fi
  echo "Measure phase: ${phase_sec}s"
  echo ""
  echo "--- Throughput (measured during phase) ---"
  if [ "$delta_mqtt_rx" -gt 0 ]; then
    echo "Mosquitto PUBLISH in (broker):  ${rate_mqtt_rx} msg/s  (delta ${delta_mqtt_rx})"
    if [ "$delta_mqtt_tx" -gt 0 ]; then
      echo "Mosquitto delivered (broker):   ${rate_mqtt_tx} msg/s  (delta ${delta_mqtt_tx})"
    fi
  else
    echo "Mosquitto counters: unavailable (add sys_interval 1 to mosquitto.conf; recreate mqtt)"
  fi
  echo "ISPF eventsFired (ingress):  ${rate_fired} events/s  (delta ${delta_fired})"
  echo "Journal flushed (metrics):   ${rate_flushed} events/s  (delta ${delta_flushed})"
  echo "Journal (Scylla meta):       ${rate_meta} events/s  (delta ${delta_meta})"
  echo "Journal per device (avg):    ${rate_per_device} events/s"
  echo ""
  echo "--- Efficiency ---"
  echo "Journal vs configured target: ${eff_target}%  (reference only)"
  echo "Journal vs emqtt formula:     ${eff_formula}%  (reference only)"
  if [ "$delta_mqtt_rx" -gt 0 ]; then
    echo "ISPF capture (fired/PUBLISH in): ${eff_fired_broker}%"
    if [ "$delta_mqtt_tx" -gt 0 ]; then
      echo "ISPF capture (fired/delivered): ${eff_fired_sent}%  (expect ~95–100%)"
    fi
    if [ "$delta_fired" -gt 0 ]; then
      local eff_meta_fired
      eff_meta_fired=$(pct_efficiency "$delta_meta" "$delta_fired")
      echo "Scylla meta vs eventsFired:   ${eff_meta_fired}%"
    fi
    echo "Journal capture (meta/broker): ${eff_broker}%"
  fi
}

curl -sf "${BASE_URL}/api/v1/info" | python3 -c "import json,sys; d=json.load(sys.stdin); print(f\"ISPF {d.get('version')} ({d.get('environment')})\")" || true

echo "Event journal store: ${journal_store}"
echo "API: ${BASE_URL}"
echo "MQTT: ${MQTT_HOST}:${MQTT_PORT}"
echo "Devices: ${DEVICES} × ~${RATE_PER_DEVICE} msg/s = ~${TOTAL_RATE} msg/s aggregate"
echo "Warmup ${WARMUP}s, measure ${PHASE}s"
echo "L0 coalesce off: ${BENCH_NO_L0_COALESCE} (callbackThreads=${CALLBACK_THREADS})"

if [ -f "$SETUP" ] && [ "$SKIP_DEVICE_SETUP" != "true" ]; then
  SETUP_ARGS=(--devices "$DEVICES" --telemetry-coalesce-ms 1 --base-url "$BASE_URL" --broker-url "tcp://127.0.0.1:1883")
  if [ "$BENCH_NO_L0_COALESCE" = "true" ]; then
    SETUP_ARGS+=(--bench-no-l0-coalesce --callback-threads "$CALLBACK_THREADS" --callback-queue-capacity "$CALLBACK_QUEUE_CAPACITY")
  fi
  "$VENV" "$SETUP" "${SETUP_ARGS[@]}"
fi

sleep 2
[ -f "$CLEANUP" ] && bash "$CLEANUP" || true

PUBLISH_SEC=$((WARMUP + PHASE + 10))
EMQTT_LOG_FILE=""
pub_pid=$(run_emqtt_publisher "$TOTAL_RATE" "$PUBLISH_SEC")

sleep "${WARMUP}"
start_count=$(meta_total_count)
start_fired=$(events_fired_total)
start_flushed=$(journal_flushed_total)
start_mqtt_rx=$(mosquitto_messages_received)
start_mqtt_tx=$(mosquitto_messages_sent)
echo "Measure start: journal=${start_count} eventsFired=${start_fired} flushed=${start_flushed} mosquitto_rx=${start_mqtt_rx} mosquitto_tx=${start_mqtt_tx}"
sleep "${PHASE}"
sleep 5

wait "${pub_pid}" 2>/dev/null || true
sleep 5
wait_journal_queue_drain 45

total=$(meta_total_count)
end_fired=$(events_fired_total)
end_flushed=$(journal_flushed_total)
end_mqtt_rx=$(mosquitto_messages_received)
end_mqtt_tx=$(mosquitto_messages_sent)

delta=$((total - start_count))
delta_fired=$((end_fired - start_fired))
delta_flushed=$((end_flushed - start_flushed))
delta_mqtt_rx=$((end_mqtt_rx - start_mqtt_rx))
delta_mqtt_tx=$((end_mqtt_tx - start_mqtt_tx))
[ "$delta_mqtt_rx" -lt 0 ] && delta_mqtt_rx=0
[ "$delta_mqtt_tx" -lt 0 ] && delta_mqtt_tx=0

if [ "$delta" -eq 0 ] && [ "$delta_fired" -gt 0 ]; then
  echo "Scylla meta counter lag; waiting 15s..." >&2
  sleep 15
  total=$(meta_total_count)
  delta=$((total - start_count))
fi

if [ "$delta_fired" -eq 0 ] && [ "$delta_mqtt_rx" -gt 1000 ]; then
  echo "WARNING: broker received messages but eventsFired=0 — re-seed drivers after mqtt recreate." >&2
fi

formula_rate=$(parse_emqtt_log_formula_rate "${EMQTT_LOG_FILE}")
print_throughput_report "$PHASE" "$delta" "$delta_fired" "$delta_flushed" "$delta_mqtt_rx" "$delta_mqtt_tx" "$formula_rate" "${EMQTT_LOG_FILE}"
echo "Total ${EVENT} in journal (global counter): ${total}"

docker stats --no-stream --format '{{.Name}} {{.CPUPerc}} {{.MemUsage}}' "$SCYLLA_CONTAINER" 2>/dev/null || true
systemctl show ispf-server -p ActiveState --value 2>/dev/null || true
