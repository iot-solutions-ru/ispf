#!/usr/bin/env bash
# Migrate prod VPS to full ADR-0032 Docker cluster (edge-api, hmi-read, io, compute).
set -euo pipefail

STAGING_DIR="${1:-}"
INSTALL_ROOT="${ISPF_INSTALL_ROOT:-/opt/ispf}"
COMPOSE_FILE="${INSTALL_ROOT}/docker-compose.vps-cluster.yml"
SERVICE_NAME="${ISPF_SERVICE_NAME:-ispf-server}"
BASE="http://127.0.0.1:8080"

if docker compose version >/dev/null 2>&1; then
  COMPOSE=(docker compose -f "$COMPOSE_FILE")
elif command -v docker-compose >/dev/null 2>&1; then
  COMPOSE=(docker-compose -f "$COMPOSE_FILE")
else
  echo "ERROR: docker compose or docker-compose required" >&2
  exit 1
fi

if [[ -n "$STAGING_DIR" ]]; then
  JAR_PATH="$STAGING_DIR/ispf-server.jar"
  UI_ZIP="$STAGING_DIR/web-console.zip"
  DRIVER_PACKS_TAR="$STAGING_DIR/driver-packs.tar.gz"
  if [[ ! -f "$JAR_PATH" || ! -f "$UI_ZIP" ]]; then
    echo "Usage: $0 [/opt/ispf/staging/<version>]" >&2
    exit 1
  fi
fi

log() { echo "==> $*"; }

install_artifacts() {
  log "Installing jar + UI from $STAGING_DIR"
  install -d -m 755 "$INSTALL_ROOT/data" "$INSTALL_ROOT/web-console"
  install -m 644 "$JAR_PATH" "$INSTALL_ROOT/ispf-server.jar"

  TMP_UI="$(mktemp -d)"
  trap 'rm -rf "$TMP_UI"' RETURN
  unzip -oq "$UI_ZIP" -d "$TMP_UI"
  rm -rf "$INSTALL_ROOT/web-console"/*
  if [[ -d "$TMP_UI/dist" ]]; then
    cp -a "$TMP_UI/dist"/. "$INSTALL_ROOT/web-console/"
  else
    cp -a "$TMP_UI"/. "$INSTALL_ROOT/web-console/"
  fi
  chmod -R a+rX "$INSTALL_ROOT/web-console"
  find "$INSTALL_ROOT/web-console" -type d -exec chmod 755 {} +

  if [[ -f "$DRIVER_PACKS_TAR" ]]; then
    install -d "$INSTALL_ROOT/data/drivers"
    tar -xzf "$DRIVER_PACKS_TAR" -C "$INSTALL_ROOT/data/drivers"
    log "Driver packs updated"
  fi

  REPAIR_SCRIPT="$INSTALL_ROOT/bin/vps-flyway-repair.sh"
  if [[ -x "$REPAIR_SCRIPT" ]]; then
    log "Flyway repair"
    "$REPAIR_SCRIPT" "$INSTALL_ROOT/ispf-server.jar" || true
  fi
}

upsert_env() {
  local key="$1" value="$2"
  touch "$INSTALL_ROOT/ispf-server.env"
  chmod 600 "$INSTALL_ROOT/ispf-server.env"
  if grep -q "^${key}=" "$INSTALL_ROOT/ispf-server.env" 2>/dev/null; then
    sed -i "s|^${key}=.*|${key}=${value}|" "$INSTALL_ROOT/ispf-server.env"
  else
    echo "${key}=${value}" >> "$INSTALL_ROOT/ispf-server.env"
  fi
}

log "Stopping systemd $SERVICE_NAME (cluster takes over :8080)"
systemctl stop "$SERVICE_NAME" 2>/dev/null || true
systemctl disable "$SERVICE_NAME" 2>/dev/null || true

if [[ -n "$STAGING_DIR" ]]; then
  install_artifacts
fi

log "Ensuring prod cluster env flags"
upsert_env ISPF_BOOTSTRAP_FIXTURES_ENABLED false
upsert_env ISPF_CLUSTER_JOB_CONSUMER_ENABLED false

log "Remove legacy single-node / stale containers"
docker ps -a --format '{{.Names}}' | grep -E 'ispf-vps-replica|ispf-vps-nginx|ispf-vps-worker|ispf-nats|_ispf-vps-|_ispf-nats' | while read -r c; do
  docker rm -f "$c" 2>/dev/null || true
done

PRUNE="$INSTALL_ROOT/bin/vps-cluster-prune-stale.sh"
if [[ -x "$PRUNE" ]]; then
  "$PRUNE" || true
fi

log "Pulling cluster images"
"${COMPOSE[@]}" pull --ignore-pull-failures 2>/dev/null || true

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
declare -A SEEN=()
for i in $(seq 1 120); do
  SEEN=()
  for _ in $(seq 1 20); do
    RID=$(curl -sf --no-keepalive -H "Connection: close" "${BASE}/api/v1/info" \
      | python3 -c "import json,sys; print(json.load(sys.stdin).get('replicaId',''))" 2>/dev/null || true)
    [[ -n "$RID" ]] && SEEN["$RID"]=1
  done
  R1=$(curl -sf "http://127.0.0.1:8081/api/v1/info" \
    | python3 -c "import json,sys; print(json.load(sys.stdin).get('replicaProfile',''))" 2>/dev/null || true)
  R2=$(curl -sf "http://127.0.0.1:8082/api/v1/info" \
    | python3 -c "import json,sys; print(json.load(sys.stdin).get('replicaProfile',''))" 2>/dev/null || true)
  R3=$(curl -sf "http://127.0.0.1:8083/api/v1/info" \
    | python3 -c "import json,sys; d=json.load(sys.stdin); print(d.get('replicaProfile',''))" 2>/dev/null || true)
  W1=$(curl -sf "http://127.0.0.1:8084/api/v1/info" \
    | python3 -c "import json,sys; d=json.load(sys.stdin); print(d.get('replicaProfile',''))" 2>/dev/null || true)
  if [[ "$R1" == "edge-api" && "$R2" == "hmi-read" && "$R3" == "io" && "$W1" == "compute" ]]; then
    log "Cluster ready: edge-api hmi-read io compute"
    break
  fi
  if [[ "$i" -eq 120 ]]; then
    echo "ERROR: expected api>=2 + io + compute, api=${#SEEN[@]} r3=$R3 w1=$W1" >&2
    "${COMPOSE[@]}" ps
    "${COMPOSE[@]}" logs --tail 40 ispf-server-1 ispf-server-2 ispf-server-3 ispf-server-worker-1 nginx
    exit 1
  fi
  sleep 3
done

INFO=$(curl -sf "${BASE}/api/v1/info")
echo "$INFO" | python3 -m json.tool | head -20
CLUSTER=$(echo "$INFO" | python3 -c "import json,sys; print(json.load(sys.stdin).get('clusterEnabled', False))")
if [[ "$CLUSTER" != "True" ]]; then
  echo "ERROR: clusterEnabled is not true" >&2
  exit 1
fi

TOKEN=$(curl -sf -X POST "${BASE}/api/v1/auth/login" \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin"}' \
  | python3 -c "import json,sys; print(json.load(sys.stdin).get('token',''))")
if [[ -z "$TOKEN" ]]; then
  echo "ERROR: admin login failed" >&2
  exit 1
fi
curl -sf -H "Authorization: Bearer ${TOKEN}" "${BASE}/api/v1/platform/cluster/health" | python3 -m json.tool

log "VPS full cluster bootstrap complete"
