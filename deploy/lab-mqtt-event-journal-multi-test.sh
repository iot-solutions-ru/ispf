#!/usr/bin/env bash
# Multi-device event journal benchmark for ~/ispf Docker lab.
set -euo pipefail

LAB_ROOT="${ISPF_LAB_ROOT:-$HOME/ispf}"
COMPOSE_FILE="$LAB_ROOT/lab-test-host-compose.yml"
HTTP_PORT="${ISPF_LAB_HTTP_PORT:-8000}"
DIR="$LAB_ROOT/loadtest"
VENV="${DIR}/venv/bin/python"
SETUP="${DIR}/setup-mqtt-event-journal-devices.py"

DEVICES="${DEVICES:-8}"
RATE_PER_DEVICE="${RATE_PER_DEVICE:-2000}"
EVENT="${EVENT:-messageReceived}"
PHASE="${PHASE:-60}"
WARMUP="${WARMUP:-15}"
INTERVAL_MS="${INTERVAL_MS:-1}"
BENCH_NO_L0_COALESCE="${BENCH_NO_L0_COALESCE:-true}"
AUTO_CALIBRATE="${AUTO_CALIBRATE:-false}"
CALIBRATE_RUN_MAIN="${CALIBRATE_RUN_MAIN:-false}"
SKIP_DEVICE_SETUP="${SKIP_DEVICE_SETUP:-false}"
CALIBRATE_WARMUP="${CALIBRATE_WARMUP:-20}"
CALIBRATE_PHASE="${CALIBRATE_PHASE:-30}"
CALIBRATE_MARGIN="${CALIBRATE_MARGIN:-1.0}"
CALIBRATE_COOLDOWN="${CALIBRATE_COOLDOWN:-90}"
CALLBACK_THREADS="${CALLBACK_THREADS:-${ISPF_DRIVER_MQTT_CALLBACK_THREADS:-}}"
CALLBACK_QUEUE_CAPACITY="${CALLBACK_QUEUE_CAPACITY:-${ISPF_DRIVER_MQTT_CALLBACK_QUEUE_CAPACITY:-}}"
TOTAL_RATE=$((DEVICES * RATE_PER_DEVICE))
PUBLISH_SEC=$((WARMUP + PHASE + 10))
BASE_URL="http://127.0.0.1:${HTTP_PORT}"
# Publisher on compose network — no docker-proxy, one emqtt container.
MQTT_HOST="${MQTT_HOST:-mqtt}"
MQTT_PORT="${MQTT_PORT:-1883}"
EMQTT_DOCKER_NETWORK="${EMQTT_DOCKER_NETWORK:-ispf-lab_default}"
EMQTT_SINGLE_CONTAINER="${EMQTT_SINGLE_CONTAINER:-true}"
EMQTT_SHARD_MAX="${EMQTT_SHARD_MAX:-4}"
export EMQTT_CPU_LIMIT="${EMQTT_CPU_LIMIT:-1.5}"

if [ ! -x "$VENV" ]; then
  VENV=python3
fi

scylla_cid() {
  docker compose -f "$COMPOSE_FILE" ps -q scylla
}

mqtt_cid() {
  docker compose -f "$COMPOSE_FILE" ps -q mqtt 2>/dev/null || true
}

meta_total_count() {
  local cql_out
  cql_out=$(docker exec "$(scylla_cid)" cqlsh -e \
    "SELECT total_count FROM ispf.event_journal_meta WHERE id='total';" 2>/dev/null || true)
  python3 -c "import re,sys; t=sys.stdin.read(); m=re.search(r'\\n\\s*(\\d+)\\s*\\n', t); print(m.group(1) if m else '0')" <<< "$cql_out"
}

mosquitto_sys_counter() {
  local topic=$1
  local cid val
  cid=$(mqtt_cid)
  if [ -z "$cid" ]; then
    echo 0
    return 0
  fi
  val=$(docker exec "$cid" mosquitto_sub -h localhost -t "$topic" -C 1 -W 3 2>/dev/null \
    | tr -d '[:space:]' || true)
  if [[ "${val:-}" =~ ^[0-9]+$ ]]; then
    echo "$val"
  else
    echo 0
  fi
}

mosquitto_messages_received() {
  mosquitto_sys_counter '$SYS/broker/messages/received'
}

mosquitto_messages_sent() {
  mosquitto_sys_counter '$SYS/broker/messages/sent'
}

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

journal_flushed_total() {
  automation_metric eventJournalFlushedTotal
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

events_fired_total() {
  automation_metric eventsFiredTotal
}

journal_queue_size() {
  automation_metric eventJournalQueueSize
}

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

print_throughput_report() {
  local phase_sec=$1
  local delta_meta=$2
  local delta_fired=$3
  local delta_flushed=$4
  local delta_mqtt_rx=$5
  local delta_mqtt_tx=$6
  local formula_rate=$7
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
    eff_broker="n/a"
    eff_fired_broker="n/a"
  fi
  if [ "$delta_mqtt_tx" -gt 0 ]; then
    eff_fired_sent=$(pct_efficiency "$delta_fired" "$delta_mqtt_tx")
  else
    eff_fired_sent="n/a"
  fi

  echo ""
  echo "=== MQTT ${DEVICES} devices → internal event journal (lab) ==="
  if [ "$AUTO_CALIBRATE" = "true" ] && [ -n "${PROBE_SUSTAINED:-}" ]; then
    echo "Probe target: ${DEVICES} × $((PROBE_TOTAL_RATE / DEVICES)) msg/s = ${PROBE_TOTAL_RATE} msg/s (sustained ${PROBE_SUSTAINED}/s)"
  fi
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
    echo "Mosquitto PUBLISH in (broker):  ${rate_mqtt_rx} msg/s  (delta ${delta_mqtt_rx}; all publisher clients)"
    if [ "$delta_mqtt_tx" -gt 0 ]; then
      echo "Mosquitto delivered (broker):   ${rate_mqtt_tx} msg/s  (delta ${delta_mqtt_tx}; to subscribers)"
    fi
  else
    echo "Mosquitto counters: unavailable (enable sys_interval in mosquitto.conf; recreate mqtt container)"
  fi
  echo "ISPF eventsFired (ingress):  ${rate_fired} events/s  (delta ${delta_fired})"
  echo "Journal flushed (metrics):   ${rate_flushed} events/s  (delta ${delta_flushed})"
  echo "Journal (Scylla meta):       ${rate_meta} events/s  (delta ${delta_meta}; may lag if asyncCounter enabled)"
  echo "Journal per device (avg):    ${rate_per_device} events/s  (from Scylla meta)"
  echo ""
  echo "--- Efficiency ---"
  echo "Journal vs configured target: ${eff_target}%  (reference only — emqtt often CPU-limited)"
  echo "Journal vs emqtt formula:     ${eff_formula}%  (reference only — formula overstates publish rate)"
  if [ "$delta_mqtt_rx" -gt 0 ]; then
    echo "ISPF capture (fired/PUBLISH in): ${eff_fired_broker}%  (may be <100%: QoS0 + many emqtt clients)"
    if [ "$delta_mqtt_tx" -gt 0 ]; then
      echo "ISPF capture (fired/delivered): ${eff_fired_sent}%  (subscriber delivery → ISPF; expect ~95–100%)"
    fi
    if [ "$delta_fired" -gt 0 ]; then
      local eff_meta_fired
      eff_meta_fired=$(pct_efficiency "$delta_meta" "$delta_fired")
      echo "Scylla meta vs eventsFired:   ${eff_meta_fired}%  (counter lag indicator; expect ~100% after drain)"
    fi
    echo "Journal capture (meta/broker): ${eff_broker}%  (broker → Scylla meta; use eventsFired if meta lags)"
  fi
  if [ "$AUTO_CALIBRATE" = "true" ]; then
    echo "Sustained calibrated target: ${TOTAL_RATE} msg/s configured ≈ ${rate_meta} events/s journal"
  fi
}

measure_journal_events_per_sec() {
  local total_rate=$1
  local warmup_sec=$2
  local measure_sec=$3
  local pub_sec=$((warmup_sec + measure_sec + 15))
  local pub_pid
  pub_pid=$(run_emqtt_publisher "$total_rate" "$pub_sec")
  sleep "${warmup_sec}"
  local start_count
  start_count=$(meta_total_count)
  echo "Measure start (journal total): ${start_count}" >&2
  sleep "${measure_sec}"
  wait "${pub_pid}" 2>/dev/null || true
  sleep 5
  local end_count
  end_count=$(meta_total_count)
  local delta=$((end_count - start_count))
  python3 -c "print(f'{$delta / max($measure_sec, 1):.1f}')"
}

echo "Event journal store: scylla (lab)"
echo "API: ${BASE_URL}"
echo "MQTT: ${MQTT_HOST}:${MQTT_PORT}"
echo "Devices: ${DEVICES} × ~${RATE_PER_DEVICE} msg/s = ~${TOTAL_RATE} msg/s aggregate"
echo "Warmup ${WARMUP}s, measure ${PHASE}s"
echo "L0 coalesce: ${BENCH_NO_L0_COALESCE} (callbackThreads=${CALLBACK_THREADS:-platform}, callbackQueue=${CALLBACK_QUEUE_CAPACITY:-platform})"

# Device setup uses broker inside Docker network (ispf-server container).
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
  else
    SETUP_ARGS+=(--no-bench-no-l0-coalesce)
  fi
  "$VENV" "$SETUP" "${SETUP_ARGS[@]}"
fi

sleep 2

bash "${LAB_ROOT}/lab-emqtt-cleanup.sh" 2>/dev/null || true

PROBE_TOTAL_RATE="${PROBE_TOTAL_RATE:-$(( DEVICES * 6000 ))}"
if [ "$AUTO_CALIBRATE" = "true" ] && [ "${PROBE_TOTAL_RATE}" -gt "${TOTAL_RATE}" ]; then
  PROBE_TOTAL_RATE=$TOTAL_RATE
fi
if [ "$AUTO_CALIBRATE" = "true" ]; then
  echo ""
  echo "=== Auto-calibrate: probe at ${PROBE_TOTAL_RATE} msg/s (${CALIBRATE_WARMUP}s warmup + ${CALIBRATE_PHASE}s measure) ==="
  PROBE_SUSTAINED=$(measure_journal_events_per_sec "$PROBE_TOTAL_RATE" "$CALIBRATE_WARMUP" "$CALIBRATE_PHASE")
  CALIBRATED_TOTAL=$(python3 -c "print(max(1, int(float('$PROBE_SUSTAINED') * float('$CALIBRATE_MARGIN'))))")
  RATE_PER_DEVICE=$(( CALIBRATED_TOTAL / DEVICES ))
  if [ "${RATE_PER_DEVICE}" -lt 1 ]; then
    RATE_PER_DEVICE=1
  fi
  TOTAL_RATE=$((DEVICES * RATE_PER_DEVICE))
  echo "Probe sustained journal: ${PROBE_SUSTAINED} events/s"
  echo "Calibrated MQTT target: ${DEVICES} × ${RATE_PER_DEVICE} = ${TOTAL_RATE} msg/s (margin ${CALIBRATE_MARGIN})"
  echo ""
  echo "Re-run sustained test (after cooldown):"
  echo "  RATE_PER_DEVICE=${RATE_PER_DEVICE} DEVICES=${DEVICES} AUTO_CALIBRATE=false SKIP_DEVICE_SETUP=true \\"
  echo "    bash lab-mqtt-event-journal-multi-test.sh"
  bash "${LAB_ROOT}/lab-emqtt-cleanup.sh" 2>/dev/null || true
  if [ "$CALIBRATE_RUN_MAIN" != "true" ]; then
    echo ""
    echo "=== Calibration complete (main phase skipped; set CALIBRATE_RUN_MAIN=true to run inline) ==="
    exit 0
  fi
  sleep 3
  wait_journal_queue_drain "${CALIBRATE_COOLDOWN}"
  sleep 5
fi

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
if [ "$delta_mqtt_rx" -lt 0 ]; then
  delta_mqtt_rx=0
fi
if [ "$delta_mqtt_tx" -lt 0 ]; then
  delta_mqtt_tx=0
fi

# Scylla event_journal_meta counter may lag behind eventsFired when asyncCounter is enabled.
if [ "$delta" -eq 0 ] && [ "$delta_fired" -gt 0 ]; then
  echo "Scylla meta counter lag; waiting 15s for async counter..." >&2
  sleep 15
  total=$(meta_total_count)
  delta=$((total - start_count))
fi

if [ "$delta_fired" -eq 0 ] && [ "$delta_mqtt_rx" -gt 1000 ]; then
  echo "WARNING: broker received ${delta_mqtt_rx} messages but ISPF eventsFired=0." >&2
  echo "  MQTT drivers may be disconnected (e.g. after mqtt container recreate). Re-seed or restart drivers." >&2
fi

formula_rate=$(parse_emqtt_log_formula_rate "${EMQTT_LOG_FILE}")
print_throughput_report "$PHASE" "$delta" "$delta_fired" "$delta_flushed" "$delta_mqtt_rx" "$delta_mqtt_tx" "$formula_rate" "${EMQTT_LOG_FILE}"
echo "Total ${EVENT} in journal (global counter): ${total}"
