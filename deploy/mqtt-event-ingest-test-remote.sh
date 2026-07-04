#!/usr/bin/env bash
# Benchmark: each MQTT payload → one event in journal (via HTTP tap).
set -euo pipefail

DIR=/opt/ispf/loadtest
ENV_FILE="${ISPF_ENV_FILE:-/opt/ispf/ispf-server.env}"
CH_PASS_FILE="${ISPF_CLICKHOUSE_PASSWORD_FILE:-/opt/ispf/clickhouse-password.txt}"
VENV="${DIR}/venv/bin/python"
TAP="${DIR}/mqtt-event-ingest-tap.py"

DEVICE="${DEVICE:-root.platform.devices.mqtt-device-01}"
TOPIC="${TOPIC:-ispf/mqtt-device-01/temperature}"
EVENT="${EVENT:-messageReceived}"
RATE="${RATE:-10000}"
PHASE="${PHASE:-60}"
WARMUP="${WARMUP:-15}"
WORKERS="${WORKERS:-64}"
INTERVAL_MS="${INTERVAL_MS:-1}"
PUBLISH_SEC=$((WARMUP + PHASE + 10))
TAP_SEC=$((WARMUP + PHASE + 15))
BASE_URL="${BASE_URL:-http://127.0.0.1:8080}"
STOP_DRIVER="${STOP_DRIVER:-1}"

if [ ! -x "$VENV" ]; then
  VENV=python3
fi

event_count() {
  local path="$1"
  local name="$2"
  if [ ! -f "$CH_PASS_FILE" ]; then
    echo "0"
    return
  fi
  local pass
  pass=$(tr -d '\r\n' < "$CH_PASS_FILE")
  docker exec ispf-clickhouse clickhouse-client --password "$pass" -q \
    "SELECT count() FROM ispf.event_history WHERE object_path='${path}' AND event_name='${name}'" 2>/dev/null \
    || echo "0"
}

journal_store=jdbc
if [ -f "$ENV_FILE" ]; then
  journal_store=$(grep '^ISPF_EVENT_JOURNAL_STORE=' "$ENV_FILE" 2>/dev/null | cut -d= -f2- || echo jdbc)
fi

echo "Event journal store: ${journal_store}"
echo "Device: ${DEVICE}"
echo "Topic: ${TOPIC} @ ~${RATE} msg/s"
echo "Tap: ${WORKERS} HTTP workers, event=${EVENT}"
echo "Warmup ${WARMUP}s, measure ${PHASE}s"

if [ "$STOP_DRIVER" = "1" ]; then
  echo "Stopping ISPF mqtt driver on device (tap-only path)..."
  curl -sf -X POST "${BASE_URL}/api/v1/auth/login" \
    -H 'Content-Type: application/json' \
    -d '{"username":"admin","password":"admin"}' >/tmp/ispf-loadtest-token.json || true
  if [ -f /tmp/ispf-loadtest-token.json ]; then
    TOKEN=$(python3 -c "import json; print(json.load(open('/tmp/ispf-loadtest-token.json'))['token'])")
    curl -sf -X POST \
      "${BASE_URL}/api/v1/drivers/runtime/stop?devicePath=${DEVICE}" \
      -H "Authorization: Bearer ${TOKEN}" >/dev/null || true
  fi
fi

echo "Preparing tap + publisher..."

"$VENV" "$TAP" \
  --broker tcp://127.0.0.1:1883 \
  --topic "${TOPIC}" \
  --base-url "${BASE_URL}" \
  --object-path "${DEVICE}" \
  --event-name "${EVENT}" \
  --workers "${WORKERS}" \
  --warmup-seconds "${WARMUP}" \
  --duration-seconds "$((PHASE + 10))" &
tap_pid=$!

sleep 2

bash "${DIR}/mqtt-emqtt-bench.sh" \
  --host 127.0.0.1 --port 1883 \
  --topic "${TOPIC}" \
  --messages-per-second "${RATE}" \
  --duration-seconds "${PUBLISH_SEC}" \
  --interval-ms "${INTERVAL_MS}" &
pub_pid=$!

sleep "${WARMUP}"
start_count=$(event_count "$DEVICE" "$EVENT")
echo "Measure start (${EVENT}): ${start_count}"
sleep "${PHASE}"
sleep 5

wait "${tap_pid}" || true
wait "${pub_pid}" || true

sleep 3
total=$(event_count "$DEVICE" "$EVENT")
delta=$((total - start_count))
rate=$(python3 -c "print(f'{$delta / max($PHASE, 1):.1f}')")
eff=$(python3 -c "print(f'{100.0 * $delta / max($RATE * $PHASE, 1):.1f}')")

echo ""
echo "=== MQTT → event journal (HTTP tap) ==="
echo "Target MQTT msg/s: ${RATE}"
echo "Measure phase: ${PHASE}s"
echo "Journal events written: ${delta} (${EVENT})"
echo "Events/s (ClickHouse): ${rate}"
echo "Efficiency vs MQTT target: ${eff}%"
echo "Total ${EVENT} in journal: ${total}"
