#!/usr/bin/env bash
# I-01 single-node: MQTT TELEMETRY_ONLY → ClickHouse historian.
set -euo pipefail

LAB_ROOT="${ISPF_LAB_ROOT:-$HOME/ispf}"
DIR="${LAB_ROOT}/loadtest"
SETUP="${DIR}/setup-mqtt-historian-devices.py"
REMOTE_EMQTT="${LAB_ROOT}/lab-emqtt-remote.sh"
# shellcheck source=lab-loadgen-common.sh
source "${LAB_ROOT}/lab-loadgen-common.sh" 2>/dev/null || true

DEVICES="${DEVICES:-4}"
RATE_PER_DEVICE="${RATE_PER_DEVICE:-500}"
PHASE="${PHASE:-60}"
WARMUP="${WARMUP:-20}"
INTERVAL_MS="${INTERVAL_MS:-10}"
EMQTT_SHARD_MAX="${EMQTT_SHARD_MAX:-2}"
TOTAL_RATE=$((DEVICES * RATE_PER_DEVICE))
HTTP_PORT="${ISPF_LAB_HTTP_PORT:-8000}"
BASE_URL="http://127.0.0.1:${HTTP_PORT}"
MQTT_HOST="${MQTT_HOST:-${ISPF_MQTT_BROKER_HOST:-198.51.100.10}}"
MQTT_PORT="${MQTT_PORT:-${ISPF_MQTT_BROKER_PORT:-1883}}"

CH_URL="${ISPF_VARIABLE_HISTORY_CLICKHOUSE_URL:-http://${ISPF_LAB_DB_HOST}:8123}"
CH_AUTH="${ISPF_VARIABLE_HISTORY_CLICKHOUSE_USERNAME:-ispf}:${ISPF_VARIABLE_HISTORY_CLICKHOUSE_PASSWORD:-CHANGE_ME_CLICKHOUSE}"
CH_DB="${ISPF_VARIABLE_HISTORY_CLICKHOUSE_DATABASE:-ispf}"
CH_TABLE="${ISPF_VARIABLE_HISTORY_CLICKHOUSE_TABLE:-variable_samples}"

if [ ! -x "${REMOTE_EMQTT}" ]; then
  echo "ERROR: missing ${REMOTE_EMQTT}" >&2
  exit 2
fi

ch_count() {
  curl -sf -u "${CH_AUTH}" \
    "${CH_URL}/?query=SELECT%20count()%20FROM%20${CH_DB}.${CH_TABLE}" 2>/dev/null || echo -1
}

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

metric_section() {
  local section_id=$1 key=$2
  if ! ensure_api_token; then echo 0; return 0; fi
  curl -sf "${BASE_URL}/api/v1/platform/metrics" -H "Authorization: Bearer ${AUTH_TOKEN}" \
    | python3 -c "
import json, sys
sid, key = sys.argv[1], sys.argv[2]
try:
    raw = sys.stdin.read().strip()
    d = json.loads(raw) if raw else {}
except Exception:
    print(0); raise SystemExit
for section in d.get('sections', []):
    if section.get('id') == sid:
        v = section.get('values', {}).get(key, 0)
        print(int(v) if isinstance(v, (int, float)) and float(v).is_integer() else v)
        break
else:
    print(0)
" "$section_id" "$key" 2>/dev/null || echo 0
}

historian_flushed_total() { metric_section automation variableHistoryFlushedTotal; }
historian_queue_size() { metric_section automation variableHistoryQueueSize; }

wait_historian_queue_drain() {
  local max_wait="${1:-120}"
  local deadline=$((SECONDS + max_wait))
  while [ "${SECONDS}" -lt "${deadline}" ]; do
    local q
    q=$(historian_queue_size)
    echo "  variableHistoryQueueSize=${q}" >&2
    [ "${q}" -le 1000 ] && return 0
    sleep 5
  done
  return 1
}

run_emqtt_publisher() {
  local total_rate=$1 pub_sec=$2
  local log_file="${DIR}/emqtt-pub-single-hist-ch-$$.log"
  NUMERIC_PAYLOAD=true bash "${REMOTE_EMQTT}" -- \
    --single-container --shard-max "$EMQTT_SHARD_MAX" \
    --devices "$DEVICES" \
    --messages-per-second "$total_rate" \
    --duration-seconds "$pub_sec" \
    --interval-ms "${INTERVAL_MS}" >"${log_file}" 2>&1 &
  echo $!
}

echo "=== I-01 MQTT historian (single-node, ClickHouse) ==="
curl -sf "${BASE_URL}/api/v1/info" | python3 -c "import json,sys; d=json.load(sys.stdin); print(f\"ISPF {d.get('version')} env={d.get('environment')} cluster={d.get('clusterEnabled')}\")" || true
echo "API: ${BASE_URL}"
echo "CH: ${CH_URL}/${CH_DB}.${CH_TABLE}"
echo "MQTT broker: ${MQTT_HOST}:${MQTT_PORT} (loadgen)"
echo "Devices: ${DEVICES} × ~${RATE_PER_DEVICE} msg/s = ~${TOTAL_RATE} msg/s"
echo "Warmup ${WARMUP}s, measure ${PHASE}s"

echo "==> Seed devices (TELEMETRY_ONLY + historian)"
python3 "${SETUP}" \
  --devices "${DEVICES}" \
  --telemetry-coalesce-ms 1 \
  --base-url "${BASE_URL}" \
  --broker-url "tcp://${MQTT_HOST}:${MQTT_PORT}" \
  --bench-no-l0-coalesce

echo "==> Stabilize drivers (15s)"
sleep 15
bash "${LAB_ROOT}/lab-emqtt-cleanup-remote.sh" 2>/dev/null || true

PUBLISH_SEC=$((WARMUP + PHASE + 10))
pub_pid=$(run_emqtt_publisher "$TOTAL_RATE" "$PUBLISH_SEC")

sleep "${WARMUP}"
start_ch=$(ch_count)
start_flushed=$(historian_flushed_total)
start_mqtt_rx=$(mosquitto_sys_counter '$SYS/broker/messages/received')
echo "Measure start: ch_samples=${start_ch} flushed=${start_flushed} mosquitto_rx=${start_mqtt_rx}"
sleep "${PHASE}"
sleep 5

wait "${pub_pid}" 2>/dev/null || true
sleep 5
wait_historian_queue_drain 120 || echo "WARN: historian queue still elevated" >&2

end_ch=$(ch_count)
end_flushed=$(historian_flushed_total)
end_mqtt_rx=$(mosquitto_sys_counter '$SYS/broker/messages/received')

delta_ch=$((end_ch - start_ch))
delta_flushed=$((end_flushed - start_flushed))
delta_mqtt_rx=$((end_mqtt_rx - start_mqtt_rx))
[ "$delta_mqtt_rx" -lt 0 ] && delta_mqtt_rx=0
[ "$delta_ch" -lt 0 ] && delta_ch=0

rate_ch=$(python3 -c "print(f'{$delta_ch / max($PHASE, 1):.1f}')")
rate_flushed=$(python3 -c "print(f'{$delta_flushed / max($PHASE, 1):.1f}')")
rate_mqtt_rx=$(python3 -c "print(f'{$delta_mqtt_rx / max($PHASE, 1):.1f}')")

echo ""
echo "=== I-01 CH results ==="
echo "CH variable_samples delta: ${delta_ch} (${rate_ch} samples/s)"
echo "Historian flushed delta:   ${delta_flushed} (${rate_flushed} samples/s)"
echo "Mosquitto received delta:  ${delta_mqtt_rx} (${rate_mqtt_rx} msg/s)"
echo "Final historian queue:     $(historian_queue_size)"

FAIL=0
if [ "${delta_ch}" -lt 40 ]; then
  echo "FAIL: CH samples delta ${delta_ch} < 40" >&2
  FAIL=1
fi
if [ "${delta_flushed}" -lt 30 ]; then
  echo "FAIL: historian flushed delta ${delta_flushed} < 30" >&2
  FAIL=1
fi

if [ "$FAIL" -eq 0 ]; then
  echo "=== I-01 CH PASS ==="
else
  echo "=== I-01 CH FAIL ===" >&2
  exit 1
fi
