#!/usr/bin/env bash
# Rollout staged jar+UI without docker-compose recreate (avoids ContainerConfig bug on old compose).
set -euo pipefail
STAGING="${1:-/opt/ispf/staging/0.9.92}"
INSTALL_ROOT="${ISPF_INSTALL_ROOT:-/opt/ispf}"
BASE="http://127.0.0.1:8080"

log() { echo "==> $*"; }

if [[ ! -f "$STAGING/ispf-server.jar" || ! -f "$STAGING/web-console.zip" ]]; then
  echo "Missing staging artifacts in $STAGING" >&2
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

log "Remove legacy multi-node containers"
docker ps -a --format '{{.Names}}' | grep -E 'ispf-vps-replica|ispf-vps-nginx|ispf-vps-worker|ispf-nats|_ispf-vps-|_ispf-nats' | while read -r c; do
  docker rm -f "$c" 2>/dev/null || true
done

log "Recreate single-node stack"
COMPOSE_FILE="${INSTALL_ROOT}/docker-compose.vps-cluster.yml"
if docker compose version >/dev/null 2>&1; then
  docker compose -f "$COMPOSE_FILE" up -d ispf-server-1 2>/dev/null \
    || docker-compose -f "$COMPOSE_FILE" up -d ispf-server-1
elif command -v docker-compose >/dev/null 2>&1; then
  docker-compose -f "$COMPOSE_FILE" up -d ispf-server-1
else
  echo "ERROR: docker compose required" >&2
  exit 1
fi

log "Start nginx (docker run — compose recreate hits ContainerConfig on v1.29)"
docker rm -f ispf-vps-nginx 2>/dev/null || true
docker run -d --name ispf-vps-nginx --network host --restart unless-stopped \
  -v "$INSTALL_ROOT/web-console:/usr/share/nginx/html:ro" \
  -v "$INSTALL_ROOT/nginx-vps-cluster.conf:/etc/nginx/conf.d/default.conf:ro" \
  nginx:1.27.4-alpine

log "Prune stale cluster registry rows"
PRUNE="$INSTALL_ROOT/bin/vps-cluster-prune-stale.sh"
if [[ -x "$PRUNE" ]]; then
  "$PRUNE" || true
fi

log "Wait for unified node (nginx → replica-1)"
for i in $(seq 1 90); do
  INFO=$(curl -sf --no-keepalive -H "Connection: close" "${BASE}/api/v1/info" 2>/dev/null || true)
  RID=$(echo "$INFO" | python3 -c "import json,sys; print(json.load(sys.stdin).get('replicaId',''))" 2>/dev/null || true)
  CLUSTER=$(echo "$INFO" | python3 -c "import json,sys; print(json.load(sys.stdin).get('clusterEnabled', True))" 2>/dev/null || true)
  ROLE=$(echo "$INFO" | python3 -c "import json,sys; print(json.load(sys.stdin).get('replicaRole',''))" 2>/dev/null || true)
  if [[ "$RID" == "replica-1" && "$CLUSTER" == "False" && "$ROLE" == "all" ]]; then
    log "Unified node ready: $RID role=$ROLE cluster=$CLUSTER"
    break
  fi
  [[ "$i" -eq 90 ]] && { echo "ERROR: unified node not ready (rid=$RID cluster=$CLUSTER role=$ROLE)"; docker ps; docker logs ispf-vps-replica-1 2>&1 | tail -30; exit 1; }
  sleep 3
done

curl -sf "${BASE}/api/v1/info" | python3 -m json.tool | head -15
curl -sf -o /dev/null -w "HTTPS root: %{http_code}\n" https://ispf.iot-solutions.ru/
log "Rollout complete"
