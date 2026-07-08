#!/usr/bin/env bash
# BL-178: nightly agent regression — schema validation + optional live pass-rate gate (≥95%).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"

echo "=== agent-regression nightly (BL-178) ==="
node tools/agent-regression/validate-scenarios.mjs "$@"

if [[ -n "${AGENT_REGRESSION_RESULTS:-}" ]]; then
  echo "=== live pass-rate gate ==="
  node tools/agent-regression/validate-scenarios.mjs \
    --results "${AGENT_REGRESSION_RESULTS}" \
    --enforce-rate
fi

echo "OK: nightly agent-regression complete"
