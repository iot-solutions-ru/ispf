#!/usr/bin/env bash
# Migrate prod VPS from systemd ispf-server to 3-replica Docker cluster.
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
  install -d "$INSTALL_ROOT/data" "$INSTALL_ROOT/web-console"
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

log "Stopping systemd $SERVICE_NAME (cluster takes over :8080)"
systemctl stop "$SERVICE_NAME" 2>/dev/null || true
systemctl disable "$SERVICE_NAME" 2>/dev/null || true

if [[ -n "$STAGING_DIR" ]]; then
  install_artifacts
fi

log "Ensuring prod cluster env flags"
grep -q '^ISPF_BOOTSTRAP_FIXTURES_ENABLED=' "$INSTALL_ROOT/ispf-server.env" 2>/dev/null \
  && sed -i 's/^ISPF_BOOTSTRAP_FIXTURES_ENABLED=.*/ISPF_BOOTSTRAP_FIXTURES_ENABLED=false/' "$INSTALL_ROOT/ispf-server.env" \
  || echo 'ISPF_BOOTSTRAP_FIXTURES_ENABLED=false' >> "$INSTALL_ROOT/ispf-server.env"

log "Attach Scylla/ClickHouse to ispf_default (legacy bridge mode; skipped for host-network replicas)"

log "Pulling cluster images"
"${COMPOSE[@]}" pull --ignore-pull-failures 2>/dev/null || true

log "Starting VPS cluster (3 replicas + NATS + nginx)"
"${COMPOSE[@]}" up -d --force-recreate

log "Waiting for >=3 unique replicaId via LB"
declare -A SEEN=()
for i in $(seq 1 120); do
  SEEN=()
  for _ in $(seq 1 18); do
    RID=$(curl -sf --no-keepalive -H "Connection: close" "${BASE}/api/v1/info" \
      | python3 -c "import json,sys; print(json.load(sys.stdin).get('replicaId',''))" 2>/dev/null || true)
    if [[ -n "$RID" ]]; then
      SEEN["$RID"]=1
    fi
  done
  UNIQUE=${#SEEN[@]}
  if [[ "$UNIQUE" -ge 3 ]]; then
    log "Cluster ready: ${UNIQUE} replicas (${i} attempts)"
    break
  fi
  if [[ "$i" -eq 120 ]]; then
    echo "ERROR: expected >=3 replicas, got ${UNIQUE}" >&2
    "${COMPOSE[@]}" ps
    "${COMPOSE[@]}" logs --tail 50 ispf-server-1 ispf-server-2 ispf-server-3 nginx
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

log "VPS 3-node cluster bootstrap complete"
