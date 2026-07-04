#!/usr/bin/env bash
# High-rate MQTT publisher for ISPF load tests (emqx/emqtt-bench via Docker).
#
# Publishes to ispf/loadtest/00001/temperature … (same layout as mqtt-loadtest-publisher.py).
# One emqtt-bench process per device topic; clients share the target rate on that topic.
#
#   msg/s ≈ devices × clients_per_topic × (1000 / interval_ms)
#
# Example (50k msg/s, 4 devices, on VPS next to Mosquitto):
#   bash mqtt-emqtt-bench.sh --devices 4 --messages-per-second 50000 --duration-seconds 60
#
set -euo pipefail

HOST="${HOST:-127.0.0.1}"
PORT="${PORT:-1883}"
DEVICES="${DEVICES:-4}"
MESSAGES_PER_SECOND="${MESSAGES_PER_SECOND:-2000}"
DURATION_SECONDS="${DURATION_SECONDS:-60}"
INTERVAL_MS="${INTERVAL_MS:-10}"
TOPIC_PREFIX="${TOPIC_PREFIX:-ispf/loadtest}"
SINGLE_TOPIC="${SINGLE_TOPIC:-}"
PAYLOAD_SIZE="${PAYLOAD_SIZE:-16}"
IMAGE="${EMQTT_BENCH_IMAGE:-emqx/emqtt-bench:latest}"
MQTT_VERSION="${MQTT_VERSION:-4}"
# Numeric template so historian can parse lastIngress.raw / temperature.raw as double
PAYLOAD_TEMPLATE="$(mktemp /tmp/ispf-emqtt-payload.XXXXXX)"
echo '%TIMESTAMPMS%' >"$PAYLOAD_TEMPLATE"
PAYLOAD_MESSAGE="template://${PAYLOAD_TEMPLATE}"
trap 'rm -f "$PAYLOAD_TEMPLATE"' EXIT

usage() {
  cat <<'EOF'
Usage: mqtt-emqtt-bench.sh [options]

  --host HOST                 MQTT broker host (default 127.0.0.1)
  --port PORT                 MQTT broker port (default 1883)
  --devices N                 ISPF device topics 1..N (default 4)
  --messages-per-second RATE  Total publish rate across all topics (default 2000)
  --duration-seconds SEC      Run time (default 60)
  --interval-ms MS            emqtt-bench -I per client (default 10 → 100 msg/s per client)
  --topic-prefix PREFIX       Topic prefix (default ispf/loadtest)
  --topic TOPIC               Single topic (overrides --devices layout)
  --payload-size BYTES        Payload size (default 8, similar to "24.582")
  --image IMAGE               Docker image (default emqx/emqtt-bench:latest)
  --pull                      docker pull before run
  -h, --help                  This help
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --host) HOST="$2"; shift 2 ;;
    --port) PORT="$2"; shift 2 ;;
    --devices) DEVICES="$2"; shift 2 ;;
    --messages-per-second) MESSAGES_PER_SECOND="$2"; shift 2 ;;
    --duration-seconds) DURATION_SECONDS="$2"; shift 2 ;;
    --interval-ms) INTERVAL_MS="$2"; shift 2 ;;
    --topic-prefix) TOPIC_PREFIX="$2"; shift 2 ;;
    --topic) SINGLE_TOPIC="$2"; shift 2 ;;
    --payload-size) PAYLOAD_SIZE="$2"; shift 2 ;;
    --image) IMAGE="$2"; shift 2 ;;
    --pull) DO_PULL=1; shift ;;
    -h | --help) usage; exit 0 ;;
    *) echo "Unknown option: $1" >&2; usage >&2; exit 2 ;;
  esac
done

if ! command -v docker >/dev/null 2>&1; then
  echo "docker is required" >&2
  exit 1
fi

if [[ "$DEVICES" -lt 1 ]]; then
  echo "--devices must be >= 1" >&2
  exit 1
fi

PAD="$DEVICES"
if [[ "$PAD" -lt 5 ]]; then
  PAD=5
fi

if [[ -n "$SINGLE_TOPIC" ]]; then
  DEVICES=1
  PER_TOPIC_RATE="$MESSAGES_PER_SECOND"
else
  PER_TOPIC_RATE=$(awk -v total="$MESSAGES_PER_SECOND" -v dev="$DEVICES" 'BEGIN { printf "%.6f", total / dev }')
fi
CLIENTS_PER_TOPIC=$(awk -v rate="$PER_TOPIC_RATE" -v interval="$INTERVAL_MS" \
  'BEGIN { n = rate * interval / 1000; if (n < 1) n = 1; printf "%d", (n == int(n) ? n : int(n)+1) }')
ACTUAL_PER_TOPIC=$(awk -v c="$CLIENTS_PER_TOPIC" -v interval="$INTERVAL_MS" \
  'BEGIN { printf "%.1f", c * (1000 / interval) }')
if [[ -n "$SINGLE_TOPIC" ]]; then
  ACTUAL_TOTAL="$ACTUAL_PER_TOPIC"
else
  ACTUAL_TOTAL=$(awk -v per="$ACTUAL_PER_TOPIC" -v dev="$DEVICES" 'BEGIN { printf "%.1f", per * dev }')
fi

if ! command -v timeout >/dev/null 2>&1; then
  echo "timeout(1) is required (coreutils)" >&2
  exit 1
fi

if [[ "${DO_PULL:-0}" == 1 ]]; then
  echo "Pulling $IMAGE ..."
  docker pull "$IMAGE"
fi

echo "emqtt-bench: ${DEVICES} topics × ${CLIENTS_PER_TOPIC} clients × ${INTERVAL_MS}ms interval"
echo "  target ~${MESSAGES_PER_SECOND} msg/s (actual ~${ACTUAL_TOTAL} msg/s) for ${DURATION_SECONDS}s"
if [[ -n "$SINGLE_TOPIC" ]]; then
  echo "  broker ${HOST}:${PORT}  topic ${SINGLE_TOPIC}  payload=${PAYLOAD_SIZE}B"
else
  echo "  broker ${HOST}:${PORT}  topic ${TOPIC_PREFIX}/<pad>/temperature  payload=${PAYLOAD_SIZE}B"
fi

started=$(date +%s)
pids=()

for i in $(seq 1 "$DEVICES"); do
  if [[ -n "$SINGLE_TOPIC" ]]; then
    topic="$SINGLE_TOPIC"
  else
    idx=$(printf "%0${PAD}d" "$i")
    topic="${TOPIC_PREFIX}/${idx}/temperature"
  fi
  timeout "${DURATION_SECONDS}s" docker run --rm --network host \
    -v "${PAYLOAD_TEMPLATE}:/payload.txt:ro" \
    "$IMAGE" pub \
    -h "$HOST" -p "$PORT" \
    -V "$MQTT_VERSION" \
    -c "$CLIENTS_PER_TOPIC" \
    -I "$INTERVAL_MS" \
    -t "$topic" \
    -m "template:///payload.txt" \
    -s "$PAYLOAD_SIZE" \
    -q 0 \
    >/dev/null 2>&1 &
  pids+=("$!")
done

for pid in "${pids[@]}"; do
  wait "$pid" || true
done
failed=0

elapsed=$(( $(date +%s) - started ))
if [[ "$elapsed" -lt 1 ]]; then
  elapsed=1
fi
approx_sent=$(awk -v total="$ACTUAL_TOTAL" -v sec="$elapsed" \
  'BEGIN { printf "%.0f", total * sec }')

echo "Done: ~${approx_sent} messages in ${elapsed}s (~$(awk -v s="$approx_sent" -v e="$elapsed" 'BEGIN { if (e>0) printf "%.1f", s/e; else print 0}') msg/s), failed_workers=${failed}"
exit "$failed"
