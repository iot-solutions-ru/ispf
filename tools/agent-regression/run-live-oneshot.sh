#!/usr/bin/env bash
# BL-177 / BL-178: run one live LLM mes-platform deploy smoke and emit results JSON.
# Requires: ISPF_LLM_SMOKE=true, ISPF_AI_BASE_URL, ISPF_AI_MODEL, ISPF_AI_API_KEY (or OPENAI_API_KEY).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"

SCENARIO_ID="${AGENT_LIVE_ONESHOT_SCENARIO_ID:-mes-platform-cert}"
OUT="${AGENT_LIVE_ONESHOT_RESULTS:-build/agent-regression/live-oneshot-results.json}"
mkdir -p "$(dirname "$OUT")"

if [[ "${ISPF_LLM_SMOKE:-}" != "true" ]]; then
  echo "SKIP: set ISPF_LLM_SMOKE=true to run live one-shot"
  exit 0
fi

if [[ -z "${ISPF_AI_API_KEY:-${OPENAI_API_KEY:-}}" ]]; then
  echo "FAIL: ISPF_AI_API_KEY or OPENAI_API_KEY required for live one-shot"
  exit 1
fi
if [[ -z "${ISPF_AI_BASE_URL:-}" ]]; then
  echo "FAIL: ISPF_AI_BASE_URL required for live one-shot"
  exit 1
fi

echo "=== BL-177 live one-shot (AgentLiveDeploySmokeTest) ==="
set +e
./gradlew :packages:ispf-server:test \
  --tests com.ispf.server.ai.agent.AgentLiveDeploySmokeTest \
  --no-daemon
STATUS=$?
set -e

if [[ "$STATUS" -eq 0 ]]; then
  RESULT_STATUS=OK
else
  RESULT_STATUS=ERROR
fi

cat >"$OUT" <<EOF
{
  "generatedAt": "$(date -u +%Y-%m-%dT%H:%M:%SZ)",
  "source": "AgentLiveDeploySmokeTest",
  "scenarios": [
    { "id": "${SCENARIO_ID}", "status": "${RESULT_STATUS}" }
  ]
}
EOF

echo "Wrote $OUT (status=$RESULT_STATUS)"
if [[ "$STATUS" -ne 0 ]]; then
  exit "$STATUS"
fi

if [[ "${AGENT_LIVE_ONESHOT_ENFORCE:-true}" == "true" ]]; then
  node tools/agent-regression/validate-scenarios.mjs --results "$OUT" --enforce-rate --oneshot
fi
