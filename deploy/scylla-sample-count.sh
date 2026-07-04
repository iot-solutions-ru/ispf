#!/usr/bin/env bash
set -euo pipefail
PATH="$1"
FIELD="${2:-raw}"
VAR="${3:-temperature}"
docker exec ispf-scylla cqlsh -e \
  "SELECT COUNT(*) FROM ispf.variable_samples WHERE object_path='${PATH}' AND variable_name='${VAR}' AND field_name='${FIELD}';" \
  2>/dev/null | grep -E '^[[:space:]]*[0-9]+[[:space:]]*$' | tr -d ' ' | head -1
