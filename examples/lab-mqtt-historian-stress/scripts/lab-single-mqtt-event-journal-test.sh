#!/usr/bin/env bash
# I-03 single-node split lab: MQTT EVENT_JOURNAL_ONLY → Scylla event_history.
# Publisher + Mosquitto on loadgen; ISPF on app host.
# PROFILE=smoke (default) | peak (16×32k, 8 emqtt shards — see lab-run-event-journal-peak.sh)
set -euo pipefail

LAB_ROOT="${ISPF_LAB_ROOT:-$HOME/ispf}"
DIR="${LAB_ROOT}/loadtest"
SETUP="${DIR}/setup-mqtt-event-journal-devices.py"
REMOTE_EMQTT="${LAB_ROOT}/lab-emqtt-remote.sh"
# shellcheck source=lab-loadgen-common.sh
source "${LAB_ROOT}/lab-loadgen-common.sh" 2>/dev/null || true

PROFILE="${PROFILE:-smoke}"
if [ "${PROFILE}" = "peak" ]; then
  DEVICES="${DEVICES:-16}"
  RATE_PER_DEVICE="${RATE_PER_DEVICE:-32000}"
  PHASE="${PHASE:-60}"
  WARMUP="${WARMUP:-20}"
  INTERVAL_MS="${INTERVAL_MS:-1}"
  EMQTT_SHARD_MAX="${EMQTT_SHARD_MAX:-8}"
  STABILIZE_SEC="${STABILIZE_SEC:-90}"
  MIN_RATE_EVENTS="${MIN_RATE_EVENTS:-80000}"
  MIN_DRIVERS_RATIO="${MIN_DRIVERS_RATIO:-1.0}"
  WAIT_QUEUE_DRAIN_SEC="${WAIT_QUEUE_DRAIN_SEC:-180}"
  CALLBACK_THREADS="${CALLBACK_THREADS:-64}"
else
  DEVICES="${DEVICES:-4}"
  RATE_PER_DEVICE="${RATE_PER_DEVICE:-500}"
  PHASE="${PHASE:-60}"
  WARMUP="${WARMUP:-20}"
  INTERVAL_MS="${INTERVAL_MS:-10}"
  EMQTT_SHARD_MAX="${EMQTT_SHARD_MAX:-2}"
  STABILIZE_SEC="${STABILIZE_SEC:-15}"
  MIN_RATE_EVENTS="${MIN_RATE_EVENTS:-50}"
  MIN_DRIVERS_RATIO="${MIN_DRIVERS_RATIO:-0.5}"
  WAIT_QUEUE_DRAIN_SEC="${WAIT_QUEUE_DRAIN_SEC:-90}"
  CALLBACK_THREADS="${CALLBACK_THREADS:-}"
fi

BENCH_NO_L0_COALESCE="${BENCH_NO_L0_COALESCE:-true}"
SKIP_DEVICE_SETUP="${SKIP_DEVICE_SETUP:-false}"
SHARED_TOPIC="${SHARED_TOPIC:-}"
# Fan-out: one MQTT publish → N drivers on same topic → N journal events.
if [ -n "${SHARED_TOPIC}" ]; then
  MQTT_PUBLISH_RATE="${MQTT_PUBLISH_RATE:-$(( (MIN_RATE_EVENTS + DEVICES - 1) / DEVICES ))}"
  TOTAL_RATE="${MQTT_PUBLISH_RATE}"
else
  TOTAL_RATE=$((DEVICES * RATE_PER_DEVICE))
fi
HTTP_PORT="${ISPF_LAB_HTTP_PORT:-8000}"
BASE_URL="http://127.0.0.1:${HTTP_PORT}"
MQTT_HOST="${MQTT_HOST:-${ISPF_MQTT_BROKER_HOST:-198.51.100.10}}"
MQTT_PORT="${MQTT_PORT:-${ISPF_MQTT_BROKER_PORT:-1883}}"

if [ ! -x "${REMOTE_EMQTT}" ]; then
  echo "ERROR: missing ${REMOTE_EMQTT}" >&2
  exit 2
fi

scylla_cqlsh() {
  local cql=$1
  ssh -o BatchMode=yes "${ISPF_LAB_DB_SSH}" \
    "docker exec ${ISPF_LAB_DB_SCYLLA_CONTAINER} cqlsh -e \"${cql}\"" 2>/dev/null || true
}

scylla_count() {
  local table=$1 cql_out
  cql_out=$(scylla_cqlsh "SELECT COUNT(*) FROM ispf.${table};")
  python3 -c "import re,sys; t=sys.stdin.read(); m=re.search(r'\\n\\s*(\\d+)\\s*\\n', t); print(m.group(1) if m else '0')" <<< "$cql_out"
}

scylla_samples() { scylla_count variable_samples; }
scylla_events() { scylla_count event_history; }

mosquitto_sys_counter() {
  local topic=$1 val
  val=$(ssh -o BatchMode=yes -o ConnectTimeout=10 "${ISPF_LAB_LOADGEN_SSH}" \
    "docker exec ${LOADGEN_MQTT_CONTAINER} mosquitto_sub -h localhost -t '${topic}' -C 1 -W 3" \
    2>/dev/null | tr -d '[:space:]' || true)
  [[ "${val:-}" =~ ^[0-9]+$ ]] && echo "$val" || echo 0
}

AUTH_TOKEN=""
ensure_api_token() {
  [ -n "${AUTH_TOKEN:-}" ] && return 0
  for _ in $(seq 1 15); do
    AUTH_TOKEN=$(
      curl -sf -X POST "${BASE_URL}/api/v1/auth/login" \
        -H 'Content-Type: application/json' \
        -d '{"username":"admin","password":"admin"}' \
        | python3 -c "import json,sys; d=json.load(sys.stdin); print(d.get('token',''))" 2>/dev/null || true
    )
    [ -n "${AUTH_TOKEN:-}" ] && return 0
    sleep 2
  done
  return 1
}

metric_val() {
  local key=$1
  if ! ensure_api_token; then echo 0; return 0; fi
  curl -sf "${BASE_URL}/api/v1/platform/metrics" -H "Authorization: Bearer ${AUTH_TOKEN}" \
    | python3 -c "
import json, sys
key = sys.argv[1]
try:
    d = json.loads(sys.stdin.read() or '{}')
except Exception:
    print(0); raise SystemExit
for section in d.get('sections', []):
    if section.get('id') == 'automation':
        v = section.get('values', {}).get(key, 0)
        print(int(v) if isinstance(v, (int, float)) and float(v).is_integer() else v)
        break
else:
    print(0)
" "$key" 2>/dev/null || echo 0
}

events_fired_total() { metric_val eventsFiredTotal; }
journal_flushed_total() { metric_val eventJournalFlushedTotal; }
journal_sync_fallback_total() { metric_val eventJournalSyncFallbackTotal; }
journal_queue_size() { metric_val eventJournalQueueSize; }

wait_journal_queue_drain() {
  local max_wait="${1:-90}"
  local deadline=$((SECONDS + max_wait))
  while [ "${SECONDS}" -lt "${deadline}" ]; do
    local q
    q=$(journal_queue_size)
    echo "  eventJournalQueueSize=${q}" >&2
    [ "${q}" -le 1000 ] && return 0
    sleep 5
  done
  return 1
}

run_emqtt_publisher() {
  local total_rate=$1 pub_sec=$2
  local log_file="${DIR}/emqtt-pub-single-journal-${PROFILE}-$$.log"
  local emqtt_args=(
    --single-container --shard-max "$EMQTT_SHARD_MAX"
    --messages-per-second "$total_rate"
    --duration-seconds "$pub_sec"
    --interval-ms "${INTERVAL_MS}"
  )
  if [ -n "${SHARED_TOPIC}" ]; then
    emqtt_args+=(--topic "${SHARED_TOPIC}" --devices 1)
    if [ "${SHARED_TOPIC_SHARDS:-1}" -gt 1 ]; then
      emqtt_args+=(--shared-topic-shards "${SHARED_TOPIC_SHARDS}")
    fi
  else
    emqtt_args+=(--devices "$DEVICES")
  fi
  bash "${REMOTE_EMQTT}" -- "${emqtt_args[@]}" >"${log_file}" 2>&1 &
  echo "${log_file}" >&2
  echo $!
}

formula_emqtt_rate() {
  python3 -c "
devices=${DEVICES}
rate=${RATE_PER_DEVICE}
interval_ms=${INTERVAL_MS}
import math
cpt = max(1, math.ceil(rate * interval_ms / 1000))
print(int(devices * cpt * (1000 / interval_ms)))
"
}

echo "=== I-03 MQTT event journal (profile=${PROFILE}, split lab, Scylla) ==="
curl -sf "${BASE_URL}/api/v1/info" | python3 -c "import json,sys; d=json.load(sys.stdin); print(f\"ISPF {d.get('version')} env={d.get('environment')}\")" || true
echo "API: ${BASE_URL}"
echo "MQTT broker: ${MQTT_HOST}:${MQTT_PORT} (loadgen ${ISPF_LAB_LOADGEN_HOST})"
echo "Publisher: emqtt on ${ISPF_LAB_LOADGEN_SSH} (shards=${EMQTT_SHARD_MAX}, interval=${INTERVAL_MS}ms)"
if [ -n "${SHARED_TOPIC}" ]; then
  echo "Fan-out: ${DEVICES} subscribers on ${SHARED_TOPIC}"
  echo "MQTT publish target: ${TOTAL_RATE} msg/s → ~$(( TOTAL_RATE * DEVICES )) events/s (fan-out)"
else
  echo "Devices: ${DEVICES} × ${RATE_PER_DEVICE} msg/s target = ${TOTAL_RATE} msg/s (formula ~$(formula_emqtt_rate) msg/s)"
fi
echo "Warmup ${WARMUP}s, measure ${PHASE}s, stabilize ${STABILIZE_SEC}s"
echo "PASS threshold: >= ${MIN_RATE_EVENTS} events/s (eventsFired delta)"

if [ "${SKIP_DEVICE_SETUP}" != "true" ]; then
  echo "==> Seed devices (EVENT_JOURNAL_ONLY)"
  setup_args=(
    --devices "${DEVICES}"
    --telemetry-coalesce-ms 1
    --base-url "${BASE_URL}"
    --broker-url "tcp://${MQTT_HOST}:${MQTT_PORT}"
    --start-batch-size 4
    --start-batch-pause-s 2
  )
  if [ "${BENCH_NO_L0_COALESCE}" = "true" ]; then
    setup_args+=(--bench-no-l0-coalesce)
  fi
  if [ -n "${CALLBACK_THREADS:-}" ]; then
    setup_args+=(--callback-threads "${CALLBACK_THREADS}")
  fi
  if [ -n "${SHARED_TOPIC}" ]; then
    setup_args+=(--shared-topic "${SHARED_TOPIC}")
  fi
  if ! python3 "${SETUP}" "${setup_args[@]}"; then
    echo "FAIL: device seed/start" >&2
    exit 1
  fi
else
  echo "==> Skip device setup (SKIP_DEVICE_SETUP=true)"
fi

echo "==> Stabilize drivers (${STABILIZE_SEC}s)"
sleep "${STABILIZE_SEC}"
bash "${LAB_ROOT}/lab-emqtt-cleanup-remote.sh" 2>/dev/null || true

PUBLISH_SEC=$((WARMUP + PHASE + 15))
pub_pid=$(run_emqtt_publisher "$TOTAL_RATE" "$PUBLISH_SEC")

sleep "${WARMUP}"
start_events=$(scylla_events)
start_samples=$(scylla_samples)
start_fired=$(events_fired_total)
start_flushed=$(journal_flushed_total)
start_fallback=$(journal_sync_fallback_total)
start_mqtt_rx=$(mosquitto_sys_counter '$SYS/broker/messages/received')
start_mqtt_del=$(mosquitto_sys_counter '$SYS/broker/messages/sent')
echo "Measure start: eventsFired=${start_fired} flushed=${start_flushed} scylla_events=${start_events} mosquitto_rx=${start_mqtt_rx}"
sleep "${PHASE}"
sleep 5

wait "${pub_pid}" 2>/dev/null || true
sleep 5
wait_journal_queue_drain "${WAIT_QUEUE_DRAIN_SEC}" || echo "WARN: journal queue still elevated" >&2

end_events=$(scylla_events)
end_samples=$(scylla_samples)
end_fired=$(events_fired_total)
end_flushed=$(journal_flushed_total)
end_fallback=$(journal_sync_fallback_total)
end_mqtt_rx=$(mosquitto_sys_counter '$SYS/broker/messages/received')
end_mqtt_del=$(mosquitto_sys_counter '$SYS/broker/messages/sent')

delta_events=$((end_events - start_events))
delta_fired=$((end_fired - start_fired))
delta_flushed=$((end_flushed - start_flushed))
delta_fallback=$((end_fallback - start_fallback))
delta_mqtt_rx=$((end_mqtt_rx - start_mqtt_rx))
delta_mqtt_del=$((end_mqtt_del - start_mqtt_del))
for v in delta_events delta_fired delta_flushed delta_mqtt_rx delta_mqtt_del; do
  [ "${!v}" -lt 0 ] && eval "$v=0"
done

rate_events=$(python3 -c "print(f'{$delta_events / max($PHASE, 1):.1f}')")
rate_fired=$(python3 -c "print(f'{$delta_fired / max($PHASE, 1):.1f}')")
rate_flushed=$(python3 -c "print(f'{$delta_flushed / max($PHASE, 1):.1f}')")
rate_mqtt_rx=$(python3 -c "print(f'{$delta_mqtt_rx / max($PHASE, 1):.1f}')")
rate_mqtt_del=$(python3 -c "print(f'{$delta_mqtt_del / max($PHASE, 1):.1f}')")
per_device=$(python3 -c "print(f'{$delta_fired / max($PHASE * max($DEVICES, 1), 1):.1f}')")
capture_pct=$(python3 -c "d=${delta_mqtt_del}; f=${delta_fired}; print(f'{(f/d*100):.1f}' if d>0 else 'n/a')")
flush_pct=$(python3 -c "f=${delta_fired}; fl=${delta_flushed}; print(f'{(fl/f*100):.1f}' if f>0 else 'n/a')")

echo ""
echo "=== I-03 results (profile=${PROFILE}) ==="
echo "eventsFiredTotal delta:       ${delta_fired} (${rate_fired} events/s, ${per_device}/device/s)"
echo "eventJournalFlushed delta:    ${delta_flushed} (${rate_flushed} flushed/s, ${flush_pct}% of fired)"
echo "eventJournalSyncFallback Δ:   ${delta_fallback}"
echo "Scylla event_history delta:   ${delta_events} (${rate_events} events/s)"
echo "Mosquitto received delta:     ${delta_mqtt_rx} (${rate_mqtt_rx} msg/s)"
echo "Mosquitto delivered delta:      ${delta_mqtt_del} (${rate_mqtt_del} msg/s, ISPF capture ${capture_pct}%)"
echo "Scylla variable_samples:        ${end_samples} (expect 0)"
echo "Final journal queue:            $(journal_queue_size)"

FAIL=0
min_delta=$((MIN_RATE_EVENTS * PHASE))
if [ "${delta_fired}" -lt "${min_delta}" ]; then
  echo "FAIL: eventsFired delta ${delta_fired} < ${min_delta} (${MIN_RATE_EVENTS}/s × ${PHASE}s)" >&2
  FAIL=1
fi
if [ "${end_samples}" != "0" ]; then
  echo "FAIL: variable_samples should stay 0, got ${end_samples}" >&2
  FAIL=1
fi
if [ "${delta_fallback}" -gt 100 ]; then
  echo "WARN: sync fallback delta ${delta_fallback} — journal queue may have saturated" >&2
fi

if [ "$FAIL" -eq 0 ]; then
  echo "=== I-03 PASS ==="
else
  echo "=== I-03 FAIL ===" >&2
  exit 1
fi
