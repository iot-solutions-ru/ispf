#!/usr/bin/env bash
# Multi-device benchmark: mqtt drivers → EventService.fireIngress → event journal.
set -euo pipefail

DIR=/opt/ispf/loadtest
ENV_FILE="${ISPF_ENV_FILE:-/opt/ispf/ispf-server.env}"
CH_PASS_FILE="${ISPF_CLICKHOUSE_PASSWORD_FILE:-/opt/ispf/clickhouse-password.txt}"
VENV="${DIR}/venv/bin/python"
SETUP="${DIR}/setup-mqtt-event-journal-devices.py"

DEVICES="${DEVICES:-8}"
RATE_PER_DEVICE="${RATE_PER_DEVICE:-2000}"
EVENT="${EVENT:-messageReceived}"
PHASE="${PHASE:-60}"
WARMUP="${WARMUP:-15}"
INTERVAL_MS="${INTERVAL_MS:-1}"
TOTAL_RATE=$((DEVICES * RATE_PER_DEVICE))
PUBLISH_SEC=$((WARMUP + PHASE + 10))
BASE_URL="${BASE_URL:-http://127.0.0.1:8080}"
PAD=5
if [ "$DEVICES" -lt 5 ]; then PAD=5; else PAD="$DEVICES"; fi

if [ ! -x "$VENV" ]; then
  VENV=python3
fi

journal_store=jdbc
if [ -f "$ENV_FILE" ]; then
  journal_store=$(grep '^ISPF_EVENT_JOURNAL_STORE=' "$ENV_FILE" 2>/dev/null | cut -d= -f2- || echo jdbc)
fi

device_path_for() {
  local index="$1"
  printf "root.platform.devices.loadtest-mqtt-dev-%0${PAD}d" "$index"
}

event_count() {
  local path="$1"
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
      "SELECT count() FROM ispf.event_history WHERE object_path='${path}' AND event_name='${EVENT}'" 2>/dev/null \
      || echo "0"
  else
    docker exec ispf-postgres psql -U ispf -d ispf -t -A -c \
      "SELECT COUNT(*) FROM event_history WHERE object_path='${path}' AND event_name='${EVENT}';" 2>/dev/null \
      || echo "0"
  fi
}

meta_total_count() {
  if [ "$journal_store" = "cassandra" ] || [ "$journal_store" = "scylla" ]; then
    local cql_out
    cql_out=$(docker exec ispf-scylla cqlsh -e \
      "SELECT total_count FROM ispf.event_journal_meta WHERE id='total';" 2>/dev/null || true)
    python3 -c "import re,sys; t=sys.stdin.read(); m=re.search(r'\\n\\s*(\\d+)\\s*\\n', t); print(m.group(1) if m else '0')" <<< "$cql_out"
  elif [ "$journal_store" = "clickhouse" ]; then
    if [ ! -f "$CH_PASS_FILE" ]; then
      echo "0"
      return
    fi
    local pass
    pass=$(tr -d '\r\n' < "$CH_PASS_FILE")
    docker exec ispf-clickhouse clickhouse-client --password "$pass" -q \
      "SELECT count() FROM ispf.event_history" 2>/dev/null || echo "0"
  else
    docker exec ispf-postgres psql -U ispf -d ispf -t -A -c \
      "SELECT COUNT(*) FROM event_history;" 2>/dev/null || echo "0"
  fi
}

sum_event_counts() {
  meta_total_count
}

echo "Event journal store: ${journal_store}"
echo "Path: mqtt driver → EventService.fireIngress (EVENT_JOURNAL_ONLY)"
echo "Devices: ${DEVICES} × ~${RATE_PER_DEVICE} msg/s = ~${TOTAL_RATE} msg/s aggregate"
echo "Warmup ${WARMUP}s, measure ${PHASE}s"

if [ -f "$SETUP" ]; then
  "$VENV" "$SETUP" --devices "$DEVICES" --telemetry-coalesce-ms 1
fi

sleep 2

bash "${DIR}/mqtt-emqtt-bench.sh" \
  --host 127.0.0.1 --port 1883 \
  --devices "$DEVICES" \
  --messages-per-second "$TOTAL_RATE" \
  --duration-seconds "$PUBLISH_SEC" \
  --interval-ms "${INTERVAL_MS}" &
pub_pid=$!

sleep "${WARMUP}"
start_count=$(sum_event_counts)
echo "Measure start (journal total): ${start_count}"
sleep "${PHASE}"
sleep 5

wait "${pub_pid}" || true
sleep 5

total=$(sum_event_counts)
delta=$((total - start_count))
rate=$(python3 -c "print(f'{$delta / max($PHASE, 1):.1f}')")
eff=$(python3 -c "print(f'{100.0 * $delta / max($TOTAL_RATE * $PHASE, 1):.1f}')")
per_device=$(python3 -c "print(f'{$delta / max($DEVICES * $PHASE, 1):.1f}')")

echo ""
echo "=== MQTT ${DEVICES} devices → internal event journal ==="
echo "Target: ${DEVICES} × ${RATE_PER_DEVICE} msg/s = ${TOTAL_RATE} msg/s"
echo "Measure phase: ${PHASE}s"
echo "Journal events written: ${delta} (${EVENT})"
echo "Events/s total (${journal_store}): ${rate}"
echo "Events/s per device (avg): ${per_device}"
echo "Efficiency vs MQTT target: ${eff}%"
echo "Total ${EVENT} in journal (global counter): ${total}"
