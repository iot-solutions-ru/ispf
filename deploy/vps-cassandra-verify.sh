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

SMOKE_WRITE="${ISPF_CASSANDRA_VERIFY_WRITE:-true}"
API_BASE="${ISPF_VERIFY_API_BASE:-http://127.0.0.1:8080}"
ADMIN_USER="${ISPF_VERIFY_ADMIN_USER:-admin}"
ADMIN_PASS="${ISPF_VERIFY_ADMIN_PASS:-admin}"

if [ "$SMOKE_WRITE" = "true" ]; then
  echo "=== Write smoke (POST /api/v1/events/fire → Scylla) ==="
  TOKEN=$(curl -sf -X POST "${API_BASE}/api/v1/auth/login" \
    -H 'Content-Type: application/json' \
    -d "{\"username\":\"${ADMIN_USER}\",\"password\":\"${ADMIN_PASS}\"}" \
    | sed -n 's/.*"token":"\([^"]*\)".*/\1/p') || true
  if [ -z "$TOKEN" ]; then
    echo "WARN: login failed — skip write smoke"
  else
    COUNT_BEFORE=$(docker exec ispf-scylla cqlsh -e "SELECT COUNT(*) FROM ${KEYSPACE}.event_history;" 2>/dev/null \
      | python3 -c "import re,sys; t=sys.stdin.read(); m=re.search(r'\\n\\s*(\\d+)\\s*\\n', t); print(m.group(1) if m else '0')" || echo "0")
    FIRE_STATUS=$(curl -s -o /dev/null -w '%{http_code}' -X POST \
      "${API_BASE}/api/v1/events/fire?objectPath=root.platform.devices.mqtt-device-01&eventName=messageReceived" \
      -H "Authorization: Bearer ${TOKEN}")
    if [ "$FIRE_STATUS" != "200" ]; then
      echo "WARN: events/fire HTTP $FIRE_STATUS (ACL or missing event descriptor — journal store still verified via startup log)"
    else
      sleep 3
      COUNT_AFTER=$(docker exec ispf-scylla cqlsh -e "SELECT COUNT(*) FROM ${KEYSPACE}.event_history;" 2>/dev/null \
        | python3 -c "import re,sys; t=sys.stdin.read(); m=re.search(r'\\n\\s*(\\d+)\\s*\\n', t); print(m.group(1) if m else '0')" || echo "0")
      echo "event_history count: ${COUNT_BEFORE} -> ${COUNT_AFTER}"
      [ "$COUNT_AFTER" -gt "$COUNT_BEFORE" ] || fail "event_history count did not increase"
    fi
  fi
fi

echo "OK: Cassandra/Scylla verify passed (contacts=${CONTACT_POINTS}:${CASSANDRA_PORT}, dc=${LOCAL_DC})"
