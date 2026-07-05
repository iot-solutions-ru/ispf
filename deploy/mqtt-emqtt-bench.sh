#!/usr/bin/env bash
# High-rate MQTT publisher for ISPF load tests (emqx/emqtt-bench via Docker).
#
# Publishes to ispf/loadtest/00001/temperature … (same layout as mqtt-loadtest-publisher.py).
#
# Lab (recommended): one container on compose network, broker mqtt:1883 — no docker-proxy.
#   bash mqtt-emqtt-bench.sh --docker-network ispf-lab_default --host mqtt --single-container ...
#
# VPS / host broker: default --network host --host 127.0.0.1
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
DOCKER_NETWORK="${DOCKER_NETWORK:-}"
SINGLE_CONTAINER="${SINGLE_CONTAINER:-false}"
SHARD_MAX="${SHARD_MAX:-4}"
SHARED_TOPIC_SHARDS="${SHARED_TOPIC_SHARDS:-1}"
CLEANUP_STALE="${CLEANUP_STALE:-true}"
# Cap Erlang CPU per shard so bench cannot starve ispf-server / scylla on shared lab host.
EMQTT_CPU_LIMIT="${EMQTT_CPU_LIMIT:-1.5}"
EMQTT_BENCH_LABEL="${EMQTT_BENCH_LABEL:-ispf.emqtt-bench}"

PAYLOAD_TEMPLATE="$(mktemp /tmp/ispf-emqtt-payload.XXXXXX)"

prepare_payload_template() {
  if [[ "${NUMERIC_PAYLOAD:-false}" == "true" ]]; then
    printf '25.0' >"$PAYLOAD_TEMPLATE"
    PAYLOAD_SIZE=4
  else
    echo '%TIMESTAMPMS%' >"$PAYLOAD_TEMPLATE"
  fi
}

prepare_payload_template

usage() {
  cat <<'EOF'
Usage: mqtt-emqtt-bench.sh [options]

  --host HOST                 MQTT broker host (lab: mqtt, VPS: 127.0.0.1)
  --port PORT                 MQTT broker port (default 1883)
  --devices N                 ISPF device topics 1..N (default 4)
  --messages-per-second RATE  Total publish rate across all topics
  --duration-seconds SEC      Run time (default 60)
  --interval-ms MS            emqtt-bench -I per client (default 10)
  --topic-prefix PREFIX       Topic prefix (default ispf/loadtest)
  --topic TOPIC               Single topic (overrides --devices layout)
  --docker-network NET        Attach publisher to Docker network (skip docker-proxy)
  --single-container          Few docker runs via --topics-payload (default max 4 shards)
  --shard-max N               Max emqtt docker containers when --single-container (default 4)
  --shared-topic-shards N     Parallel publishers on --topic (default 1; use 4+ for high rate)
  --no-cleanup-stale          Do not stop orphaned emqtt-bench containers
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
    --docker-network) DOCKER_NETWORK="$2"; shift 2 ;;
    --single-container) SINGLE_CONTAINER=true; shift ;;
    --shard-max) SHARD_MAX="$2"; shift 2 ;;
    --shared-topic-shards) SHARED_TOPIC_SHARDS="$2"; shift 2 ;;
    --no-cleanup-stale) CLEANUP_STALE=false; shift ;;
    --pull) DO_PULL=1; shift ;;
    -h | --help) usage; exit 0 ;;
    *) echo "Unknown option: $1" >&2; usage >&2; exit 2 ;;
  esac
done

# Historian load tests: plain numeric MQTT body via multi-container template mode.
if [[ "${NUMERIC_PAYLOAD:-false}" == "true" ]]; then
  SINGLE_CONTAINER=false
fi

if ! command -v docker >/dev/null 2>&1; then
  echo "docker is required" >&2
  exit 1
fi

if [[ "$DEVICES" -lt 1 ]]; then
  echo "--devices must be >= 1" >&2
  exit 1
fi

trap 'cleanup_stale_emqtt' EXIT INT TERM

cleanup_stale_emqtt() {
  if [[ "$CLEANUP_STALE" != "true" ]]; then
    return 0
  fi
  local ids
  ids=$(docker ps -q --filter "label=${EMQTT_BENCH_LABEL}=1" 2>/dev/null || true)
  if [[ -z "$ids" ]]; then
    ids=$(docker ps -q --filter "ancestor=${IMAGE}" 2>/dev/null || true)
  fi
  if [[ -n "$ids" ]]; then
    echo "Stopping stale emqtt-bench containers: $(echo "$ids" | wc -w)"
    # shellcheck disable=SC2086
    docker stop -t 2 $ids >/dev/null 2>&1 || true
  fi
  rm -f "$PAYLOAD_TEMPLATE" 2>/dev/null || true
}

docker_network_args() {
  if [[ -n "$DOCKER_NETWORK" ]]; then
    NET_ARGS=(--network "$DOCKER_NETWORK")
  else
    NET_ARGS=(--network host)
  fi
}

write_topics_json() {
  local out="$1"
  local from="$2"
  local to="$3"
  TOPIC_PREFIX="$TOPIC_PREFIX" PAD="$PAD" INTERVAL_MS="$INTERVAL_MS" NUMERIC_PAYLOAD="${NUMERIC_PAYLOAD:-false}" \
    python3 - "$out" "$from" "$to" <<'PY'
import json, os, sys

out, start, end = sys.argv[1], int(sys.argv[2]), int(sys.argv[3])
prefix = os.environ["TOPIC_PREFIX"]
pad = int(os.environ["PAD"])
interval_ms = os.environ["INTERVAL_MS"]
numeric = os.environ.get("NUMERIC_PAYLOAD", "false").lower() in ("1", "true", "yes")
topics = []
for i in range(start, end + 1):
    idx = f"{i:0{pad}d}"
    entry = {
        "name": f"{prefix}/{idx}/temperature",
        "interval_ms": str(interval_ms),
        "QoS": 0,
        "retain": False,
        "stream": 0,
        "stream_priority": 0,
    }
    if numeric:
        # Bare JSON number in MQTT body (no inject_timestamp — historian parses temperature.raw as double)
        entry["payload_encoding"] = "json"
        entry["payload"] = 25.0
    else:
        entry["inject_timestamp"] = "ms"
        entry["payload_encoding"] = "json"
        entry["payload"] = {"v": 1}
    topics.append(entry)
with open(out, "w", encoding="utf-8") as f:
    json.dump({"topics": topics}, f)
PY
}

# Match mqtt_loadtest_lib: pad = max(5, len(str(DEVICES)))
resolve_publish_params() {
  local rate=$1
  local interval_ms=$2
  RESOLVED_CLIENTS=$(awk -v rate="$rate" -v interval="$interval_ms" \
    'BEGIN { n = rate * interval / 1000; if (n < 1) n = 1; printf "%d", (n == int(n) ? n : int(n)+1) }')
  RESOLVED_INTERVAL="$interval_ms"
  local capacity
  capacity=$(awk -v c="$RESOLVED_CLIENTS" -v interval="$interval_ms" \
    'BEGIN { printf "%.6f", c * (1000 / interval) }')
  if awk -v cap="$capacity" -v rate="$rate" 'BEGIN { exit (cap > rate + 0.001) ? 0 : 1 }'; then
    RESOLVED_INTERVAL=$(awk -v rate="$rate" -v c="$RESOLVED_CLIENTS" \
      'BEGIN { ms = (c * 1000) / rate; if (ms < 1) ms = 1; printf "%.0f", ms }')
  fi
  RESOLVED_RATE=$(awk -v c="$RESOLVED_CLIENTS" -v interval="$RESOLVED_INTERVAL" \
    'BEGIN { printf "%.1f", c * (1000 / interval) }')
}

PAD=5
DEVICES_LEN=${#DEVICES}
if [[ "$DEVICES_LEN" -gt 5 ]]; then
  PAD="$DEVICES_LEN"
fi

if [[ -n "$SINGLE_TOPIC" ]]; then
  DEVICES=1
  if [[ "$SHARED_TOPIC_SHARDS" -gt 1 ]]; then
    PER_TOPIC_RATE=$(awk -v total="$MESSAGES_PER_SECOND" -v shards="$SHARED_TOPIC_SHARDS" \
      'BEGIN { printf "%.6f", total / shards }')
  else
    PER_TOPIC_RATE="$MESSAGES_PER_SECOND"
  fi
else
  PER_TOPIC_RATE=$(awk -v total="$MESSAGES_PER_SECOND" -v dev="$DEVICES" 'BEGIN { printf "%.6f", total / dev }')
fi
CLIENTS_PER_TOPIC=$(awk -v rate="$PER_TOPIC_RATE" -v interval="$INTERVAL_MS" \
  'BEGIN { n = rate * interval / 1000; if (n < 1) n = 1; printf "%d", (n == int(n) ? n : int(n)+1) }')
ACTUAL_PER_TOPIC=$(awk -v c="$CLIENTS_PER_TOPIC" -v interval="$INTERVAL_MS" \
  'BEGIN { printf "%.1f", c * (1000 / interval) }')
if [[ -n "$SINGLE_TOPIC" ]]; then
  if [[ "$SHARED_TOPIC_SHARDS" -gt 1 ]]; then
    shard_rate=$(awk -v total="$MESSAGES_PER_SECOND" -v shards="$SHARED_TOPIC_SHARDS" \
      'BEGIN { printf "%.0f", total / shards }')
    resolve_publish_params "$shard_rate" "$INTERVAL_MS"
    ACTUAL_TOTAL=$(awk -v per="$RESOLVED_RATE" -v shards="$SHARED_TOPIC_SHARDS" \
      'BEGIN { printf "%.1f", per * shards }')
  else
    resolve_publish_params "$MESSAGES_PER_SECOND" "$INTERVAL_MS"
    ACTUAL_TOTAL="$RESOLVED_RATE"
  fi
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

cleanup_stale_emqtt

prepare_payload_template

mode="multi-container"
shard_count="$DEVICES"
if [[ -n "$SINGLE_TOPIC" && "$SHARED_TOPIC_SHARDS" -gt 1 ]]; then
  mode="shared-topic-sharded"
  shard_count="$SHARED_TOPIC_SHARDS"
elif [[ "$SINGLE_CONTAINER" == "true" ]]; then
  mode="sharded-topics-payload"
  if [[ "$DEVICES" -lt "$SHARD_MAX" ]]; then
    shard_count="$DEVICES"
  else
    shard_count="$SHARD_MAX"
  fi
fi
net_label="${DOCKER_NETWORK:-host}"
echo "emqtt-bench (${mode}, ${shard_count} docker run(s)): ${DEVICES} topics × ${CLIENTS_PER_TOPIC} clients × ${INTERVAL_MS}ms"
echo "  configured target ~${MESSAGES_PER_SECOND} msg/s (formula estimate ~${ACTUAL_TOTAL} msg/s — not measured) for ${DURATION_SECONDS}s"
echo "  network ${net_label}  broker ${HOST}:${PORT}  payload=${PAYLOAD_SIZE}B"
echo "ISPF_EMQTT_FORMULA_RATE=${ACTUAL_TOTAL}"
echo "ISPF_EMQTT_CLIENTS_PER_TOPIC=${CLIENTS_PER_TOPIC}"
echo "ISPF_EMQTT_SHARD_COUNT=${shard_count}"

run_single_topic_pub() {
  local topic=$1
  local rate=$2
  resolve_publish_params "$rate" "$INTERVAL_MS"
  timeout "${DURATION_SECONDS}s" docker run --rm "${NET_ARGS[@]}" \
    --label "${EMQTT_BENCH_LABEL}=1" \
    --cpus "${EMQTT_CPU_LIMIT}" \
    -v "${PAYLOAD_TEMPLATE}:/payload.txt:ro" \
    "$IMAGE" pub \
    -h "$HOST" -p "$PORT" \
    -V "$MQTT_VERSION" \
    -c "$RESOLVED_CLIENTS" \
    -I "$RESOLVED_INTERVAL" \
    -t "$topic" \
    -m "template:///payload.txt" \
    -s "$PAYLOAD_SIZE" \
    -q 0 \
    >/dev/null 2>&1 &
  pids+=("$!")
}

docker_network_args
started=$(date +%s)
failed=0
pids=()

if [[ -n "$SINGLE_TOPIC" && "$SHARED_TOPIC_SHARDS" -gt 1 ]]; then
  shard_rate=$(awk -v total="$MESSAGES_PER_SECOND" -v shards="$SHARED_TOPIC_SHARDS" \
    'BEGIN { printf "%.0f", total / shards }')
  resolve_publish_params "$shard_rate" "$INTERVAL_MS"
  ACTUAL_TOTAL=$(awk -v per="$RESOLVED_RATE" -v shards="$SHARED_TOPIC_SHARDS" \
    'BEGIN { printf "%.1f", per * shards }')
  for _ in $(seq 1 "$SHARED_TOPIC_SHARDS"); do
    run_single_topic_pub "$SINGLE_TOPIC" "$shard_rate"
  done
  for pid in "${pids[@]}"; do
    wait "$pid" || true
  done
elif [[ "$SINGLE_CONTAINER" == "true" ]]; then
  topics_per_shard=$(( (DEVICES + shard_count - 1) / shard_count ))
  json_files=()
  pids=()
  shard=0
  for ((i = 1; i <= DEVICES; i += topics_per_shard)); do
    shard=$((shard + 1))
    end=$((i + topics_per_shard - 1))
    if [[ "$end" -gt "$DEVICES" ]]; then
      end="$DEVICES"
    fi
    json_file="$(mktemp "/tmp/ispf-emqtt-topics-${shard}.XXXXXX.json")"
    json_files+=("$json_file")
    write_topics_json "$json_file" "$i" "$end"
    set +e
    timeout "${DURATION_SECONDS}s" docker run --rm "${NET_ARGS[@]}" \
      --label "${EMQTT_BENCH_LABEL}=1" \
      --cpus "${EMQTT_CPU_LIMIT}" \
      -v "${json_file}:/topics.json:ro" \
      "$IMAGE" pub \
      -h "$HOST" -p "$PORT" \
      -V "$MQTT_VERSION" \
      -c "$CLIENTS_PER_TOPIC" \
      -i "$INTERVAL_MS" \
      --topics-payload /topics.json \
      -q 0 \
      >/dev/null 2>&1 &
    pids+=("$!")
    set -e
  done
  for pid in "${pids[@]}"; do
    set +e
    wait "$pid"
    rc=$?
    set -e
    if [[ "$rc" -ne 0 && "$rc" -ne 124 ]]; then
      failed=$((failed + 1))
    fi
  done
  for json_file in "${json_files[@]}"; do
    rm -f "$json_file"
  done
else
  for i in $(seq 1 "$DEVICES"); do
    if [[ -n "$SINGLE_TOPIC" ]]; then
      topic="$SINGLE_TOPIC"
    else
      idx=$(printf "%0${PAD}d" "$i")
      topic="${TOPIC_PREFIX}/${idx}/temperature"
    fi
    run_single_topic_pub "$topic" "$PER_TOPIC_RATE"
  done
  for pid in "${pids[@]}"; do
    wait "$pid" || true
  done
fi

cleanup_stale_emqtt

elapsed=$(( $(date +%s) - started ))
if [[ "$elapsed" -lt 1 ]]; then
  elapsed=1
fi
approx_sent=$(awk -v total="$ACTUAL_TOTAL" -v sec="$elapsed" \
  'BEGIN { printf "%.0f", total * sec }')

formula_rate=$(awk -v s="$approx_sent" -v e="$elapsed" 'BEGIN { if (e>0) printf "%.1f", s/e; else print 0}')
echo "Done (formula estimate): ~${approx_sent} messages in ${elapsed}s (~${formula_rate} msg/s), failed_workers=${failed}"
echo "ISPF_EMQTT_FORMULA_RATE=${formula_rate}"
exit 0
