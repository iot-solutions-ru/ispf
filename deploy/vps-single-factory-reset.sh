#!/usr/bin/env bash
# Factory reset for VPS single unified node (replica-1 + nginx).
# Usage: vps-single-factory-reset.sh [--fixtures|--no-fixtures]
set -euo pipefail

ENV_FILE="${ISPF_ENV_FILE:-/opt/ispf/ispf-server.env}"
COMPOSE_FILE="${ISPF_CLUSTER_COMPOSE_FILE:-/opt/ispf/docker-compose.vps-cluster.yml}"
INSTALL_ROOT="${ISPF_INSTALL_ROOT:-/opt/ispf}"
BASE="http://127.0.0.1:8080"
FIXTURES=false

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
upsert_env ISPF_PLATFORM_METRICS_PROBE_ENABLED "false"

log "Stop ISPF replica"
docker stop ispf-vps-replica-1 2>/dev/null || true
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

log "Flush Redis"
if docker ps --format '{{.Names}}' | grep -qx ispf-redis; then
  docker exec ispf-redis redis-cli FLUSHALL >/dev/null || true
fi

log "Start replica-1 (Flyway + bootstrap)"
"${COMPOSE[@]}" up -d ispf-server-1

log "Wait for bootstrap"
for i in $(seq 1 90); do
  login_code="$(curl -s -o /dev/null -w '%{http_code}' -X POST "${BASE}/api/v1/auth/login" \
    -H 'Content-Type: application/json' \
    -d '{"username":"admin","password":"admin"}' 2>/dev/null || echo 000)"
  node_count="$(docker exec ispf-postgres psql -U ispf -d ispf -t -A -c \
    "SELECT COUNT(*) FROM object_nodes WHERE path='root.platform';" 2>/dev/null || echo 0)"
  if [ "$login_code" = "200" ] && [ "${node_count:-0}" -ge 1 ]; then
    log "Bootstrap OK (login=$login_code, root.platform=$node_count)"
    break
  fi
  if [ "$i" -eq 90 ]; then
    docker logs ispf-vps-replica-1 --tail 80
    exit 1
  fi
  sleep 3
done

PRUNE="$INSTALL_ROOT/bin/vps-cluster-prune-stale.sh"
if [[ -x "$PRUNE" ]]; then
  log "Prune stale cluster registry rows"
  "$PRUNE" || true
fi

log "Post-reset counts"
docker exec ispf-postgres psql -U ispf -d ispf -c \
  "SELECT 'object_nodes' AS tbl, COUNT(*) FROM object_nodes
   UNION ALL SELECT 'object_variables', COUNT(*) FROM object_variables
   UNION ALL SELECT 'variable_samples', COUNT(*) FROM variable_samples
   UNION ALL SELECT 'alert_rules', COUNT(*) FROM alert_rules
   UNION ALL SELECT 'event_history', COUNT(*) FROM event_history;"

curl -sf "${BASE}/api/v1/info" | python3 -m json.tool | head -12
curl -sf -o /dev/null -w "HTTPS: %{http_code}\n" https://ispf.iot-solutions.ru/
log "Single-node factory reset complete (fixtures=$FIXTURES)"
