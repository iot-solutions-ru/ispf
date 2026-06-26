#!/bin/bash
# Verify ClickHouse event journal backend on VPS (read + optional write smoke).
set -euo pipefail

INSTALL_ROOT="${ISPF_INSTALL_ROOT:-/opt/ispf}"
ENV_FILE="${ISPF_ENV_FILE:-$INSTALL_ROOT/ispf-server.env}"
CH_PASSWORD_FILE="${ISPF_CLICKHOUSE_PASSWORD_FILE:-$INSTALL_ROOT/clickhouse-password.txt}"
CLICKHOUSE_URL="${ISPF_EVENT_JOURNAL_CLICKHOUSE_URL:-http://127.0.0.1:8123}"
EXPECTED_VERSION="${1:-}"
SMOKE_WRITE="${ISPF_CLICKHOUSE_VERIFY_WRITE:-true}"
API_BASE="${ISPF_VERIFY_API_BASE:-http://127.0.0.1:8080}"
ADMIN_USER="${ISPF_VERIFY_ADMIN_USER:-admin}"
ADMIN_PASS="${ISPF_VERIFY_ADMIN_PASS:-admin}"

fail() {
  echo "FAIL: $*" >&2
  exit 1
}

if [ ! -f "$CH_PASSWORD_FILE" ]; then
  fail "Missing $CH_PASSWORD_FILE — run vps-clickhouse-setup.sh first"
fi

PASS=$(tr -d '\r\n' < "$CH_PASSWORD_FILE")

echo "=== ClickHouse ping ==="
curl -sf "${CLICKHOUSE_URL}/ping" >/dev/null || fail "ClickHouse not reachable at $CLICKHOUSE_URL"

echo "=== ISPF /api/v1/info ==="
INFO=$(curl -sf "${API_BASE}/api/v1/info") || fail "ISPF /api/v1/info unreachable"
echo "$INFO"
if [ -n "$EXPECTED_VERSION" ]; then
  echo "$INFO" | grep -q "\"version\":\"$EXPECTED_VERSION\"" \
    || fail "version mismatch (expected $EXPECTED_VERSION)"
fi

echo "=== ISPF health ==="
curl -sf "${API_BASE}/actuator/health" || fail "actuator/health failed"
echo

echo "=== event journal env ==="
grep '^ISPF_EVENT_JOURNAL_STORE=' "$ENV_FILE" || fail "ISPF_EVENT_JOURNAL_STORE not set"
STORE=$(grep '^ISPF_EVENT_JOURNAL_STORE=' "$ENV_FILE" | cut -d= -f2-)
if [ "$STORE" != "clickhouse" ]; then
  fail "ISPF_EVENT_JOURNAL_STORE=$STORE (expected clickhouse)"
fi
grep '^ISPF_EVENT_JOURNAL_CLICKHOUSE_' "$ENV_FILE" | sed 's/PASSWORD=.*/PASSWORD=***/' || true

echo "=== ClickHouse container ==="
docker ps --filter name=ispf-clickhouse --format '{{.Names}} {{.Status}}' | grep -q ispf-clickhouse \
  || fail "ispf-clickhouse container not running"

echo "=== ClickHouse tables ==="
docker exec ispf-clickhouse clickhouse-client --password "$PASS" -q "SHOW TABLES FROM ispf"

echo "=== event_history count (before) ==="
COUNT_BEFORE=$(docker exec ispf-clickhouse clickhouse-client --password "$PASS" -q "SELECT count() FROM ispf.event_history")
echo "$COUNT_BEFORE"

echo "=== Startup log (ClickHouse journal) ==="
journalctl -u ispf-server -n 300 --no-pager \
  | grep -E 'ClickHouse event journal ready|TimescaleDB hypertable skipped for event_history' \
  | tail -3 || echo "(no matching log lines — check journalctl -u ispf-server)"

if [ "$SMOKE_WRITE" = "true" ]; then
  echo "=== Write smoke (POST /api/v1/events/fire → ClickHouse) ==="
  TOKEN=$(curl -sf -X POST "${API_BASE}/api/v1/auth/login" \
    -H 'Content-Type: application/json' \
    -d "{\"username\":\"${ADMIN_USER}\",\"password\":\"${ADMIN_PASS}\"}" \
    | sed -n 's/.*"token":"\([^"]*\)".*/\1/p') || true
  if [ -z "$TOKEN" ]; then
    echo "WARN: login failed — skip write smoke (set ISPF_VERIFY_ADMIN_* or ISPF_CLICKHOUSE_VERIFY_WRITE=false)"
  else
    FIRE_STATUS=$(curl -s -o /dev/null -w '%{http_code}' -X POST \
      "${API_BASE}/api/v1/events/fire?objectPath=root.platform.devices.demo-sensor-01&eventName=thresholdExceeded" \
      -H "Authorization: Bearer ${TOKEN}")
    [ "$FIRE_STATUS" = "200" ] || fail "events/fire HTTP $FIRE_STATUS"
    sleep 3
    COUNT_AFTER=$(docker exec ispf-clickhouse clickhouse-client --password "$PASS" -q "SELECT count() FROM ispf.event_history")
    echo "event_history count (after): $COUNT_AFTER"
    if [ "$COUNT_AFTER" -le "$COUNT_BEFORE" ]; then
      fail "event_history count did not increase ($COUNT_BEFORE -> $COUNT_AFTER)"
    fi
    echo "OK: +$((COUNT_AFTER - COUNT_BEFORE)) row(s)"
  fi
fi

echo "=== OK: ClickHouse event journal verified ==="
