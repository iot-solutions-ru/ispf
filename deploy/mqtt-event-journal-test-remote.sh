#!/usr/bin/env bash
# Internal benchmark: mqtt driver → EventService.fireIngress → event journal.
set -euo pipefail

DIR=/opt/ispf/loadtest
ENV_FILE="${ISPF_ENV_FILE:-/opt/ispf/ispf-server.env}"
CH_PASS_FILE="${ISPF_CLICKHOUSE_PASSWORD_FILE:-/opt/ispf/clickhouse-password.txt}"
VENV="${DIR}/venv/bin/python"
SETUP="${DIR}/setup-mqtt-event-journal.py"

DEVICE="${DEVICE:-root.platform.devices.mqtt-device-01}"
TOPIC="${TOPIC:-ispf/mqtt-device-01/temperature}"
EVENT="${EVENT:-messageReceived}"
RATE="${RATE:-10000}"
PHASE="${PHASE:-60}"
WARMUP="${WARMUP:-15}"
INTERVAL_MS="${INTERVAL_MS:-1}"
PUBLISH_SEC=$((WARMUP + PHASE + 10))
BASE_URL="${BASE_URL:-http://127.0.0.1:8080}"

if [ ! -x "$VENV" ]; then
  VENV=python3
fi

event_count() {
  local path="$1"
  local name="$2"
  if [ "$journal_store" = "cassandra" ] || [ "$journal_store" = "scylla" ]; then
    local cql_out
    cql_out=$(docker exec ispf-scylla cqlsh -e \
      "SELECT COUNT(*) FROM ispf.event_history WHERE object_path='${path}';" 2>/dev/null || true)
    python3 -c "import re,sys; t=sys.stdin.read(); m=re.search(r'\\n\\s*(\\d+)\\s*\\n', t); print(m.group(1) if m else '0')" <<< "$cql_out"
  elif [ "$journal_store" = "clickhouse" ]; then
    if [ ! -f "$CH_PASS_FILE" ]; then
      echo "0"
      return
    fi
    local pass
    pass=$(tr -d '\r\n' < "$CH_PASS_FILE")
    docker exec ispf-clickhouse clickhouse-client --password "$pass" -q \
      "SELECT count() FROM ispf.event_history WHERE object_path='${path}' AND event_name='${name}'" 2>/dev/null \
      || echo "0"
  else
    docker exec ispf-postgres psql -U ispf -d ispf -t -A -c \
      "SELECT COUNT(*) FROM event_history WHERE object_path='${path}' AND event_name='${name}';" 2>/dev/null \
      || echo "0"
  fi
}

journal_store=jdbc
if [ -f "$ENV_FILE" ]; then
  journal_store=$(grep '^ISPF_EVENT_JOURNAL_STORE=' "$ENV_FILE" 2>/dev/null | cut -d= -f2- || echo jdbc)
fi

echo "Event journal store: ${journal_store}"
echo "Path: mqtt driver → EventService.fireIngress (EVENT_JOURNAL_ONLY)"
echo "Device: ${DEVICE}  event=${EVENT}"
echo "Topic: ${TOPIC} @ ~${RATE} msg/s"
echo "Warmup ${WARMUP}s, measure ${PHASE}s"

if [ -f "$SETUP" ]; then
  "$VENV" "$SETUP" --skip-purge --telemetry-coalesce-ms 1 || true
fi

curl -sf -X POST "${BASE_URL}/api/v1/auth/login" \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin"}' >/tmp/ispf-loadtest-token.json
TOKEN=$(python3 -c "import json; print(json.load(open('/tmp/ispf-loadtest-token.json'))['token'])")

curl -sf -X POST \
  "${BASE_URL}/api/v1/drivers/runtime/stop?devicePath=${DEVICE}" \
  -H "Authorization: Bearer ${TOKEN}" >/dev/null || true

curl -sf -X PUT \
  "${BASE_URL}/api/v1/drivers/runtime/configure?devicePath=${DEVICE}" \
  -H "Authorization: Bearer ${TOKEN}" \
  -H 'Content-Type: application/json' \
  -d "{\"driverId\":\"mqtt\",\"pollIntervalMs\":5000,\"telemetryPublishMode\":\"EVENT_JOURNAL_ONLY\",\"telemetryCoalesceMs\":1,\"configuration\":{\"brokerUrl\":\"tcp://127.0.0.1:1883\",\"topicPrefix\":\"\",\"ingressEventName\":\"${EVENT}\"},\"pointMappings\":{\"temperature\":\"${TOPIC}\"},\"autoStart\":true}" >/dev/null

curl -sf -X POST \
  "${BASE_URL}/api/v1/drivers/runtime/start?devicePath=${DEVICE}" \
  -H "Authorization: Bearer ${TOKEN}" >/dev/null

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

wait "${pub_pid}" || true
sleep 3

total=$(event_count "$DEVICE" "$EVENT")
delta=$((total - start_count))
rate=$(python3 -c "print(f'{$delta / max($PHASE, 1):.1f}')")
eff=$(python3 -c "print(f'{100.0 * $delta / max($RATE * $PHASE, 1):.1f}')")

echo ""
echo "=== MQTT driver → internal event journal ==="
echo "Target MQTT msg/s: ${RATE}"
echo "Measure phase: ${PHASE}s"
echo "Journal events written: ${delta} (${EVENT})"
echo "Events/s (${journal_store}): ${rate}"
echo "Efficiency vs MQTT target: ${eff}%"
echo "Total ${EVENT} in journal: ${total}"
