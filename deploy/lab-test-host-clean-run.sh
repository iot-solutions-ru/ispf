#!/usr/bin/env bash
# Factory reset lab stack under ~/ispf (Docker volumes + Scylla keyspace), then benchmark.
set -euo pipefail

LAB_ROOT="${ISPF_LAB_ROOT:-$HOME/ispf}"
COMPOSE_FILE="$LAB_ROOT/lab-test-host-compose.yml"
ENV_FILE="$LAB_ROOT/lab-stress.env"
COMPOSE=(docker compose -f "$COMPOSE_FILE")
if [[ -f "$ENV_FILE" ]]; then
  COMPOSE+=(--env-file "$ENV_FILE")
fi
HTTP_PORT="${ISPF_LAB_HTTP_PORT:-8000}"
LOADTEST="$LAB_ROOT/loadtest"
LOG="$LOADTEST/clean-run.log"
MULTI_TEST="$LAB_ROOT/lab-mqtt-event-journal-multi-test.sh"

DEVICES="${DEVICES:-16}"
RATE_PER_DEVICE="${RATE_PER_DEVICE:-5200}"
WARMUP="${WARMUP:-25}"
PHASE="${PHASE:-90}"

cd "$LAB_ROOT"

echo "=== Stop emqtt orphans ==="
bash "$LAB_ROOT/lab-emqtt-cleanup.sh" 2>/dev/null || true

echo "=== Stop stack and wipe data volumes ==="
"${COMPOSE[@]}" down -v 2>/dev/null || true
rm -f "$LAB_ROOT/data/drivers/.extracted" 2>/dev/null || true

echo "=== Start postgres + scylla + mqtt ==="
ISPF_LAB_HTTP_PORT="$HTTP_PORT" \
ISPF_BOOTSTRAP_FIXTURES_ENABLED=false \
"${COMPOSE[@]}" up -d postgres scylla mqtt

echo "=== Wait for Scylla ==="
SCYLLA_CID=$("${COMPOSE[@]}" ps -q scylla)
for i in $(seq 1 90); do
  if docker exec "$SCYLLA_CID" cqlsh -e "SELECT now() FROM system.local" >/dev/null 2>&1; then
    break
  fi
  [ "$i" -eq 90 ] && { echo "Scylla timeout"; exit 1; }
  sleep 3
done
docker exec "$SCYLLA_CID" cqlsh -e \
  "CREATE KEYSPACE IF NOT EXISTS ispf WITH replication = {'class': 'SimpleStrategy', 'replication_factor': 1};"

echo "=== Start ISPF (fixtures off, local token auth) ==="
export ISPF_BOOTSTRAP_FIXTURES_ENABLED=false
ISPF_LAB_HTTP_PORT="$HTTP_PORT" \
"${COMPOSE[@]}" up -d ispf-server nginx

echo "=== Wait for API on :${HTTP_PORT} ==="
for i in $(seq 1 90); do
  if curl -sf "http://127.0.0.1:${HTTP_PORT}/api/v1/info" >/dev/null 2>&1; then
    break
  fi
  [ "$i" -eq 90 ] && { "${COMPOSE[@]}" logs --tail 40 ispf-server; exit 1; }
  sleep 5
done
curl -sf "http://127.0.0.1:${HTTP_PORT}/api/v1/info" | head -c 300
echo

echo "=== Wait for admin login (bootstrap may lag /api/v1/info) ==="
for i in $(seq 1 60); do
  if curl -sf -X POST "http://127.0.0.1:${HTTP_PORT}/api/v1/auth/login" \
      -H 'Content-Type: application/json' \
      -d '{"username":"admin","password":"admin"}' >/dev/null 2>&1; then
    echo "login OK"
    break
  fi
  [ "$i" -eq 60 ] && { echo "login timeout after bootstrap"; exit 1; }
  sleep 2
done

NODES=$("${COMPOSE[@]}" exec -T postgres psql -U ispf -d ispf -t -A -c "SELECT COUNT(*) FROM object_nodes;" 2>/dev/null || echo "?")
echo "object_nodes after bootstrap: ${NODES}"

echo "=== Stop interfering processes ==="
pkill -f lab-mqtt-event-journal-multi-test || true
pkill -f mqtt-event-journal-multi-test || true
sleep 2

echo "=== Run benchmark: ${DEVICES}×${RATE_PER_DEVICE} msg/s (${WARMUP}s warmup + ${PHASE}s measure) ==="
: > "$LOG"
chmod +x "$MULTI_TEST" "$LAB_ROOT/lab-emqtt-cleanup.sh" "$LOADTEST/mqtt-emqtt-bench.sh"
env DEVICES="$DEVICES" RATE_PER_DEVICE="$RATE_PER_DEVICE" WARMUP="$WARMUP" PHASE="$PHASE" \
  AUTO_CALIBRATE=false EMQTT_SHARD_MAX="${EMQTT_SHARD_MAX:-4}" \
  ISPF_LAB_HTTP_PORT="$HTTP_PORT" \
  bash "$MULTI_TEST" 2>&1 | tee "$LOG"
