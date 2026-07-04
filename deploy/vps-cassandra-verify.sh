#!/bin/bash
# Verify Cassandra/Scylla time-series backends on VPS (CQL ping + optional ISPF smoke).
set -euo pipefail

INSTALL_ROOT="${ISPF_INSTALL_ROOT:-/opt/ispf}"
ENV_FILE="${ISPF_ENV_FILE:-$INSTALL_ROOT/ispf-server.env}"
CONTACT_POINTS="${ISPF_EVENT_JOURNAL_CASSANDRA_CONTACT_POINTS:-127.0.0.1}"
CASSANDRA_PORT="${ISPF_EVENT_JOURNAL_CASSANDRA_PORT:-9042}"
KEYSPACE="${ISPF_EVENT_JOURNAL_CASSANDRA_KEYSPACE:-ispf}"
LOCAL_DC="${ISPF_EVENT_JOURNAL_CASSANDRA_LOCAL_DATACENTER:-datacenter1}"
API_BASE="${ISPF_VERIFY_API_BASE:-http://127.0.0.1:8080}"
EXPECTED_VERSION="${1:-}"

fail() {
  echo "FAIL: $*" >&2
  exit 1
}

echo "=== Scylla/Cassandra container ==="
docker ps --filter name=ispf-scylla --format '{{.Names}} {{.Status}}' | grep -q ispf-scylla \
  || fail "ispf-scylla container not running"

echo "=== CQL describe keyspace ==="
docker exec ispf-scylla cqlsh -e "DESCRIBE KEYSPACE ${KEYSPACE}" >/dev/null \
  || fail "keyspace ${KEYSPACE} not ready"

echo "=== event journal env ==="
grep '^ISPF_EVENT_JOURNAL_STORE=' "$ENV_FILE" || fail "ISPF_EVENT_JOURNAL_STORE not set"
STORE=$(grep '^ISPF_EVENT_JOURNAL_STORE=' "$ENV_FILE" | cut -d= -f2-)
if [ "$STORE" != "cassandra" ] && [ "$STORE" != "scylla" ]; then
  fail "ISPF_EVENT_JOURNAL_STORE=$STORE (expected cassandra or scylla)"
fi

VAR_STORE=$(grep '^ISPF_VARIABLE_HISTORY_STORE=' "$ENV_FILE" 2>/dev/null | cut -d= -f2- || echo "jdbc")
if [ "$VAR_STORE" = "cassandra" ] || [ "$VAR_STORE" = "scylla" ]; then
  echo "=== variable history env (cassandra/scylla) ==="
  grep '^ISPF_VARIABLE_HISTORY_CASSANDRA_' "$ENV_FILE" | sed 's/PASSWORD=.*/PASSWORD=***/' || true
fi

echo "=== ISPF /api/v1/info ==="
INFO=$(curl -sf "${API_BASE}/api/v1/info") || fail "ISPF /api/v1/info unreachable"
echo "$INFO"
if [ -n "$EXPECTED_VERSION" ]; then
  echo "$INFO" | grep -q "\"version\":\"$EXPECTED_VERSION\"" \
    || fail "version mismatch (expected $EXPECTED_VERSION)"
fi

echo "=== Startup log (Cassandra journal/history) ==="
journalctl -u ispf-server -n 300 --no-pager \
  | grep -E 'Cassandra event journal ready|Cassandra variable history ready|TimescaleDB hypertable skipped' \
  | tail -8 || echo "(no matching log lines — check journalctl -u ispf-server)"

echo "OK: Cassandra/Scylla verify passed (contacts=${CONTACT_POINTS}:${CASSANDRA_PORT}, dc=${LOCAL_DC})"
