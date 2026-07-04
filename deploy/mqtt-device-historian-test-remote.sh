#!/usr/bin/env bash
set -euo pipefail
DIR=/opt/ispf/loadtest
ENV_FILE="${ISPF_ENV_FILE:-/opt/ispf/ispf-server.env}"
CH_PASS_FILE="${ISPF_CLICKHOUSE_PASSWORD_FILE:-/opt/ispf/clickhouse-password.txt}"
DEVICE=root.platform.devices.mqtt-device-01
TOPIC=ispf/mqtt-device-01/temperature
RATE="${RATE:-10000}"
PHASE="${PHASE:-60}"
WARMUP="${WARMUP:-15}"
FIELD=raw
VARIABLE=temperature
INTERVAL_MS="${INTERVAL_MS:-1}"
PUBLISH_SEC=$((WARMUP + PHASE + 5))

VAR_STORE=jdbc
if [ -f "$ENV_FILE" ]; then
  VAR_STORE=$(grep '^ISPF_VARIABLE_HISTORY_STORE=' "$ENV_FILE" 2>/dev/null | cut -d= -f2- || echo jdbc)
fi

sample_count() {
  local path="$1"
  local field="$2"
  if [ "$VAR_STORE" = "clickhouse" ]; then
    local pass
    pass=$(tr -d '\r\n' < "$CH_PASS_FILE")
    docker exec ispf-clickhouse clickhouse-client --password "$pass" -q \
      "SELECT count() FROM ispf.variable_samples WHERE object_path='${path}' AND field_name='${field}'"
  elif [ "$VAR_STORE" = "cassandra" ] || [ "$VAR_STORE" = "scylla" ]; then
    local cql_out
    cql_out=$(docker exec ispf-scylla cqlsh -e \
      "SELECT COUNT(*) FROM ispf.variable_samples WHERE object_path='${path}' AND variable_name='${VARIABLE}' AND field_name='${field}';" 2>/dev/null || true)
    python3 -c "import re,sys; t=sys.stdin.read(); m=re.search(r'\\n\\s*(\\d+)\\s*\\n', t); print(m.group(1) if m else '0')" <<< "$cql_out"
  else
    docker exec ispf-postgres psql -U ispf -d ispf -t -A -c \
      "SELECT COUNT(*) FROM variable_samples WHERE object_path='${path}' AND field_name='${field}';"
  fi
}

baseline=$(sample_count "$DEVICE" "$FIELD")
echo "Historian store: ${VAR_STORE}"
echo "Baseline samples (${FIELD}): ${baseline}"
echo "Warmup ${WARMUP}s, emqtt-bench ~${RATE} msg/s on ${TOPIC} for ${PUBLISH_SEC}s, measure ${PHASE}s"

bash "${DIR}/mqtt-emqtt-bench.sh" \
  --host 127.0.0.1 --port 1883 \
  --topic "${TOPIC}" \
  --messages-per-second "${RATE}" \
  --duration-seconds "${PUBLISH_SEC}" \
  --interval-ms "${INTERVAL_MS}" &
pub_pid=$!

sleep "${WARMUP}"
sleep "${PHASE}"
sleep 5
elapsed=$((PHASE + 5))

wait "${pub_pid}" || true

total=$(sample_count "$DEVICE" "$FIELD")
delta=$((total - baseline))
rate=$(python3 -c "print(f'{$delta / max($elapsed, 1):.1f}')")
eff=$(python3 -c "print(f'{100.0 * $delta / max($RATE * $PHASE, 1):.1f}')")

echo ""
echo "Target msg/s: ${RATE}"
echo "Publisher phase: ${PHASE}s"
echo "Historian samples: ${delta} (${FIELD})"
echo "Samples/s: ${rate}"
echo "Efficiency vs target: ${eff}%"
echo "Total ${FIELD} samples in store: ${total}"
