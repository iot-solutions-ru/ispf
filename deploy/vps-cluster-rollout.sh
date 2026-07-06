#!/usr/bin/env bash
# Rollout staged jar+UI to full ADR-0032 VPS cluster (edge-api, hmi-read, io, compute).
set -euo pipefail
STAGING="${1:-/opt/ispf/staging/0.9.92}"
INSTALL_ROOT="${ISPF_INSTALL_ROOT:-/opt/ispf}"
COMPOSE_FILE="${INSTALL_ROOT}/docker-compose.vps-cluster.yml"
BASE="http://127.0.0.1:8080"

log() { echo "==> $*"; }

if [[ ! -f "$STAGING/ispf-server.jar" || ! -f "$STAGING/web-console.zip" ]]; then
  echo "Missing staging artifacts in $STAGING" >&2
  exit 1
fi

if docker compose version >/dev/null 2>&1; then
  COMPOSE=(docker compose -f "$COMPOSE_FILE")
elif command -v docker-compose >/dev/null 2>&1; then
  COMPOSE=(docker-compose -f "$COMPOSE_FILE")
else
  echo "ERROR: docker compose required" >&2
  exit 1
fi

log "Stop systemd fallback"
systemctl stop ispf-server 2>/dev/null || true

log "Install jar"
install -m 644 "$STAGING/ispf-server.jar" "$INSTALL_ROOT/ispf-server.jar"

log "Install UI"
TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT
unzip -oq "$STAGING/web-console.zip" -d "$TMP"
install -d -m 755 "$INSTALL_ROOT/web-console"
rm -rf "$INSTALL_ROOT/web-console"/*
if [[ -d "$TMP/dist" ]]; then
  cp -a "$TMP/dist"/. "$INSTALL_ROOT/web-console/"
else
  cp -a "$TMP"/. "$INSTALL_ROOT/web-console/"
fi
chmod 755 "$INSTALL_ROOT/web-console"
chmod -R a+rX "$INSTALL_ROOT/web-console"

if [[ -f "$STAGING/driver-packs.tar.gz" ]]; then
  log "Driver packs"
  install -d "$INSTALL_ROOT/data/drivers"
  tar -xzf "$STAGING/driver-packs.tar.gz" -C "$INSTALL_ROOT/data/drivers"
fi

REPAIR="$INSTALL_ROOT/bin/vps-flyway-repair.sh"
if [[ -x "$REPAIR" ]]; then
  log "Flyway repair"
  "$REPAIR" "$INSTALL_ROOT/ispf-server.jar" || true
fi

grep -q '^ISPF_BOOTSTRAP_FIXTURES_ENABLED=' "$INSTALL_ROOT/ispf-server.env" 2>/dev/null \
  && sed -i 's/^ISPF_BOOTSTRAP_FIXTURES_ENABLED=.*/ISPF_BOOTSTRAP_FIXTURES_ENABLED=false/' "$INSTALL_ROOT/ispf-server.env" \
  || echo 'ISPF_BOOTSTRAP_FIXTURES_ENABLED=false' >> "$INSTALL_ROOT/ispf-server.env"

log "Remove legacy containers"
docker ps -a --format '{{.Names}}' | grep -E 'ispf-vps-replica|ispf-vps-nginx|ispf-vps-worker|ispf-nats|_ispf-vps-|_ispf-nats' | while read -r c; do
  docker rm -f "$c" 2>/dev/null || true
done

PRUNE="$INSTALL_ROOT/bin/vps-cluster-prune-stale.sh"
if [[ -x "$PRUNE" ]]; then
  log "Prune stale cluster registry rows"
  "$PRUNE" || true
fi

log "Starting NATS"
"${COMPOSE[@]}" up -d nats
sleep 2

log "Starting edge-api leader (replica-1)"
"${COMPOSE[@]}" up -d ispf-server-1
for i in $(seq 1 90); do
  login_code="$(curl -s -o /dev/null -w '%{http_code}' -X POST "http://127.0.0.1:8081/api/v1/auth/login" \
    -H 'Content-Type: application/json' \
    -d '{"username":"admin","password":"admin"}' 2>/dev/null || echo 000)"
  if [[ "$login_code" = "200" ]]; then
    log "replica-1 login OK (direct :8081)"
    break
  fi
  if [[ "$i" -eq 90 ]]; then
    docker logs ispf-vps-replica-1 --tail 60
    exit 1
  fi
  sleep 3
done

log "Starting hmi-read, io, compute + nginx"
"${COMPOSE[@]}" up -d ispf-server-2 ispf-server-3 ispf-server-worker-1 nginx

log "Waiting for full profile cluster"
for i in $(seq 1 120); do
  R1=$(curl -sf "http://127.0.0.1:8081/api/v1/info" \
    | python3 -c "import json,sys; print(json.load(sys.stdin).get('replicaProfile',''))" 2>/dev/null || true)
  R2=$(curl -sf "http://127.0.0.1:8082/api/v1/info" \
    | python3 -c "import json,sys; print(json.load(sys.stdin).get('replicaProfile',''))" 2>/dev/null || true)
  R3=$(curl -sf "http://127.0.0.1:8083/api/v1/info" \
    | python3 -c "import json,sys; print(json.load(sys.stdin).get('replicaProfile',''))" 2>/dev/null || true)
  W1=$(curl -sf "http://127.0.0.1:8084/api/v1/info" \
    | python3 -c "import json,sys; print(json.load(sys.stdin).get('replicaProfile',''))" 2>/dev/null || true)
  if [[ "$R1" == "edge-api" && "$R2" == "hmi-read" && "$R3" == "io" && "$W1" == "compute" ]]; then
    log "Cluster ready: edge-api hmi-read io compute"
    break
  fi
  if [[ "$i" -eq 120 ]]; then
    echo "ERROR: cluster not ready r1=$R1 r2=$R2 r3=$R3 w1=$W1" >&2
    "${COMPOSE[@]}" ps
    "${COMPOSE[@]}" logs --tail 40 ispf-server-1 ispf-server-2 ispf-server-3 ispf-server-worker-1 nginx
    exit 1
  fi
  sleep 3
done

INFO=$(curl -sf "${BASE}/api/v1/info")
echo "$INFO" | python3 -m json.tool | head -15
CLUSTER=$(echo "$INFO" | python3 -c "import json,sys; print(json.load(sys.stdin).get('clusterEnabled', False))")
if [[ "$CLUSTER" != "True" ]]; then
  echo "ERROR: clusterEnabled is not true" >&2
  exit 1
fi

curl -sf -o /dev/null -w "HTTPS root: %{http_code}\n" https://ispf.iot-solutions.ru/
log "Rollout complete"
