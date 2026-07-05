#!/usr/bin/env bash
# Progressive 1-device MQTT ramp on lab compose (EVENT_JOURNAL_ONLY → Scylla).
set -euo pipefail

LAB_ROOT="${ISPF_LAB_ROOT:-$HOME/ispf}"
COMPOSE_FILE="$LAB_ROOT/lab-test-host-compose.yml"
ENV_FILE="$LAB_ROOT/lab-stress.env"
HTTP_PORT="${ISPF_LAB_HTTP_PORT:-8000}"
DIR="$LAB_ROOT/loadtest"
VENV="${DIR}/venv/bin/python"
SETUP="${DIR}/setup-mqtt-event-journal-devices.py"
BASE_URL="http://127.0.0.1:${HTTP_PORT}"
DEVICE="${DEVICE:-root.platform.devices.loadtest-mqtt-dev-00001}"
TOPIC="${TOPIC:-ispf/loadtest/00001/temperature}"
MQTT_NETWORK="${EMQTT_DOCKER_NETWORK:-ispf-lab_default}"
LOG="${LOG:-$LAB_ROOT/loadtest/ispf-ramp.log}"
PHASE_DURATION="${PHASE_DURATION:-60}"
COOLDOWN="${COOLDOWN:-10}"
RAMP_FAST="${RAMP_FAST:-false}"
RAMP_TO_200K="${RAMP_TO_200K:-false}"
RAMP_TO_1M="${RAMP_TO_1M:-false}"
RAMP_FROM_STEP="${RAMP_FROM_STEP:-1}"
RAMP_STOP_ON_QUEUE="${RAMP_STOP_ON_QUEUE:-true}"
RAMP_ABORTED=false
if [ "$RAMP_TO_1M" = "true" ]; then
  RAMP_TO_200K=true
fi
if [ "$RAMP_FROM_STEP" -ge 11 ] && [ "$RAMP_FAST" = "false" ]; then
  RAMP_FAST=true
fi

if [ -f "$ENV_FILE" ]; then
  set -a
  # shellcheck disable=SC1090
  source "$ENV_FILE"
  set +a
fi

if [ "$RAMP_FAST" = "true" ]; then
  COOLDOWN=2
fi
echo "RAMP_FAST=${RAMP_FAST} COOLDOWN=${COOLDOWN}s PHASE_DURATION=${PHASE_DURATION}s RAMP_FROM_STEP=${RAMP_FROM_STEP} RAMP_STOP_ON_QUEUE=${RAMP_STOP_ON_QUEUE}"

if [ "$RAMP_FROM_STEP" -gt 1 ] && [ -f "$LOG" ]; then
  exec > >(tee -a "$LOG") 2>&1
else
  exec > >(tee "$LOG") 2>&1
fi
echo "=== ISPF lab ramp load test $(date -Is) ==="
echo "API: $BASE_URL  MQTT network: $MQTT_NETWORK  topic: $TOPIC"

if [ ! -x "$VENV" ]; then
  VENV=python3
fi

scylla_cid() {
  docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" ps -q scylla
}

automation_metric() {
  local key="$1"
  curl -sf "${BASE_URL}/api/v1/platform/metrics" -H "Authorization: Bearer ${TOKEN}" 2>/dev/null \
    | python3 -c "
import json, sys
key = sys.argv[1]
data = json.load(sys.stdin)
sections = data.get('sections', {})
if isinstance(sections, dict):
    auto = sections.get('automation', {})
    if isinstance(auto, dict):
        print(auto.get('values', {}).get(key, auto.get(key, 0)))
        raise SystemExit
for section in sections if isinstance(sections, list) else []:
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

journal_queue_size() {
  automation_metric eventJournalQueueSize
}

stop_emqtt_load() {
  local pid
  for pid in "$@"; do
    kill "$pid" 2>/dev/null || true
  done
  bash "$LAB_ROOT/lab-emqtt-cleanup.sh" 2>/dev/null || true
}

ensure_queue_empty() {
  local max="${1:-60}"
  local label="${2:-pre-step}"
  for _ in $(seq 1 "$max"); do
    local q
    q=$(journal_queue_size)
    if [ "${q:-0}" -eq 0 ] 2>/dev/null; then
      echo "  ${label}: eventJournalQueueSize=0"
      return 0
    fi
    echo "  ${label}: draining queue=${q}..."
    sleep 1
  done
  local q
  q=$(journal_queue_size)
  echo "  ${label}: queue still ${q} after ${max}s — abort ramp"
  RAMP_ABORTED=true
  echo "RAMP_ABORT|queue|phase=${label}|queue=${q}"
  return 1
}

run_emqtt() {
  local duration="$1"
  local clients="$2"
  local interval="$3"
  local shards="${4:-1}"
  local cpu="${EMQTT_CPU_LIMIT:-1.5}"
  local per=$(( (clients + shards - 1) / shards ))
  local formula_rate
  formula_rate=$(awk -v c="$clients" -v i="$interval" 'BEGIN { printf "%.0f", c * (1000 / i) }')
  echo "--- emqtt: ${duration}s clients=${clients} (${shards}×${per}) interval_ms=${interval} formula~${formula_rate} msg/s topic=${TOPIC} ---"
  local pids=()
  for _ in $(seq 1 "$shards"); do
    timeout "${duration}s" docker run --rm --network "$MQTT_NETWORK" \
      --cpus "$cpu" --label ispf.emqtt-bench=1 \
      emqx/emqtt-bench pub \
      -h mqtt -p 1883 -c "$per" -I "$interval" -t "$TOPIC" -m '{"v":42}' -q 0 \
      >/dev/null 2>&1 &
    pids+=("$!")
  done
  local elapsed=0
  local poll=2
  local actual_duration=0
  while [ "$elapsed" -lt "$duration" ]; do
    if [ "$RAMP_STOP_ON_QUEUE" = "true" ]; then
      local q
      q=$(journal_queue_size)
      if [ "${q:-0}" -gt 0 ] 2>/dev/null; then
        echo "QUEUE DETECTED eventJournalQueueSize=${q} at ${elapsed}s — stopping emqtt and ramp"
        stop_emqtt_load "${pids[@]}"
        RAMP_ABORTED=true
        actual_duration=$elapsed
        RUN_STEP_ACTUAL_DURATION=$actual_duration
        echo "RAMP_ABORT|queue|elapsed_s=${actual_duration}|queue=${q}"
        return 0
      fi
    fi
    sleep "$poll"
    elapsed=$((elapsed + poll))
  done
  actual_duration=$duration
  for pid in "${pids[@]}"; do
    wait "$pid" || true
  done
  RUN_STEP_ACTUAL_DURATION=$actual_duration
}

wait_queue_drain() {
  local max_wait="${1:-45}"
  if [ "$RAMP_ABORTED" = "true" ]; then
    echo "Skipping queue drain (ramp aborted on queue)"
    return 1
  fi
  echo "Waiting for event-journal queue drain (max ${max_wait}s)..."
  for _ in $(seq 1 "$max_wait"); do
    local q
    q=$(journal_queue_size)
    if [ "${q:-0}" -eq 0 ] 2>/dev/null; then
      echo "  eventJournalQueueSize=0"
      return 0
    fi
    if [ "$RAMP_STOP_ON_QUEUE" = "true" ] && [ "${q:-0}" -gt 0 ] 2>/dev/null; then
      echo "  eventJournalQueueSize=${q} — abort ramp (stop on queue)"
      RAMP_ABORTED=true
      echo "RAMP_ABORT|queue|phase=drain|queue=${q}"
      return 1
    fi
    sleep 1
  done
  local q
  q=$(journal_queue_size)
  echo "  queue still ${q} after ${max_wait}s"
  if [ "$RAMP_STOP_ON_QUEUE" = "true" ] && [ "${q:-0}" -gt 0 ] 2>/dev/null; then
    RAMP_ABORTED=true
    echo "RAMP_ABORT|queue|phase=drain_timeout|queue=${q}"
    return 1
  fi
}

run_step() {
  local step="$1"
  local label="$2"
  local duration="$3"
  local clients="$4"
  local interval="$5"
  local shards="${6:-1}"
  echo ""
  echo "========== STEP ${step}: ${label} (${duration}s, ${clients}c, ${interval}ms, ${shards} shard(s)) =========="
  bash "$LAB_ROOT/lab-emqtt-cleanup.sh" 2>/dev/null || true
  local before_fired before_queue before_fallback
  before_fired=$(events_fired_total)
  before_queue=$(automation_metric eventJournalQueueSize)
  before_fallback=$(automation_metric eventJournalSyncFallbackTotal)
  echo "before: eventsFired=${before_fired} queue=${before_queue} syncFallback=${before_fallback}"
  if [ "$RAMP_STOP_ON_QUEUE" = "true" ] && [ "${before_queue:-0}" -gt 0 ] 2>/dev/null; then
    echo "QUEUE already ${before_queue} before step — waiting to drain..."
    ensure_queue_empty 120 "pre_step_${step}" || return 1
    before_queue=0
  fi
  if [ "$RAMP_STOP_ON_QUEUE" = "true" ]; then
    ensure_queue_empty 30 "pre_load_${step}" || return 1
  fi
  RUN_STEP_ACTUAL_DURATION=$duration
  run_emqtt "$duration" "$clients" "$interval" "$shards"
  local measure_duration=$duration
  if [ -n "${RUN_STEP_ACTUAL_DURATION+set}" ]; then
    measure_duration=$RUN_STEP_ACTUAL_DURATION
  fi
  local settle=8
  if [ "$interval" -le 5 ]; then
    settle=20
  fi
  if [ "$clients" -ge 80 ]; then
    settle=25
  fi
  if [ "$clients" -ge 500 ]; then
    settle=35
  fi
  if [ "$clients" -ge 800 ]; then
    settle=45
  fi
  if [ "$RAMP_FAST" = "true" ]; then
    settle=$(( settle * 2 / 5 ))
    [ "$settle" -lt 5 ] && settle=5
  fi
  echo "settle=${settle}s..."
  local s
  for s in $(seq 1 "$settle"); do
    if [ "$RAMP_ABORTED" = "true" ]; then
      break
    fi
    if [ "$RAMP_STOP_ON_QUEUE" = "true" ]; then
      local q
      q=$(journal_queue_size)
      if [ "${q:-0}" -gt 0 ] 2>/dev/null; then
        echo "QUEUE during settle (${s}s): eventJournalQueueSize=${q} — abort ramp"
        RAMP_ABORTED=true
        echo "RAMP_ABORT|queue|phase=settle|queue=${q}"
        break
      fi
    fi
    sleep 1
  done
  local drain_max=30
  if [ "$clients" -ge 80 ]; then
    drain_max=120
  fi
  if [ "$clients" -ge 500 ]; then
    drain_max=240
  fi
  if [ "$clients" -ge 800 ]; then
    drain_max=360
  fi
  if [ "$RAMP_FAST" = "true" ]; then
    drain_max=$(( drain_max * 2 / 5 ))
    [ "$drain_max" -lt 30 ] && drain_max=30
  fi
  wait_queue_drain "$drain_max"
  local after_fired after_queue after_fallback after_flushed
  after_fired=$(events_fired_total)
  after_queue=$(automation_metric eventJournalQueueSize)
  after_fallback=$(automation_metric eventJournalSyncFallbackTotal)
  after_flushed=$(automation_metric eventJournalFlushedTotal)
  local delta_fired=$((after_fired - before_fired))
  local delta_fallback=$((after_fallback - before_fallback))
  local rate_fired
  rate_fired=$(awk -v d="$delta_fired" -v t="$measure_duration" 'BEGIN { if (t > 0) printf "%.1f", d/t; else print "0.0" }')
  echo "RESULT step=${step} clients=${clients} interval_ms=${interval} duration=${measure_duration}s (planned=${duration}s)"
  echo "  eventsFired_delta=${delta_fired} eventsFired_per_sec=${rate_fired}"
  echo "  eventJournalQueueSize=${after_queue} syncFallback_delta=${delta_fallback}"
  echo "  eventJournalFlushedTotal=${after_flushed}"
  docker stats --no-stream --format '  docker {{.Name}} cpu={{.CPUPerc}} mem={{.MemUsage}}' \
    "$(docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" ps -q ispf-server)" \
    "$(scylla_cid)" 2>/dev/null || true
  echo "RAMP_ROW|${step}|${clients}|${interval}|${measure_duration}|${delta_fired}|${rate_fired}|${after_queue}|${delta_fallback}"
  if [ "$RAMP_ABORTED" = "true" ]; then
    return 1
  fi
}

bash "$LAB_ROOT/lab-emqtt-cleanup.sh" 2>/dev/null || true
cd "$LAB_ROOT"
if [ "$RAMP_FROM_STEP" -ge 11 ]; then
  echo "RAMP_FROM_STEP=${RAMP_FROM_STEP}: skip compose up (stack already running)"
else
  docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" up -d scylla mqtt ispf-server nginx
fi

login_attempts=90
[ "$RAMP_FROM_STEP" -ge 12 ] && login_attempts=5
for i in $(seq 1 "$login_attempts"); do
  if curl -sf -X POST "${BASE_URL}/api/v1/auth/login" \
    -H 'Content-Type: application/json' \
    -d '{"username":"admin","password":"admin"}' >"$DIR/ispf-ramp-token.json" 2>/dev/null; then
    echo "ISPF API ready attempt=$i"
    break
  fi
  sleep 3
done
TOKEN=$(python3 -c "import json; print(json.load(open('$DIR/ispf-ramp-token.json'))['token'])")
curl -sf "${BASE_URL}/api/v1/info" -H "Authorization: Bearer ${TOKEN}" \
  | python3 -c "import json,sys; d=json.load(sys.stdin); print('version', d.get('version'))" || true

if [ "$RAMP_FROM_STEP" -le 1 ]; then
  echo "Seeding 1 MQTT device (EVENT_JOURNAL_ONLY, bench-no-l0-coalesce)..."
  "$VENV" "$SETUP" \
    --base-url "$BASE_URL" \
    --broker-url "tcp://mqtt:1883" \
    --devices 1 \
    --telemetry-coalesce-ms 1 \
    --bench-no-l0-coalesce
  sleep 3
  curl -sf "${BASE_URL}/api/v1/drivers/runtime/status?devicePath=${DEVICE}" \
    -H "Authorization: Bearer ${TOKEN}" \
    | python3 -c "import json,sys; d=json.load(sys.stdin); print('driver', d.get('status'))" 2>/dev/null || true
else
  echo "RAMP_FROM_STEP=${RAMP_FROM_STEP}: skipping device seed (continue run)"
fi

D="$PHASE_DURATION"
C="$COOLDOWN"

maybe_step() {
  local step="$1"
  shift
  if [ "$RAMP_ABORTED" = "true" ]; then
    echo "skip step ${step} (ramp aborted: queue appeared)"
    return 0
  fi
  if [ "$step" -lt "$RAMP_FROM_STEP" ]; then
    echo "skip step ${step} (RAMP_FROM_STEP=${RAMP_FROM_STEP})"
    return 0
  fi
  run_step "$step" "$@" || true
  if [ "$RAMP_ABORTED" = "true" ]; then
    echo "=== RAMP STOPPED: event journal queue appeared (step ${step}) ==="
    return 0
  fi
  sleep "$C"
}

maybe_step 1 "warmup"     30  4   100
maybe_step 2 "light"      "$D"  8   50
maybe_step 3 "medium"     "$D"  16  20
maybe_step 4 "sustained"  "$D"  20  10
maybe_step 5 "high"       "$D"  32  5   1
maybe_step 6 "peak-32k"   "$D"  32  1   1

if [ "$RAMP_TO_200K" = "true" ]; then
  export EMQTT_CPU_LIMIT="${EMQTT_CPU_LIMIT:-2.5}"
  maybe_step 7  "50k-formula"  "$D"  50   1  2
  maybe_step 8  "80k-formula"  "$D"  80   1  4
  maybe_step 9  "110k-formula" "$D" 110   1  4
  maybe_step 10 "150k-formula" "$D" 150   1  6
  maybe_step 11 "200k-formula" "$D" 200   1  8
fi

if [ "$RAMP_TO_1M" = "true" ]; then
  export EMQTT_CPU_LIMIT="${EMQTT_CPU_LIMIT:-3.0}"
  maybe_step 12 "250k-formula" "$D" 250   1  10
  maybe_step 13 "300k-formula" "$D" 300   1  12
  maybe_step 14 "400k-formula" "$D" 400   1  16
  maybe_step 15 "500k-formula" "$D" 500   1  16
  maybe_step 16 "600k-formula" "$D" 600   1  20
  maybe_step 17 "750k-formula" "$D" 750   1  24
  maybe_step 18 "1M-formula"   "$D" 1000  1  32
fi

echo ""
echo "=== RAMP SUMMARY ==="
python3 - "$LOG" <<'PY'
import sys
path = sys.argv[1]
rows = []
by_step = {}
try:
    with open(path, encoding="utf-8", errors="replace") as f:
        lines = f.readlines()
except OSError:
    lines = []
for line in lines:
    if not line.startswith("RAMP_ROW|"):
        continue
    p = line.strip().split("|")
    step = int(p[1])
    row = {
        "step": step, "clients": int(p[2]), "interval_ms": int(p[3]),
        "duration_s": int(p[4]), "delta": int(p[5]), "rate": float(p[6]),
        "queue": p[7], "fallback_delta": int(p[8]),
    }
    by_step[step] = row
rows = [by_step[k] for k in sorted(by_step)]
if not rows:
    print("(no RAMP_ROW lines parsed)")
else:
    print(f"{'step':>4} {'clients':>7} {'int_ms':>6} {'rate/s':>10} {'queue':>8} {'fb_delta':>8}")
    for r in rows:
        print(f"{r['step']:4d} {r['clients']:7d} {r['interval_ms']:6d} {r['rate']:10.1f} {r['queue']:>8} {r['fallback_delta']:8d}")
    peak = max(rows, key=lambda r: r["rate"])
    print(f"peak step {peak['step']}: {peak['rate']:.1f}/s ({peak['clients']}c x {peak['interval_ms']}ms) queue={peak['queue']}")
    abort = [ln.strip() for ln in lines if ln.startswith("RAMP_ABORT|")]
    if abort:
        print(f"stopped on queue: {abort[-1]}")
PY

echo ""
echo "=== DONE $(date -Is) ==="
docker stats --no-stream --format '{{.Name}} {{.CPUPerc}} {{.MemUsage}}' \
  $(docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" ps -q) 2>/dev/null || true
