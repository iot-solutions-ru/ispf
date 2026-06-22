#!/bin/bash
# Wipe ISPF object tree + automation runtime data from PostgreSQL, then restart server
# (platform bootstrap recreates root.platform.* skeleton and demo fixtures).
set -euo pipefail

COMPOSE_FILE="${ISPF_COMPOSE_FILE:-/opt/ispf/docker-compose.postgres.yml}"
ENV_FILE="${ISPF_ENV_FILE:-/opt/ispf/ispf-server.env}"

echo "=== Stop ISPF server ==="
systemctl stop ispf-server

echo "=== Truncate object / automation tables ==="
docker-compose -f "$COMPOSE_FILE" --env-file "$ENV_FILE" exec -T postgres psql -U ispf -d ispf -v ON_ERROR_STOP=1 <<'SQL'
TRUNCATE TABLE
  correlator_hits,
  event_correlators,
  alert_rules,
  workflow_user_tasks,
  workflow_instances,
  workflow_cancel_journal,
  event_history,
  object_acl_entries,
  object_config_audit,
  object_edit_leases,
  object_variables,
  object_nodes,
  function_invoke_audit,
  report_templates,
  model_definitions,
  platform_schedules,
  platform_change_sets,
  variable_samples
RESTART IDENTITY CASCADE;
SQL

echo "=== Start ISPF server (fresh bootstrap) ==="
systemctl start ispf-server

for i in $(seq 1 120); do
  if curl -sf http://127.0.0.1:8080/actuator/health >/dev/null 2>&1; then
    echo HEALTH_OK
    break
  fi
  if [ "$i" -eq 120 ]; then
    journalctl -u ispf-server -n 60 --no-pager
    exit 1
  fi
  sleep 2
done

echo "=== Counts after wipe ==="
docker-compose -f "$COMPOSE_FILE" --env-file "$ENV_FILE" exec -T postgres psql -U ispf -d ispf -c \
  "SELECT 'object_nodes' AS tbl, COUNT(*) FROM object_nodes
   UNION ALL SELECT 'object_variables', COUNT(*) FROM object_variables
   UNION ALL SELECT 'alert_rules', COUNT(*) FROM alert_rules
   UNION ALL SELECT 'event_correlators', COUNT(*) FROM event_correlators
   UNION ALL SELECT 'event_history', COUNT(*) FROM event_history;"

curl -sf http://127.0.0.1:8080/api/v1/info
echo
