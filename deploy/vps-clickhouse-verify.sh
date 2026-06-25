#!/bin/bash
set -euo pipefail
PASS=$(tr -d '\r\n' < /opt/ispf/clickhouse-password.txt)
echo "=== ClickHouse tables ==="
docker exec ispf-clickhouse clickhouse-client --password "$PASS" -q "SHOW TABLES FROM ispf"
echo "=== event_history count ==="
docker exec ispf-clickhouse clickhouse-client --password "$PASS" -q "SELECT count() FROM ispf.event_history"
echo "=== ISPF health ==="
curl -sf http://127.0.0.1:8080/actuator/health
echo
echo "=== event journal env ==="
grep '^ISPF_EVENT_JOURNAL_STORE=' /opt/ispf/ispf-server.env
