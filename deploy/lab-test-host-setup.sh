#!/usr/bin/env bash
# Bootstrap ISPF lab stack under ~/ispf (no root required — Docker only).
set -euo pipefail

LAB_ROOT="${ISPF_LAB_ROOT:-$HOME/ispf}"
COMPOSE_FILE="$LAB_ROOT/lab-test-host-compose.yml"
HTTP_PORT="${ISPF_LAB_HTTP_PORT:-8000}"

cd "$LAB_ROOT"

echo "=== ISPF lab setup in $LAB_ROOT ==="
mkdir -p mqtt loadtest data/drivers bin

if [ ! -f mqtt/mosquitto.conf ]; then
  cat > mqtt/mosquitto.conf <<'EOF'
listener 1883
allow_anonymous true
persistence false
log_type error
EOF
fi

sync_staging_artifacts() {
  if [ -f staging/ispf-server.jar ]; then
    cp -f staging/ispf-server.jar ispf-server.jar
  fi
  if [ -d staging/web-console ]; then
    mkdir -p web-console
    rm -rf web-console/*
    cp -a staging/web-console/. web-console/
  fi
}

sync_staging_artifacts

if [ -f staging/driver-packs.tar.gz ] && [ ! -f data/drivers/.extracted ]; then
  echo "=== Extract driver packs ==="
  tar -xzf staging/driver-packs.tar.gz -C data/drivers
  touch data/drivers/.extracted
fi

for script in mqtt-loadtest-publisher.py mqtt_loadtest_lib.py mqtt-emqtt-bench.sh health-check.sh vps-cassandra-verify.sh; do
  if [ -f "staging/$script" ]; then
    cp "staging/$script" loadtest/
  fi
done
chmod +x loadtest/*.sh 2>/dev/null || true

echo "=== Pull images ==="
docker compose -f "$COMPOSE_FILE" pull

echo "=== Start data services ==="
docker compose -f "$COMPOSE_FILE" up -d postgres scylla mqtt

echo "=== Wait for Scylla CQL ==="
SCYLLA_CID=$(docker compose -f "$COMPOSE_FILE" ps -q scylla)
for i in $(seq 1 90); do
  if docker exec "$SCYLLA_CID" cqlsh -e "SELECT now() FROM system.local" >/dev/null 2>&1; then
    echo "Scylla CQL ready"
    break
  fi
  if [ "$i" -eq 90 ]; then
    echo "Scylla did not become ready" >&2
    docker compose -f "$COMPOSE_FILE" logs --tail 40 scylla
    exit 1
  fi
  sleep 3
done

echo "=== Create keyspace ispf ==="
docker exec "$SCYLLA_CID" cqlsh -e \
  "CREATE KEYSPACE IF NOT EXISTS ispf WITH replication = {'class': 'SimpleStrategy', 'replication_factor': 1};"

echo "=== Stage server artifacts ==="
sync_staging_artifacts

echo "=== Start ISPF + nginx ==="
export ISPF_BOOTSTRAP_FIXTURES_ENABLED="${ISPF_BOOTSTRAP_FIXTURES_ENABLED:-false}"
docker compose -f "$COMPOSE_FILE" up -d --force-recreate ispf-server nginx

echo "=== Wait for ISPF (via nginx :${HTTP_PORT}) ==="
for i in $(seq 1 90); do
  if curl -sf "http://127.0.0.1:${HTTP_PORT}/api/v1/info" >/dev/null 2>&1; then
    echo "ISPF API ready (attempt $i)"
    break
  fi
  if [ "$i" -eq 90 ]; then
    echo "ISPF did not become ready" >&2
    docker compose -f "$COMPOSE_FILE" logs --tail 80 ispf-server
    exit 1
  fi
  sleep 5
done

echo "=== API info ==="
curl -sf "http://127.0.0.1:${HTTP_PORT}/api/v1/info" | head -c 600
echo

echo "=== Smoke login ==="
TOKEN=$(curl -sf -X POST "http://127.0.0.1:${HTTP_PORT}/api/v1/auth/login" \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin"}' | python3 -c "import sys,json; print(json.load(sys.stdin).get('token',''))")
if [ -z "$TOKEN" ]; then
  echo "WARN: admin login failed" >&2
else
  echo "admin login OK"
  curl -sf -H "Authorization: Bearer $TOKEN" \
    "http://127.0.0.1:${HTTP_PORT}/api/v1/objects/by-path?path=root.platform.devices.demo-sensor-01" \
    | head -c 300
  echo
fi

echo "=== Containers ==="
docker compose -f "$COMPOSE_FILE" ps

echo
echo "Lab ready:"
echo "  UI:  http://$(hostname -I | awk '{print $1}'):${HTTP_PORT}/"
echo "  API: http://127.0.0.1:${HTTP_PORT}/api/v1/info"
echo "  MQTT (internal): tcp://mqtt:1883"
