#!/usr/bin/env bash
# Gate: 1 device, 1 topic, target ~100k MQTT msg/s → ~100k journal/s (1:1, no fan-out).
set -euo pipefail

LAB_ROOT="${ISPF_LAB_ROOT:-$HOME/ispf}"
COMPOSE_FILE="$LAB_ROOT/lab-test-host-compose.yml"
ENV_FILE="$LAB_ROOT/lab-stress.env"
HTTP_PORT="${ISPF_LAB_HTTP_PORT:-8000}"
BASE_URL="http://127.0.0.1:${HTTP_PORT}"
TOPIC="${TOPIC:-ispf/loadtest/00001/temperature}"
WARMUP="${WARMUP:-20}"
PHASE="${PHASE:-60}"
TARGET_RATE="${TARGET_RATE:-100000}"
EMQTT_CLIENTS="${EMQTT_CLIENTS:-220}"
EMQTT_INTERVAL_MS="${EMQTT_INTERVAL_MS:-1}"
EMQTT_SHARDS="${EMQTT_SHARDS:-4}"
EMQTT_CPU_LIMIT="${EMQTT_CPU_LIMIT:-3.0}"
LOG="${LOG:-$LAB_ROOT/loadtest/gate-1dev-100k.log}"

if [ -f "$ENV_FILE" ]; then
  set -a
  # shellcheck disable=SC1090
  source "$ENV_FILE"
  set +a
fi

exec > >(tee "$LOG") 2>&1
echo "=== GATE 1dev ~${TARGET_RATE}/s topic=${TOPIC} $(date -Is) ==="

TOKEN=$(curl -sf -X POST "${BASE_URL}/api/v1/auth/login" \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin"}' \
  | python3 -c "import json,sys; print(json.load(sys.stdin)['token'])")

automation_metric() {
  curl -sf "${BASE_URL}/api/v1/platform/metrics" -H "Authorization: Bearer ${TOKEN}" \
    | python3 -c "
import json, sys
key = sys.argv[1]
for section in json.load(sys.stdin).get('sections', []):
    if section.get('id') == 'automation':
        print(int(section.get('values', {}).get(key, 0) or 0))
        break
else:
    print(0)
" "$1"
}

mqtt_container_id() {
  docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" ps -q mqtt 2>/dev/null | head -1
}

broker_messages_received_total() {
  local cid
  cid=$(mqtt_container_id)
  if [ -z "$cid" ]; then echo 0; return; fi
  docker exec "$cid" mosquitto_sub -h localhost -t '$SYS/broker/messages/received' -C 1 -W 5 2>/dev/null \
    | tr -d '\r' | awk '{print $NF}' | tail -1
}

bash "$LAB_ROOT/lab-emqtt-cleanup.sh" 2>/dev/null || true

before_fired=$(automation_metric eventsFiredTotal)
before_broker=$(broker_messages_received_total)
before_broker=${before_broker:-0}
before_queue=$(automation_metric eventJournalQueueSize)
formula_rate=$(awk -v c="$EMQTT_CLIENTS" -v i="$EMQTT_INTERVAL_MS" 'BEGIN { printf "%.0f", c * (1000 / i) }')
echo "before: eventsFired=${before_fired} queue=${before_queue} brokerRx=${before_broker}"
echo "emqtt: ${EMQTT_SHARDS} shard(s) × ${EMQTT_CLIENTS} clients @ ${EMQTT_INTERVAL_MS}ms formula~${formula_rate}/shard"

pub_sec=$((WARMUP + PHASE + 15))
MQTT_NETWORK="${EMQTT_DOCKER_NETWORK:-ispf-lab_default}"
per=$(( (EMQTT_CLIENTS + EMQTT_SHARDS - 1) / EMQTT_SHARDS ))
pids=()
for _ in $(seq 1 "$EMQTT_SHARDS"); do
  timeout "${pub_sec}s" docker run --rm --network "$MQTT_NETWORK" \
    --cpus "$EMQTT_CPU_LIMIT" --label ispf.emqtt-bench=1 \
    emqx/emqtt-bench pub \
    -h mqtt -p 1883 -c "$per" -I "$EMQTT_INTERVAL_MS" -t "$TOPIC" -m '{"v":42}' -q 0 \
    >/dev/null 2>&1 &
  pids+=("$!")
done

sleep "$WARMUP"
start_fired=$(automation_metric eventsFiredTotal)
start_broker=$(broker_messages_received_total)
start_broker=${start_broker:-0}
measure_start=$SECONDS
sleep "$PHASE"
measure_sec=$((SECONDS - measure_start))
for pid in "${pids[@]}"; do wait "$pid" 2>/dev/null || true; done
sleep 5

end_fired=$(automation_metric eventsFiredTotal)
end_broker=$(broker_messages_received_total)
end_broker=${end_broker:-0}
after_queue=$(automation_metric eventJournalQueueSize)
delta_fired=$((end_fired - start_fired))
delta_broker=$((end_broker - start_broker))
if [ "${delta_broker:-0}" -lt 0 ] 2>/dev/null; then delta_broker=0; fi
rate_fired=$(awk -v d="$delta_fired" -v t="$measure_sec" 'BEGIN { if (t > 0) printf "%.1f", d/t; else print "0.0" }')
rate_broker=$(awk -v d="$delta_broker" -v t="$measure_sec" 'BEGIN { if (t > 0) printf "%.1f", d/t; else print "0.0" }')
capture=$(awk -v j="$rate_fired" -v b="$rate_broker" 'BEGIN { if (b > 0) printf "%.1f", 100*j/b; else print "0.0" }')

echo "RESULT 1dev target=${TARGET_RATE}/s measure=${measure_sec}s"
echo "  broker_rx_per_sec=${rate_broker} (Mosquitto \$SYS)"
echo "  eventsFired_per_sec=${rate_fired} capture_vs_broker=${capture}%"
echo "  eventJournalQueueSize=${after_queue}"
echo "GATE_ROW|1dev|${TARGET_RATE}|${measure_sec}|${delta_fired}|${rate_fired}|${rate_broker}|${capture}|${after_queue}"

docker stats --no-stream --format '  {{.Name}} cpu={{.CPUPerc}} mem={{.MemUsage}}' \
  "$(docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" ps -q ispf-server 2>/dev/null)" \
  "$(docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" ps -q scylla 2>/dev/null)" 2>/dev/null || true
echo "=== DONE 1dev gate $(date -Is) ==="
