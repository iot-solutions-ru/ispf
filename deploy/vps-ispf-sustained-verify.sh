#!/usr/bin/env bash
# Verify sustained 313/s: queue drain, metrics, Scylla recounts, Mosquitto counters.
set -euo pipefail

DIR=/opt/ispf/loadtest
BASE_URL="${BASE_URL:-http://127.0.0.1:8080}"
DEVICE="${DEVICE:-root.platform.devices.loadtest-mqtt-dev-00001}"
TOPIC="${TOPIC:-ispf/loadtest/00001/temperature}"
DURATION="${DURATION:-65}"
CLIENTS="${CLIENTS:-20}"
INTERVAL="${INTERVAL:-10}"
LOG="${LOG:-/opt/ispf/loadtest/ispf-sustained-verify.log}"

exec > >(tee "$LOG") 2>&1
echo "=== ISPF sustained verify $(date -Is) ==="
echo "device=$DEVICE topic=$TOPIC duration=${DURATION}s clients=$CLIENTS interval_ms=$INTERVAL"

curl -sf -X POST "${BASE_URL}/api/v1/auth/login" \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin"}' >/tmp/ispf-verify-token.json
TOKEN=$(python3 -c "import json; print(json.load(open('/tmp/ispf-verify-token.json'))['token'])")

automation_metric() {
  local key=$1
  curl -sf "${BASE_URL}/api/v1/platform/metrics" -H "Authorization: Bearer ${TOKEN}" \
    | python3 -c "
import json, sys
key = sys.argv[1]
for section in json.load(sys.stdin).get('sections', []):
    if section.get('id') == 'automation':
        print(section.get('values', {}).get(key, 0))
        break
else:
    print(0)
" "$key"
}

scylla_device_count() {
  local cql_out
  cql_out=$(docker exec ispf-scylla cqlsh -e \
    "SELECT COUNT(*) FROM ispf.event_history WHERE object_path='${DEVICE}';" 2>/dev/null || echo "0")
  python3 -c "import re,sys; t=sys.stdin.read(); m=re.search(r'\\n\\s*(\\d+)\\s*\\n', t); print(m.group(1) if m else '0')" <<< "$cql_out"
}

scylla_total_count() {
  local cql_out
  cql_out=$(docker exec ispf-scylla cqlsh -e \
    "SELECT COUNT(*) FROM ispf.event_history;" 2>/dev/null || echo "0")
  python3 -c "import re,sys; t=sys.stdin.read(); m=re.search(r'\\n\\s*(\\d+)\\s*\\n', t); print(m.group(1) if m else '0')" <<< "$cql_out"
}

mosquitto_delta() {
  local key=$1
  docker exec ispf-mqtt-loadtest mosquitto_sub -h 127.0.0.1 -t "$key" -C 1 -W 3 2>/dev/null || echo "0"
}

read_mosquitto_counter() {
  # $SYS/broker/messages/received and messages/sent
  docker exec ispf-mqtt-loadtest sh -c \
    "mosquitto_sub -h 127.0.0.1 -t '\$SYS/broker/messages/received' -C 1 -W 2 2>/dev/null; echo; \
     mosquitto_sub -h 127.0.0.1 -t '\$SYS/broker/messages/sent' -C 1 -W 2 2>/dev/null" 2>/dev/null \
    | tr -d '\r' | head -2
}

print_metrics() {
  local label=$1
  echo "--- metrics ${label} ---"
  echo "  eventJournalQueueSize=$(automation_metric eventJournalQueueSize)"
  echo "  eventsFiredTotal=$(automation_metric eventsFiredTotal)"
  echo "  eventJournalFlushedTotal=$(automation_metric eventJournalFlushedTotal)"
  echo "  eventHistoryRecords=$(automation_metric eventHistoryRecords)"
  echo "  scylla_device=$(scylla_device_count)"
  echo "  scylla_total=$(scylla_total_count)"
}

curl -sf "${BASE_URL}/api/v1/drivers/runtime/status?devicePath=${DEVICE}" \
  -H "Authorization: Bearer ${TOKEN}" | python3 -m json.tool 2>/dev/null || true

print_metrics "before"
BEFORE_DEVICE=$(scylla_device_count)
BEFORE_TOTAL=$(scylla_total_count)
BEFORE_FIRED=$(automation_metric eventsFiredTotal)
BEFORE_FLUSHED=$(automation_metric eventJournalFlushedTotal)
MQTT_RX_BEFORE=$(docker exec ispf-mqtt-loadtest sh -c "mosquitto_sub -h 127.0.0.1 -t '\$SYS/broker/messages/received' -C 1 -W 2" 2>/dev/null | tr -d '\r' || echo "0")
MQTT_TX_BEFORE=$(docker exec ispf-mqtt-loadtest sh -c "mosquitto_sub -h 127.0.0.1 -t '\$SYS/broker/messages/sent' -C 1 -W 2" 2>/dev/null | tr -d '\r' || echo "0")
echo "mosquitto_rx_before=$MQTT_RX_BEFORE mosquitto_tx_before=$MQTT_TX_BEFORE"

echo "--- emqtt publish ${DURATION}s ---"
timeout "${DURATION}s" docker run --rm --network host emqx/emqtt-bench pub \
  -h 127.0.0.1 -p 1883 -c "$CLIENTS" -I "$INTERVAL" -t "$TOPIC" -m '{"v":42}' -q 0 \
  || true

print_metrics "t+5s"
sleep 5
print_metrics "t+10s"
sleep 5
print_metrics "t+15s"

echo "Waiting for journal queue drain (max 120s)..."
deadline=$((SECONDS + 120))
while [ "$SECONDS" -lt "$deadline" ]; do
  q=$(automation_metric eventJournalQueueSize)
  echo "  queue=$q elapsed=$((deadline - SECONDS))s left"
  if [ "$q" -le 500 ]; then
    break
  fi
  sleep 5
done

for wait in 0 10 20 30; do
  [ "$wait" -gt 0 ] && sleep "$wait"
  echo "--- recount t+${wait}s after drain wait ---"
  echo "  scylla_device=$(scylla_device_count)"
done

AFTER_DEVICE=$(scylla_device_count)
AFTER_TOTAL=$(scylla_total_count)
AFTER_FIRED=$(automation_metric eventsFiredTotal)
AFTER_FLUSHED=$(automation_metric eventJournalFlushedTotal)
MQTT_RX_AFTER=$(docker exec ispf-mqtt-loadtest sh -c "mosquitto_sub -h 127.0.0.1 -t '\$SYS/broker/messages/received' -C 1 -W 2" 2>/dev/null | tr -d '\r' || echo "0")
MQTT_TX_AFTER=$(docker exec ispf-mqtt-loadtest sh -c "mosquitto_sub -h 127.0.0.1 -t '\$SYS/broker/messages/sent' -C 1 -W 2" 2>/dev/null | tr -d '\r' || echo "0")

DELTA_DEVICE=$((AFTER_DEVICE - BEFORE_DEVICE))
DELTA_FIRED=$((AFTER_FIRED - BEFORE_FIRED))
DELTA_FLUSHED=$((AFTER_FLUSHED - BEFORE_FLUSHED))
DELTA_MQTT_RX=$((MQTT_RX_AFTER - MQTT_RX_BEFORE))
DELTA_MQTT_TX=$((MQTT_TX_AFTER - MQTT_TX_BEFORE))

RATE_DEVICE=$(awk -v d="$DELTA_DEVICE" -v t="$DURATION" 'BEGIN { printf "%.1f", d/t }')
RATE_FIRED=$(awk -v d="$DELTA_FIRED" -v t="$DURATION" 'BEGIN { printf "%.1f", d/t }')
RATE_FLUSHED=$(awk -v d="$DELTA_FLUSHED" -v t="$DURATION" 'BEGIN { printf "%.1f", d/t }')
RATE_MQTT_RX=$(awk -v d="$DELTA_MQTT_RX" -v t="$DURATION" 'BEGIN { printf "%.1f", d/t }')
RATE_MQTT_TX=$(awk -v d="$DELTA_MQTT_TX" -v t="$DURATION" 'BEGIN { printf "%.1f", d/t }')

echo ""
echo "=== SUMMARY (sustained verify) ==="
echo "scylla_device_delta=${DELTA_DEVICE}  rate=${RATE_DEVICE} per sec"
echo "eventsFired_delta=${DELTA_FIRED}  rate=${RATE_FIRED} per sec"
echo "flushed_delta=${DELTA_FLUSHED}  rate=${RATE_FLUSHED} per sec"
echo "mosquitto_rx_delta=${DELTA_MQTT_RX}  rate=${RATE_MQTT_RX} per sec"
echo "mosquitto_tx_delta=${DELTA_MQTT_TX}  rate=${RATE_MQTT_TX} per sec"
echo "Previous ISPF sustained (no drain): ~313/s"
