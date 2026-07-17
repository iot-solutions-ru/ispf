#!/usr/bin/env bash
# Platform velocity gate (BL-177 / BL-178 / BL-180):
# 1) Deterministic deploy playbook on platform-primitive fixture (no LLM)
# 2) Generator unit tests (draft=catalog; apply without LLM fails fast)
# 3) Optional live LLM oneshot when ISPF_LLM_SMOKE=true + AI secrets
# Writes build/agent-regression/platform-gate-results.json for --enforce-rate --oneshot
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"

OUT="${AGENT_PLATFORM_GATE_RESULTS:-build/agent-regression/platform-gate-results.json}"
mkdir -p "$(dirname "$OUT")"

STATUS_DEPLOY=ERROR
STATUS_GENERATOR=ERROR
STATUS_BUNDLE=ERROR
STATUS_CONTINUITY=ERROR
STATUS_LIVE=SKIP

echo "=== Platform gate: AgentPlatformPrimitiveDeployIntegrationTest ==="
set +e
./gradlew :packages:ispf-server:test \
  --tests com.ispf.server.ai.agent.AgentPlatformPrimitiveDeployIntegrationTest \
  --no-daemon
DEPLOY_RC=$?
set -e
if [[ "$DEPLOY_RC" -eq 0 ]]; then
  STATUS_DEPLOY=OK
fi

echo "=== Platform gate: AiSolutionGeneratorServiceTest ==="
set +e
./gradlew :packages:ispf-server:test \
  --tests com.ispf.server.ai.generation.AiSolutionGeneratorServiceTest \
  --no-daemon
GEN_RC=$?
set -e
if [[ "$GEN_RC" -eq 0 ]]; then
  STATUS_GENERATOR=OK
fi

echo "=== Platform gate: AgentBundleDeploySuiteTest (no LLM) ==="
set +e
./gradlew :packages:ispf-server:test \
  --tests com.ispf.server.ai.agent.AgentBundleDeploySuiteTest \
  --no-daemon
BUNDLE_RC=$?
set -e
if [[ "$BUNDLE_RC" -eq 0 ]]; then
  STATUS_BUNDLE=OK
fi

echo "=== Platform gate: OperatorAgentContinuityIntegrationTest (BL-179) ==="
set +e
./gradlew :packages:ispf-server:test \
  --tests com.ispf.server.ai.agent.OperatorAgentContinuityIntegrationTest \
  --no-daemon
CONTINUITY_RC=$?
set -e
if [[ "$CONTINUITY_RC" -eq 0 ]]; then
  STATUS_CONTINUITY=OK
fi

LIVE_JSON=""
if [[ "${ISPF_LLM_SMOKE:-}" == "true" ]] \
  && [[ -n "${ISPF_AI_API_KEY:-${OPENAI_API_KEY:-}}" ]] \
  && [[ -n "${ISPF_AI_BASE_URL:-}" ]]; then
  echo "=== Platform gate: optional live LLM oneshot ==="
  set +e
  AGENT_LIVE_ONESHOT_SCENARIO_ID="${AGENT_LIVE_ONESHOT_SCENARIO_ID:-platform-primitive-deploy}" \
    AGENT_LIVE_ONESHOT_ENFORCE=false \
    bash tools/agent-regression/run-live-oneshot.sh
  LIVE_RC=$?
  set -e
  if [[ "$LIVE_RC" -eq 0 ]]; then
    STATUS_LIVE=OK
  else
    STATUS_LIVE=ERROR
  fi
  LIVE_JSON=",
    { \"id\": \"platform-live-oneshot\", \"status\": \"${STATUS_LIVE}\" }"
else
  echo "SKIP live LLM oneshot (set ISPF_LLM_SMOKE=true + AI secrets to enable)"
fi

cat >"$OUT" <<EOF
{
  "generatedAt": "$(date -u +%Y-%m-%dT%H:%M:%SZ)",
  "source": "run-platform-gate.sh",
  "scenarios": [
    { "id": "platform-primitive-deploy", "status": "${STATUS_DEPLOY}" },
    { "id": "platform-generator-primitives", "status": "${STATUS_GENERATOR}" },
    { "id": "platform-bundle-suite", "status": "${STATUS_BUNDLE}" },
    { "id": "platform-operator-continuity", "status": "${STATUS_CONTINUITY}" }${LIVE_JSON}
  ]
}
EOF

echo "Wrote $OUT"
node tools/agent-regression/validate-scenarios.mjs --results "$OUT" --enforce-rate --oneshot

if [[ "$STATUS_DEPLOY" != "OK" ]] || [[ "$STATUS_GENERATOR" != "OK" ]] \
  || [[ "$STATUS_BUNDLE" != "OK" ]] || [[ "$STATUS_CONTINUITY" != "OK" ]]; then
  echo "FAIL: deterministic platform gate tests did not pass"
  exit 1
fi
if [[ "$STATUS_LIVE" == "ERROR" ]]; then
  echo "FAIL: live LLM oneshot failed"
  exit 1
fi

echo "OK: platform velocity gate passed"
