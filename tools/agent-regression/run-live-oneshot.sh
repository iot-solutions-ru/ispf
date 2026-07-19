#!/usr/bin/env bash
# BL-177 / BL-178: run live LLM deploy smoke matrix and emit results JSON.
# Default apps: mes-platform, building-hvac, platform-primitive.
# Override: AGENT_LIVE_APP_ID (single) or AGENT_LIVE_APP_IDS (comma-separated).
# Requires: ISPF_LLM_SMOKE=true, ISPF_AI_BASE_URL, ISPF_AI_MODEL, ISPF_AI_API_KEY (or OPENAI_API_KEY).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"

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

if [[ -n "${AGENT_LIVE_APP_ID:-}" ]]; then
  APPS=("${AGENT_LIVE_APP_ID}")
elif [[ -n "${AGENT_LIVE_APP_IDS:-}" ]]; then
  IFS=',' read -r -a APPS <<< "${AGENT_LIVE_APP_IDS}"
else
  APPS=(mes-platform building-hvac platform-primitive)
fi

echo "=== BL-177 live multi-app smoke (AgentLiveDeploySmokeTest) apps=${APPS[*]} ==="

SCENARIO_ENTRIES=()
OVERALL_STATUS=0
for raw in "${APPS[@]}"; do
  app="$(echo "$raw" | xargs)"
  [[ -z "$app" ]] && continue
  # Single-app override keeps scenario id stable for legacy callers; matrix uses per-app ids.
  if [[ -n "${AGENT_LIVE_APP_ID:-}" && ${#APPS[@]} -eq 1 && -n "${AGENT_LIVE_ONESHOT_SCENARIO_ID:-}" ]]; then
    scenario_id="${AGENT_LIVE_ONESHOT_SCENARIO_ID}"
  else
    scenario_id="${app}-deploy"
  fi
  # Pin AGENT_LIVE_APP_ID so parameterized matrix does not re-run peers in this loop.
  echo "--- appId=${app} ---"
  set +e
  AGENT_LIVE_APP_ID="$app" ./gradlew :packages:ispf-ai-agent:test \
    --tests com.ispf.server.ai.agent.AgentLiveDeploySmokeTest \
    --no-daemon
  STATUS=$?
  set -e
  if [[ "$STATUS" -eq 0 ]]; then
    RESULT_STATUS=OK
  else
    RESULT_STATUS=ERROR
    OVERALL_STATUS=1
  fi
  SCENARIO_ENTRIES+=("    { \"id\": \"${scenario_id}\", \"appId\": \"${app}\", \"status\": \"${RESULT_STATUS}\" }")
done

SCENARIOS_JOINED=""
for i in "${!SCENARIO_ENTRIES[@]}"; do
  if [[ "$i" -gt 0 ]]; then
    SCENARIOS_JOINED+=",
"
  fi
  SCENARIOS_JOINED+="${SCENARIO_ENTRIES[$i]}"
done

cat >"$OUT" <<EOF
{
  "generatedAt": "$(date -u +%Y-%m-%dT%H:%M:%SZ)",
  "source": "AgentLiveDeploySmokeTest",
  "matrix": true,
  "scenarios": [
${SCENARIOS_JOINED}
  ]
}
EOF

echo "Wrote $OUT (overall=$([[ $OVERALL_STATUS -eq 0 ]] && echo OK || echo ERROR))"
if [[ "$OVERALL_STATUS" -ne 0 ]]; then
  exit "$OVERALL_STATUS"
fi

if [[ "${AGENT_LIVE_ONESHOT_ENFORCE:-true}" == "true" ]]; then
  node tools/agent-regression/validate-scenarios.mjs --results "$OUT" --enforce-rate --oneshot
fi
