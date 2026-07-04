#!/usr/bin/env bash
# Smoke test for ISPF lab stack (docker compose under ~/ispf).
set -euo pipefail

LAB_ROOT="${ISPF_LAB_ROOT:-$HOME/ispf}"
COMPOSE_FILE="$LAB_ROOT/lab-test-host-compose.yml"
HTTP_PORT="${ISPF_LAB_HTTP_PORT:-8000}"
BASE_URL="http://127.0.0.1:${HTTP_PORT}"

SCYLLA_CID=$(docker compose -f "$COMPOSE_FILE" ps -q scylla)
API_BASE="${BASE_URL}/api"

echo "=== API info ==="
INFO=$(curl -sf "${API_BASE}/v1/info")
echo "$INFO" | head -c 500
echo

echo "=== Login ==="
TOKEN=$(curl -sf -X POST "${API_BASE}/v1/auth/login" \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin"}' \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['token'])")
echo "token OK (${#TOKEN} chars)"

echo "=== Demo device ==="
curl -sf -H "Authorization: Bearer $TOKEN" \
  "${API_BASE}/v1/objects/by-path?path=root.platform.devices.demo-sensor-01" \
  | python3 -c "import sys,json; o=json.load(sys.stdin); print(o.get('path'), o.get('displayName'))"

echo "=== Scylla keyspace/tables ==="
docker exec "$SCYLLA_CID" cqlsh -e "DESCRIBE KEYSPACE ispf" | head -20

echo "=== Event fire smoke ==="
DEVICE="root.platform.devices.demo-sensor-01"
EVENT="thresholdExceeded"
curl -sf -X POST \
  "${API_BASE}/v1/events/fire?objectPath=${DEVICE}&eventName=${EVENT}" \
  -H "Authorization: Bearer $TOKEN" >/dev/null
sleep 2
COUNT=$(docker exec "$SCYLLA_CID" cqlsh -e \
  "SELECT COUNT(*) FROM ispf.event_history WHERE object_path='${DEVICE}';" \
  | python3 -c "import re,sys; t=sys.stdin.read(); m=re.search(r'\n\s*(\d+)\s*\n', t); print(m.group(1) if m else '0')")
echo "event_history count for demo-sensor-01: $COUNT"
if [ "${COUNT:-0}" -lt 1 ]; then
  echo "FAIL: expected event in Scylla" >&2
  exit 1
fi

echo "=== MQTT broker ==="
MQTT_CID=$(docker compose -f "$COMPOSE_FILE" ps -q mqtt)
docker exec "$MQTT_CID" mosquitto_sub -h localhost -t 'test/ping' -C 1 -W 3 &
SUB_PID=$!
sleep 1
docker exec "$MQTT_CID" mosquitto_pub -h localhost -t 'test/ping' -m ok
wait "$SUB_PID" && echo "MQTT pub/sub OK"

echo
echo "PASS: lab stack smoke test"
echo "UI: http://$(hostname -I | awk '{print $1}'):${HTTP_PORT}/"
