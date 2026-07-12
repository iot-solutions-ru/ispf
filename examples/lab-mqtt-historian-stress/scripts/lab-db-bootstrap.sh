#!/usr/bin/env bash
set -euo pipefail

ROOT="${ISPF_LAB_LOADGEN_ROOT:-$HOME/ispf}"
ENV_FILE="${ISPF_LAB_DB_ENV:-${ROOT}/lab-db.env}"
COMPOSE_FILE="${ISPF_LAB_DB_COMPOSE:-${ROOT}/lab-db-compose.yml}"

echo "==> lab-db-bootstrap $(date -Iseconds)"
echo "ROOT=${ROOT}"

if [[ ! -f "${COMPOSE_FILE}" ]]; then
  echo "ERROR: missing ${COMPOSE_FILE}" >&2
  exit 1
fi

COMPOSE=(docker compose --env-file "${ENV_FILE}" -f "${COMPOSE_FILE}")

echo "==> Pull DB images"
"${COMPOSE[@]}" pull

echo "==> Start postgres + scylla + clickhouse (bind ${ISPF_LAB_DB_BIND:-198.51.100.10})"
"${COMPOSE[@]}" up -d postgres scylla clickhouse

echo "==> Wait PostgreSQL"
for i in $(seq 1 60); do
  if "${COMPOSE[@]}" exec -T postgres pg_isready -U ispf -d ispf >/dev/null 2>&1; then
    echo "  postgres ready (${i})"
    break
  fi
  if [[ "${i}" -eq 60 ]]; then
    echo "ERROR: postgres timeout" >&2
    "${COMPOSE[@]}" logs --tail 30 postgres
    exit 1
  fi
  sleep 2
done

echo "==> Wait Scylla CQL"
SCYLLA_CID="$("${COMPOSE[@]}" ps -q scylla)"
for i in $(seq 1 90); do
  if docker exec "${SCYLLA_CID}" cqlsh -e "SELECT now() FROM system.local" >/dev/null 2>&1; then
    echo "  scylla CQL ready (${i})"
    break
  fi
  if [[ "${i}" -eq 90 ]]; then
    echo "ERROR: scylla timeout" >&2
    "${COMPOSE[@]}" logs --tail 40 scylla
    exit 1
  fi
  sleep 3
done

echo "==> Ensure keyspace ispf"
docker exec "${SCYLLA_CID}" cqlsh -e \
  "CREATE KEYSPACE IF NOT EXISTS ispf WITH replication = {'class': 'SimpleStrategy', 'replication_factor': 1};"

echo "==> Wait ClickHouse HTTP"
CH_PASS="${CLICKHOUSE_PASSWORD:-CHANGE_ME_CLICKHOUSE}"
CH_CID="$("${COMPOSE[@]}" ps -q clickhouse)"
for i in $(seq 1 60); do
  if docker exec "${CH_CID}" wget -qO- --user=ispf --password="${CH_PASS}" http://127.0.0.1:8123/ping 2>/dev/null | grep -q Ok; then
    echo "  clickhouse ready (${i})"
    break
  fi
  if [[ "${i}" -eq 60 ]]; then
    echo "ERROR: clickhouse timeout" >&2
    "${COMPOSE[@]}" logs --tail 30 clickhouse
    exit 1
  fi
  sleep 2
done

echo "==> DB stack"
"${COMPOSE[@]}" ps
