#!/usr/bin/env bash
set -euo pipefail
docker exec ispf-postgres psql -U ispf -d ispf -c "DELETE FROM platform_cluster_replicas WHERE replica_id IN ('replica-2', 'replica-3', 'replica-4', 'worker-1');"
