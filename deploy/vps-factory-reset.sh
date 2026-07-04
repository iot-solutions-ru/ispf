#!/bin/bash
# Factory reset: empty PostgreSQL + ClickHouse, then first-boot Flyway + platform bootstrap.
#
# Usage:
#   vps-factory-reset.sh [--fixtures|--no-fixtures]
#
# --no-fixtures (default): system catalogs + built-in models only (load-test baseline)
# --fixtures:            demo sensor, mini-TEC, lab devices, SNMP localhost
set -euo pipefail

ENV_FILE="${ISPF_ENV_FILE:-/opt/ispf/ispf-server.env}"
FIXTURES=false

for arg in "$@"; do
  case "$arg" in
    --fixtures) FIXTURES=true ;;
    --no-fixtures|--skip-fixtures) FIXTURES=false ;;
    -h|--help)
      sed -n '2,9p' "$0"
      exit 0
      ;;
    *)
      echo "Unknown argument: $arg" >&2
      exit 1
      ;;
  esac
done

upsert_env() {
  local key="$1"
  local value="$2"
  if [ ! -f "$ENV_FILE" ]; then
    touch "$ENV_FILE"
    chmod 600 "$ENV_FILE"
  fi
  if grep -q "^${key}=" "$ENV_FILE" 2>/dev/null; then
    sed -i "s|^${key}=.*|${key}=${value}|" "$ENV_FILE"
  else
    echo "${key}=${value}" >> "$ENV_FILE"
  fi
}

echo "=== Bootstrap fixtures: $FIXTURES ==="
upsert_env ISPF_BOOTSTRAP_FIXTURES_ENABLED "$FIXTURES"

echo "=== Stop ISPF ==="
systemctl stop ispf-server

echo "=== Drop and recreate PostgreSQL database ispf ==="
docker exec ispf-postgres psql -U ispf -d postgres -v ON_ERROR_STOP=1 \
  -c 'DROP DATABASE IF EXISTS ispf;' \
  -c 'CREATE DATABASE ispf OWNER ispf;'

echo "=== Truncate ClickHouse event journal (if present) ==="
if docker ps --format '{{.Names}}' | grep -qx ispf-clickhouse; then
  docker exec ispf-clickhouse clickhouse-client --query "TRUNCATE TABLE IF EXISTS ispf.event_history" 2>/dev/null || true
fi

echo "=== Reset Scylla/Cassandra keyspace (if present) ==="
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

echo "=== Start ISPF (Flyway + bootstrap) ==="
systemctl start ispf-server
for i in $(seq 1 60); do
  if curl -sf http://127.0.0.1:8080/actuator/health/liveness >/dev/null 2>&1; then
    echo LIVENESS_OK
    break
  fi
  if [ "$i" -eq 60 ]; then
    journalctl -u ispf-server -n 80 --no-pager
    exit 1
  fi
  sleep 2
done

echo "=== Wait for bootstrap (admin login + platform tree) ==="
for i in $(seq 1 120); do
  login_code="$(curl -s -o /dev/null -w '%{http_code}' http://127.0.0.1:8080/api/v1/auth/login \
    -H 'Content-Type: application/json' \
    -d '{"username":"admin","password":"admin"}')"
  node_count="$(docker exec ispf-postgres psql -U ispf -d ispf -t -A -c "SELECT COUNT(*) FROM object_nodes WHERE path='root.platform';" 2>/dev/null || echo 0)"
  if [ "$login_code" = "200" ] && [ "${node_count:-0}" -ge 1 ]; then
    echo BOOTSTRAP_OK
    break
  fi
  if [ "$i" -eq 120 ]; then
    echo "WARN: bootstrap wait timed out (login=$login_code root.platform nodes=$node_count)" >&2
    journalctl -u ispf-server -n 80 --no-pager
    exit 1
  fi
  sleep 2
done

echo "=== Post-reset counts ==="
docker exec ispf-postgres psql -U ispf -d ispf -c \
  "SELECT 'object_nodes' AS tbl, COUNT(*) FROM object_nodes
   UNION ALL SELECT 'object_variables', COUNT(*) FROM object_variables
   UNION ALL SELECT 'variable_samples', COUNT(*) FROM variable_samples
   UNION ALL SELECT 'alert_rules', COUNT(*) FROM alert_rules
   UNION ALL SELECT 'event_history_pg', COUNT(*) FROM event_history;"

if docker ps --format '{{.Names}}' | grep -qx ispf-clickhouse; then
  docker exec ispf-clickhouse clickhouse-client --query "SELECT count() AS event_history_ch FROM ispf.event_history" 2>/dev/null || true
fi
if docker ps --format '{{.Names}}' | grep -qx ispf-scylla; then
  docker exec ispf-scylla cqlsh -e "SELECT total_count FROM ispf.event_journal_meta WHERE id='total';" 2>/dev/null \
    || docker exec ispf-scylla cqlsh -e "DESCRIBE KEYSPACE ispf;" 2>/dev/null | head -5 || true
fi

curl -sf http://127.0.0.1:8080/api/v1/info
echo
