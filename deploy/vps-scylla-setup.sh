#!/bin/bash
# Deploy ScyllaDB (CQL-compatible) on VPS for ISPF time-series backends.
set -euo pipefail

INSTALL_ROOT="${ISPF_INSTALL_ROOT:-/opt/ispf}"
SCYLLA_IMAGE="${ISPF_SCYLLA_IMAGE:-scylladb/scylla:6.2}"
CONTACT_POINTS="${ISPF_VARIABLE_HISTORY_CASSANDRA_CONTACT_POINTS:-127.0.0.1}"
CASSANDRA_PORT="${ISPF_VARIABLE_HISTORY_CASSANDRA_PORT:-9042}"
KEYSPACE="${ISPF_VARIABLE_HISTORY_CASSANDRA_KEYSPACE:-ispf}"
LOCAL_DC="${ISPF_VARIABLE_HISTORY_CASSANDRA_LOCAL_DATACENTER:-datacenter1}"

if ! command -v docker >/dev/null 2>&1; then
  echo "Docker is required" >&2
  exit 1
fi

echo "=== Starting Scylla (ispf-scylla) ==="
docker rm -f ispf-scylla 2>/dev/null || true
docker run -d --name ispf-scylla --restart unless-stopped \
  -p "127.0.0.1:${CASSANDRA_PORT}:9042" \
  -v ispf_scylla-data:/var/lib/scylla \
  "$SCYLLA_IMAGE" \
  --smp 1 --memory 750M --overprovisioned 1 --api-address 0.0.0.0

echo "=== Waiting for CQL (${CONTACT_POINTS}:${CASSANDRA_PORT}) ==="
for i in $(seq 1 90); do
  if docker exec ispf-scylla cqlsh -e "SELECT now() FROM system.local" >/dev/null 2>&1; then
    echo "Scylla CQL is ready"
    break
  fi
  if [ "$i" -eq 90 ]; then
    echo "Scylla did not become ready in time" >&2
    docker logs --tail 80 ispf-scylla >&2 || true
    exit 1
  fi
  sleep 2
done

echo "=== Pre-create keyspace ${KEYSPACE} ==="
docker exec ispf-scylla cqlsh -e \
  "CREATE KEYSPACE IF NOT EXISTS ${KEYSPACE} WITH replication = {'class': 'SimpleStrategy', 'replication_factor': 1};"

docker exec ispf-scylla cqlsh -e "DESCRIBE KEYSPACE ${KEYSPACE}" >/dev/null

echo "=== Scylla container ==="
docker ps --filter name=ispf-scylla --format '{{.Names}} {{.Status}}'
echo "=== Done: Scylla at ${CONTACT_POINTS}:${CASSANDRA_PORT}, dc=${LOCAL_DC}, keyspace=${KEYSPACE} ==="
