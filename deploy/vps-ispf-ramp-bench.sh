#!/usr/bin/env bash
# Progressive load test: 1 MQTT device, EVENT_JOURNAL_ONLY, increasing emqtt rate.
set -euo pipefail

DIR=/opt/ispf/loadtest
ENV_FILE="${ISPF_ENV_FILE:-/opt/ispf/ispf-server.env}"
VENV="${DIR}/venv/bin/python"
SETUP_DEVICES="${DIR}/setup-mqtt-event-journal-devices.py"
BASE_URL="${BASE_URL:-http://127.0.0.1:8080}"
DEVICE="${DEVICE:-root.platform.devices.loadtest-mqtt-dev-00001}"
TOPIC="${TOPIC:-ispf/loadtest/00001/temperature}"
BROKER="${BROKER:-tcp://127.0.0.1:1883}"
LOG="${LOG:-/opt/ispf/loadtest/ispf-ramp.log}"
PHASE_DURATION="${PHASE_DURATION:-60}"
COOLDOWN="${COOLDOWN:-10}"

exec > >(tee "$LOG") 2>&1
echo "=== ISPF ramp load test $(date -Is) ==="

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

run_step() {
  local step="$1"
  local label="$2"
  local duration="$3"
  local clients="$4"
  local interval="$5"
  echo ""
  echo "========== STEP ${step}: ${label} (${duration}s, ${clients}c, ${interval}ms) =========="
  local before_fired before_queue before_fallback
  before_fired=$(events_fired_total)
  before_queue=$(automation_metric eventJournalQueueSize)
  before_fallback=$(automation_metric eventJournalSyncFallbackTotal)
  echo "before: eventsFired=${before_fired} queue=${before_queue} syncFallback=${before_fallback}"
  run_emqtt "$duration" "$clients" "$interval"
  local settle=8
  if [ "$interval" -le 5 ]; then
    settle=20
  fi
  echo "settle=${settle}s..."
  sleep "$settle"
  local after_fired after_queue after_fallback after_flushed
  after_fired=$(events_fired_total)
  after_queue=$(automation_metric eventJournalQueueSize)
  after_fallback=$(automation_metric eventJournalSyncFallbackTotal)
  after_flushed=$(automation_metric eventJournalFlushedTotal)
  local delta_fired=$((after_fired - before_fired))
  local delta_fallback=$((after_fallback - before_fallback))
  local rate_fired
  rate_fired=$(awk -v d="$delta_fired" -v t="$duration" 'BEGIN { printf "%.1f", d/t }')
  echo "RESULT step=${step} clients=${clients} interval_ms=${interval} duration=${duration}s"
  echo "  eventsFired_delta=${delta_fired} eventsFired_per_sec=${rate_fired}"
  echo "  eventJournalQueueSize=${after_queue} syncFallback_delta=${delta_fallback}"
  echo "  eventJournalFlushedTotal=${after_flushed}"
  docker stats --no-stream --format '  docker {{.Name}} cpu={{.CPUPerc}} mem={{.MemUsage}}' ispf-scylla 2>/dev/null || true
  echo "RAMP_ROW|${step}|${clients}|${interval}|${duration}|${delta_fired}|${rate_fired}|${after_queue}|${delta_fallback}"
}

echo "Starting dependencies..."
for c in ispf-scylla ispf-postgres ispf-redis ispf-mqtt-loadtest; do
  docker start "$c" 2>/dev/null && echo "started $c" || echo "skip $c"
done
for i in $(seq 1 60); do
  docker exec ispf-scylla cqlsh -e "SELECT now() FROM system.local" >/dev/null 2>&1 && break
  sleep 2
done
systemctl restart ispf-server
for i in $(seq 1 90); do
  curl -sf -X POST "${BASE_URL}/api/v1/auth/login" \
    -H 'Content-Type: application/json' \
    -d '{"username":"admin","password":"admin"}' >/tmp/ispf-ramp-token.json 2>/dev/null && break
  sleep 3
done
TOKEN=$(python3 -c "import json; print(json.load(open('/tmp/ispf-ramp-token.json'))['token'])")
curl -sf "${BASE_URL}/api/v1/info" -H "Authorization: Bearer ${TOKEN}" | python3 -c "import json,sys; d=json.load(sys.stdin); print('version', d.get('version'))" || true

echo "Seeding 1 MQTT device (EVENT_JOURNAL_ONLY, no L0 coalesce)..."
"$VENV" "$SETUP_DEVICES" \
  --base-url "$BASE_URL" \
  --broker-url "$BROKER" \
  --devices 1 \
  --telemetry-coalesce-ms 1 \
  --bench-no-l0-coalesce
sleep 3
curl -sf "${BASE_URL}/api/v1/drivers/runtime/status?devicePath=${DEVICE}" \
  -H "Authorization: Bearer ${TOKEN}" | python3 -c "import json,sys; d=json.load(sys.stdin); print('driver', d.get('status'))" 2>/dev/null || true

D="$PHASE_DURATION"
C="$COOLDOWN"

# Progressive steps: clients × interval → approximate publish rate
run_step 1 "warmup"     30  4   100
sleep "$C"
run_step 2 "light"      "$D"  8   50
sleep "$C"
run_step 3 "medium"     "$D"  16  20
sleep "$C"
run_step 4 "sustained"  "$D"  20  10
sleep "$C"
run_step 5 "high"       "$D"  32  5
sleep "$C"
run_step 6 "peak"       "$D"  32  1

echo ""
echo "=== RAMP SUMMARY ==="
python3 - "$LOG" <<'PY'
import sys
path = sys.argv[1]
rows = []
try:
    with open(path, encoding="utf-8", errors="replace") as f:
        lines = f.readlines()
except OSError:
    lines = []
for line in lines:
    if not line.startswith("RAMP_ROW|"):
        continue
    p = line.strip().split("|")
    rows.append({
        "step": int(p[1]), "clients": int(p[2]), "interval_ms": int(p[3]),
        "duration_s": int(p[4]), "delta": int(p[5]), "rate": float(p[6]),
        "queue": p[7], "fallback_delta": int(p[8]),
    })
if not rows:
    print("(no RAMP_ROW lines parsed)")
else:
    print(f"{'step':>4} {'clients':>7} {'int_ms':>6} {'rate/s':>10} {'queue':>8} {'fb_delta':>8}")
    for r in rows:
        print(f"{r['step']:4d} {r['clients']:7d} {r['interval_ms']:6d} {r['rate']:10.1f} {r['queue']:>8} {r['fallback_delta']:8d}")
    peak = max(rows, key=lambda r: r["rate"])
    print(f"peak step {peak['step']}: {peak['rate']:.1f}/s ({peak['clients']}c x {peak['interval_ms']}ms)")
PY

echo ""
echo "=== DONE $(date -Is) ==="
docker stats --no-stream --format '{{.Name}} {{.CPUPerc}} {{.MemUsage}}' ispf-scylla 2>/dev/null || true
free -h | head -2
