#!/usr/bin/env bash
# Factory reset for VPS Docker cluster (3 replicas + shared PostgreSQL).
# Usage: vps-cluster-factory-reset.sh [--fixtures|--no-fixtures]
set -euo pipefail

ENV_FILE="${ISPF_ENV_FILE:-/opt/ispf/ispf-server.env}"
COMPOSE_FILE="${ISPF_CLUSTER_COMPOSE_FILE:-/opt/ispf/docker-compose.vps-cluster.yml}"
BASE="http://127.0.0.1:8080"
FIXTURES=false

if docker compose version >/dev/null 2>&1; then
  COMPOSE=(docker compose -f "$COMPOSE_FILE")
elif command -v docker-compose >/dev/null 2>&1; then
  COMPOSE=(docker-compose -f "$COMPOSE_FILE")
else
  echo "ERROR: docker compose required" >&2
  exit 1
fi

for arg in "$@"; do
  case "$arg" in
    --fixtures) FIXTURES=true ;;
    --no-fixtures|--skip-fixtures) FIXTURES=false ;;
    -h|--help)
      echo "Usage: $0 [--fixtures|--no-fixtures]"
      exit 0
      ;;
    *)
      echo "Unknown argument: $arg" >&2
      exit 1
      ;;
  esac
done

upsert_env() {
  local key="$1" value="$2"
  touch "$ENV_FILE"
  chmod 600 "$ENV_FILE"
  if grep -q "^${key}=" "$ENV_FILE" 2>/dev/null; then
    sed -i "s|^${key}=.*|${key}=${value}|" "$ENV_FILE"
  else
    echo "${key}=${value}" >> "$ENV_FILE"
  fi
}

log() { echo "==> $*"; }

log "Bootstrap fixtures: $FIXTURES"
upsert_env ISPF_BOOTSTRAP_FIXTURES_ENABLED "$FIXTURES"

log "Stop cluster replicas"
docker stop ispf-vps-replica-1 ispf-vps-replica-2 ispf-vps-replica-3 ispf-vps-worker-1 ispf-nats 2>/dev/null || true
docker rm ispf-vps-replica-1 ispf-vps-replica-2 ispf-vps-replica-3 ispf-vps-worker-1 ispf-nats 2>/dev/null || true
systemctl stop ispf-server 2>/dev/null || true

log "Drop and recreate PostgreSQL database ispf"
docker exec ispf-postgres psql -U ispf -d postgres -v ON_ERROR_STOP=1 \
  -c 'DROP DATABASE IF EXISTS ispf;' \
  -c 'CREATE DATABASE ispf OWNER ispf;'

log "Truncate ClickHouse event journal (if present)"
if docker ps --format '{{.Names}}' | grep -qx ispf-clickhouse; then
  docker exec ispf-clickhouse clickhouse-client --query "TRUNCATE TABLE IF EXISTS ispf.event_history" 2>/dev/null || true
fi

log "Reset Scylla keyspace (if present)"
if docker ps --format '{{.Names}}' | grep -qx ispf-scylla; then
  docker exec ispf-scylla cqlsh -e "DROP KEYSPACE IF EXISTS ispf;" 2>/dev/null || true
  for i in $(seq 1 30); do
    if docker exec ispf-scylla cqlsh -e "SELECT now() FROM system.local" >/dev/null 2>&1; then
      break
    fi
    sleep 2
  done
  docker exec ispf-scylla cqlsh -e \
    "CREATE KEYSPACE IF NOT EXISTS ispf WITH replication = {'class': 'SimpleStrategy', 'replication_factor': 1};"
fi

log "Flush Redis (cluster path interest + caches)"
if docker ps --format '{{.Names}}' | grep -qx ispf-redis; then
  docker exec ispf-redis redis-cli FLUSHALL >/dev/null || true
fi

log "Start NATS + replica-1 (Flyway + bootstrap leader)"
"${COMPOSE[@]}" up -d nats
sleep 2
"${COMPOSE[@]}" up -d ispf-server-1
for i in $(seq 1 90); do
  login_code="$(curl -s -o /dev/null -w '%{http_code}' -X POST "${BASE}/api/v1/auth/login" \
    -H 'Content-Type: application/json' \
    -d '{"username":"admin","password":"admin"}' 2>/dev/null || echo 000)"
  node_count="$(docker exec ispf-postgres psql -U ispf -d ispf -t -A -c \
    "SELECT COUNT(*) FROM object_nodes WHERE path='root.platform';" 2>/dev/null || echo 0)"
  if [ "$login_code" = "200" ] && [ "${node_count:-0}" -ge 1 ]; then
    log "Bootstrap OK on replica-1 (login=$login_code, root.platform=$node_count)"
    break
  fi
  if [ "$i" -eq 90 ]; then
    docker logs ispf-vps-replica-1 --tail 60
    exit 1
  fi
  sleep 3
done

log "Start replica-2, replica-3, worker-1, nginx"
"${COMPOSE[@]}" up -d ispf-server-2 ispf-server-3 ispf-server-worker-1 nginx

log "Wait for api pool (edge-api + hmi-read) + io + compute"
declare -A SEEN=()
for i in $(seq 1 60); do
  SEEN=()
  for _ in $(seq 1 15); do
    RID=$(curl -sf --no-keepalive -H "Connection: close" "${BASE}/api/v1/info" \
      | python3 -c "import json,sys; print(json.load(sys.stdin).get('replicaId',''))" 2>/dev/null || true)
    [[ -n "$RID" ]] && SEEN["$RID"]=1
  done
  R3=$(curl -sf "http://127.0.0.1:8083/api/v1/info" \
    | python3 -c "import json,sys; d=json.load(sys.stdin); print(d.get('replicaProfile',''))" 2>/dev/null || true)
  W1=$(curl -sf "http://127.0.0.1:8084/api/v1/info" \
    | python3 -c "import json,sys; d=json.load(sys.stdin); print(d.get('replicaProfile',''))" 2>/dev/null || true)
  if [[ ${#SEEN[@]} -ge 2 && "$R3" == "io" && "$W1" == "compute" ]]; then
    log "Cluster ready: api=${!SEEN[*]} io=$R3 compute=$W1"
    break
  fi
  [[ "$i" -eq 60 ]] && { docker ps; exit 1; }
  sleep 3
done

log "Post-reset counts"
docker exec ispf-postgres psql -U ispf -d ispf -c \
  "SELECT 'object_nodes' AS tbl, COUNT(*) FROM object_nodes
   UNION ALL SELECT 'object_variables', COUNT(*) FROM object_variables;"

curl -sf "${BASE}/api/v1/info" | python3 -m json.tool | head -12
curl -sf -o /dev/null -w "HTTPS: %{http_code}\n" https://ispf.iot-solutions.ru/
log "Cluster factory reset complete (fixtures=$FIXTURES)"
