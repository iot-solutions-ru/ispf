#!/usr/bin/env bash
# ISPF single-device MQTT load test: emqtt_bench, 1 device, Scylla event_history delta.
set -euo pipefail

DIR=/opt/ispf/loadtest
ENV_FILE="${ISPF_ENV_FILE:-/opt/ispf/ispf-server.env}"
VENV="${DIR}/venv/bin/python"
SETUP_DEVICES="${DIR}/setup-mqtt-event-journal-devices.py"
BASE_URL="${BASE_URL:-http://127.0.0.1:8080}"
DEVICE="${DEVICE:-root.platform.devices.loadtest-mqtt-dev-00001}"
TOPIC="${TOPIC:-ispf/loadtest/00001/temperature}"
BROKER="${BROKER:-tcp://127.0.0.1:1883}"
LOG="${LOG:-/opt/ispf/loadtest/ispf-fair-single.log}"

if [ "${SKIP_STARTUP:-0}" != "1" ]; then
  exec > >(tee -a "$LOG") 2>&1
else
  exec >> "$LOG" 2>&1
fi
echo "=== ISPF fair single-device bench $(date -Is) ==="

if [ ! -x "$VENV" ]; then
  VENV=python3
fi

journal_store=scylla
if [ -f "$ENV_FILE" ]; then
  journal_store=$(grep '^ISPF_EVENT_JOURNAL_STORE=' "$ENV_FILE" 2>/dev/null | cut -d= -f2- || echo scylla)
fi

event_count() {
  local path="$1"
  local attempts="${2:-1}"
  if [ "$journal_store" != "scylla" ] && [ "$journal_store" != "cassandra" ]; then
    echo "0"
    return
  fi
  local i cql_out count
  count=0
  for i in $(seq 1 "$attempts"); do
    cql_out=$(docker exec ispf-scylla cqlsh -e \
      "SELECT COUNT(*) FROM ispf.event_history WHERE object_path='${path}';" 2>/dev/null || echo "0")
    count=$(python3 -c "import re,sys; t=sys.stdin.read(); m=re.search(r'\\n\\s*(\\d+)\\s*\\n', t); print(m.group(1) if m else '0')" <<< "$cql_out")
    if [ "$count" != "0" ]; then
      echo "$count"
      return
    fi
    [ "$i" -lt "$attempts" ] && sleep 5
  done
  echo "$count"
}

automation_metric() {
  local key="$1"
  curl -sf "${BASE_URL}/api/v1/platform/metrics" -H "Authorization: Bearer ${TOKEN}" 2>/dev/null \
    | python3 -c "
import json, sys
key = sys.argv[1]
for section in json.load(sys.stdin).get('sections', []):
    if section.get('id') == 'automation':
        print(section.get('values', {}).get(key, 0))
        break
else:
    print(0)
" "$key" || echo "0"
}

events_fired_total() {
  automation_metric eventsFiredTotal
}

run_emqtt() {
  local duration="$1"
  local clients="$2"
  local interval="$3"
  echo "--- emqtt: ${duration}s clients=${clients} interval_ms=${interval} topic=${TOPIC} ---"
  timeout "${duration}s" docker run --rm --network host emqx/emqtt-bench pub \
    -h 127.0.0.1 -p 1883 -c "$clients" -I "$interval" -t "$TOPIC" -m '{"v":42}' -q 0 \
    || true
}

run_phase() {
  local label="$1"
  local duration="$2"
  local clients="$3"
  local interval="$4"
  echo ""
  echo "========== ${label} =========="
  local before_scylla before_fired
  before_scylla=$(event_count "$DEVICE")
  before_fired=$(events_fired_total)
  echo "scylla_before=${before_scylla} eventsFired_before=${before_fired}"
  run_emqtt "$duration" "$clients" "$interval"
  local settle=5
  if [ "$interval" -le 5 ]; then
    settle=25
  fi
  echo "settle=${settle}s (journal drain + Scylla COUNT retries)..."
  sleep "$settle"
  local after_scylla after_fired after_queue after_fallback
  local count_attempts=1
  if [ "$interval" -le 5 ]; then
    count_attempts=6
  fi
  after_scylla=$(event_count "$DEVICE" "$count_attempts")
  after_fired=$(events_fired_total)
  after_queue=$(automation_metric eventJournalQueueSize)
  after_fallback=$(automation_metric eventJournalSyncFallbackTotal)
  local delta_scylla delta_fired
  delta_scylla=$((after_scylla - before_scylla))
  delta_fired=$((after_fired - before_fired))
  local rate_scylla rate_fired
  rate_scylla=$(awk -v d="$delta_scylla" -v t="$duration" 'BEGIN { printf "%.1f", d/t }')
  rate_fired=$(awk -v d="$delta_fired" -v t="$duration" 'BEGIN { printf "%.1f", d/t }')
  echo "scylla_after=${after_scylla} scylla_delta=${delta_scylla} scylla_per_sec=${rate_scylla}"
  echo "eventsFired_after=${after_fired} eventsFired_delta=${delta_fired} eventsFired_per_sec=${rate_fired}"
  echo "eventJournalQueueSize=${after_queue} eventJournalSyncFallbackTotal=${after_fallback}"
}

echo "Starting ISPF dependencies..."
if [ "${SKIP_STARTUP:-0}" != "1" ]; then
  for c in ispf-scylla ispf-postgres ispf-redis; do
    docker start "$c" 2>/dev/null && echo "started $c" || echo "skip $c"
  done
  if ! docker ps -q -f name=ispf-mqtt-loadtest | grep -q .; then
    bash "${DIR}/vps-mqtt-broker.sh" || docker start ispf-mqtt-loadtest
  fi
  echo "Waiting for Scylla CQL..."
  for i in $(seq 1 60); do
    if docker exec ispf-scylla cqlsh -e "SELECT now() FROM system.local" >/dev/null 2>&1; then
      echo "Scylla ready"
      break
    fi
    sleep 2
  done
  systemctl start ispf-server
  echo "Waiting for ISPF API..."
  for i in $(seq 1 90); do
    if curl -sf -X POST "${BASE_URL}/api/v1/auth/login" \
      -H 'Content-Type: application/json' \
      -d '{"username":"admin","password":"admin"}' >/tmp/ispf-fair-token.json 2>/dev/null; then
      echo "ISPF API ready"
      break
    fi
    sleep 3
  done
else
  curl -sf -X POST "${BASE_URL}/api/v1/auth/login" \
    -H 'Content-Type: application/json' \
    -d '{"username":"admin","password":"admin"}' >/tmp/ispf-fair-token.json
  echo "SKIP_STARTUP: using running ISPF"
fi
TOKEN=$(python3 -c "import json; print(json.load(open('/tmp/ispf-fair-token.json'))['token'])")
curl -sf "${BASE_URL}/api/v1/info" -H "Authorization: Bearer ${TOKEN}" || true
echo ""

echo "Seeding 1 MQTT device (EVENT_JOURNAL_ONLY)..."
"$VENV" "$SETUP_DEVICES" \
  --base-url "$BASE_URL" \
  --broker-url "$BROKER" \
  --devices 1 \
  --telemetry-coalesce-ms 1 \
  --bench-no-l0-coalesce

sleep 3
curl -sf "${BASE_URL}/api/v1/drivers/runtime/status?devicePath=${DEVICE}" \
  -H "Authorization: Bearer ${TOKEN}" | python3 -m json.tool 2>/dev/null || true
echo "device=${DEVICE} topic=${TOPIC} store=${journal_store}"

run_phase "SUSTAINED (65s, 20 clients, 10ms)" 65 20 10
sleep 10
run_phase "PEAK (65s, 32 clients, 1ms)" 65 32 1

echo ""
echo "=== DONE ==="
docker stats --no-stream --format '{{.Name}} {{.CPUPerc}} {{.MemUsage}}' ispf-scylla 2>/dev/null || true
free -h | head -2
