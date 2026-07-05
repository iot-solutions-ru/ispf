#!/usr/bin/env bash
# Smoke test for lab-cluster-compose.yml (round-robin, cluster health, nginx failover, driver reclaim).
set -euo pipefail

ROOT="${ISPF_LAB_ROOT:-/home/iot-solutions/ispf}"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PORT="${ISPF_LAB_CLUSTER_PORT:-8000}"
export ISPF_CLUSTER_COMPOSE_FILE="${ROOT}/lab-cluster-compose.yml"
export ISPF_CLUSTER_PORT="${PORT}"
export ISPF_CLUSTER_PG_USER=ispf
export ISPF_CLUSTER_PG_PASS=ispf-cluster-lab

exec bash "${SCRIPT_DIR}/cluster-smoke-test.sh"
