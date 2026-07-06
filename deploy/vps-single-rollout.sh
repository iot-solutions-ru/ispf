#!/usr/bin/env bash
# Migrate VPS from multi-replica cluster to single unified node (full stack).
set -euo pipefail

STAGING="${1:-}"
INSTALL_ROOT="${ISPF_INSTALL_ROOT:-/opt/ispf}"
COMPOSE_FILE="${INSTALL_ROOT}/docker-compose.vps-single.yml"
BASE="http://127.0.0.1:8080"

log() { echo "==> $*"; }

if docker compose version >/dev/null 2>&1; then
  COMPOSE=(docker compose -f "$COMPOSE_FILE")
elif command -v docker-compose >/dev/null 2>&1; then
  COMPOSE=(docker-compose -f "$COMPOSE_FILE")
else
  echo "ERROR: docker compose required" >&2
  exit 1
fi

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

remove_env_key() {
  local key="$1"
  if [[ -f "$INSTALL_ROOT/ispf-server.env" ]]; then
    sed -i "/^${key}=/d" "$INSTALL_ROOT/ispf-server.env"
  fi
}

if [[ -n "$STAGING" && -d "$STAGING" ]]; then
  if [[ ! -f "$STAGING/ispf-server.jar" || ! -f "$STAGING/web-console.zip" ]]; then
    echo "Missing staging artifacts in $STAGING" >&2
    exit 1
  fi
  log "Install jar + UI from $STAGING"
  install -m 644 "$STAGING/ispf-server.jar" "$INSTALL_ROOT/ispf-server.jar"
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
  chmod -R a+rX "$INSTALL_ROOT/web-console"
  if [[ -f "$STAGING/driver-packs.tar.gz" ]]; then
    install -d "$INSTALL_ROOT/data/drivers"
    tar -xzf "$STAGING/driver-packs.tar.gz" -C "$INSTALL_ROOT/data/drivers"
  fi
  REPAIR="$INSTALL_ROOT/bin/vps-flyway-repair.sh"
  if [[ -x "$REPAIR" ]]; then
    log "Flyway repair"
    "$REPAIR" "$INSTALL_ROOT/ispf-server.jar" || true
  fi
fi

log "Stop systemd fallback"
systemctl stop ispf-server 2>/dev/null || true
systemctl disable ispf-server 2>/dev/null || true

log "Configure single unified env"
upsert_env ISPF_CLUSTER_ENABLED false
upsert_env ISPF_REPLICA_ROLE all
upsert_env ISPF_BOOTSTRAP_FIXTURES_ENABLED false
upsert_env ISPF_PLATFORM_METRICS_PROBE_ENABLED false
remove_env_key ISPF_REPLICA_PROFILE

log "Remove all cluster instances"
docker ps -a --format '{{.Names}}' | grep -E 'ispf-vps-replica|ispf-vps-nginx|ispf-vps-worker|ispf-nats|_ispf-vps-|_ispf-nats' | while read -r c; do
  docker rm -f "$c" 2>/dev/null || true
done

PRUNE="$INSTALL_ROOT/bin/vps-cluster-prune-stale.sh"
if [[ -x "$PRUNE" ]]; then
  log "Prune stale cluster registry rows"
  "$PRUNE" || true
fi

log "Start single unified stack (replica-1 + nginx)"
"${COMPOSE[@]}" up -d ispf-server-1 nginx

log "Wait for unified node"
for i in $(seq 1 90); do
  INFO=$(curl -sf --no-keepalive -H "Connection: close" "${BASE}/api/v1/info" 2>/dev/null || true)
  RID=$(echo "$INFO" | python3 -c "import json,sys; print(json.load(sys.stdin).get('replicaId',''))" 2>/dev/null || true)
  CLUSTER=$(echo "$INFO" | python3 -c "import json,sys; print(json.load(sys.stdin).get('clusterEnabled', True))" 2>/dev/null || true)
  ROLE=$(echo "$INFO" | python3 -c "import json,sys; print(json.load(sys.stdin).get('replicaRole',''))" 2>/dev/null || true)
  if [[ "$RID" == "replica-1" && "$CLUSTER" == "False" && "$ROLE" == "all" ]]; then
    log "Unified node ready: $RID role=$ROLE cluster=$CLUSTER"
    break
  fi
  if [[ "$i" -eq 90 ]]; then
    echo "ERROR: unified node not ready (rid=$RID cluster=$CLUSTER role=$ROLE)" >&2
    docker ps
    docker logs ispf-vps-replica-1 2>&1 | tail -40
    exit 1
  fi
  sleep 3
done

curl -sf "${BASE}/api/v1/info" | python3 -m json.tool | head -16
curl -sf -o /dev/null -w "HTTPS root: %{http_code}\n" https://ispf.iot-solutions.ru/
docker ps --format 'table {{.Names}}\t{{.Status}}' | grep -E 'ispf-vps|ispf-nats' || true
log "Single unified rollout complete"
