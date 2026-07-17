# Agent regression suite (BL-178)

> **Status:** Lab — Scenario CI gates. Hub: [doc-status.md](doc-status.md).

Foundation for CI agent scenario validation: curated prompts, bundle references, and schema checks before live agent runs against a platform instance.

## Layout

| Path | Purpose |
|------|---------|
| `tools/agent-regression/scenario-schema.json` | JSON Schema for scenario files |
| `tools/agent-regression/scenarios/*.json` | Scenario definitions (SCADA, MES, HVAC) |
| `tools/agent-regression/run-nightly.sh` | Nightly stub: validate-scenarios + optional live pass-rate gate |
| `scripts/run-agent-regression.sh` | Validates scenario + bundle manifest shape |

## Scenario file

Each scenario describes an agent task and optional bundle under test:

```json
{
  "id": "mes-reference-deploy",
  "version": "1",
  "domain": "mes",
  "title": "MES reference bundle deploy",
  "prompt": "Deploy mes-reference and verify BFF functions.",
  "playbook": "mes-reference-lifecycle",
  "bundle": {
    "appId": "mes-reference",
    "manifestPath": "examples/mes-reference/bundle.json"
  },
  "acceptance": {
    "validateBundle": true,
    "requiredTools": ["validate_bundle", "import_package"]
  }
}
```

Domains: `scada`, `mes`, `hvac`.

## Run validation (local)

From repo root:

```bash
bash scripts/run-agent-regression.sh
```

Optional: point at a running ISPF to validate bundles via API (same contract as `tools/bundle-validate-cli/validate.mjs`):

```bash
export ISPF_BASE_URL=http://localhost:8080
export ISPF_API_TOKEN=<admin-jwt>
bash scripts/run-agent-regression.sh --live
```

## CI integration

| Stage | Gate |
|-------|------|
| PR | `agent-regression` job in [ci.yml](../../.github/workflows/ci.yml): `validate-scenarios.mjs` + `AgentRegressionCiTest` (schema + manifest; **fails on schema errors**) |
| Nightly | `run-nightly.sh` schema validation only. Optional BL-177 live one-shot when secrets `ISPF_AI_API_KEY` + `ISPF_AI_BASE_URL` are set (`run-live-oneshot.sh`). **`nightly-stub-results.json` is deprecated** — not live ≥95% proof |
| Platform gate | `run-platform-gate.sh` — primitive fixture + generator + **`AgentBundleDeploySuiteTest`** (≥95% bundles) + **BL-179** `OperatorAgentContinuityIntegrationTest` |
| Live suite | `run-live-suite.sh` — hybrid: tool playbook first, LLM fallback; modes `platform`/`bundle`/`full` |
| Manual live | `ISPF_LLM_SMOKE=true` + `AgentLiveDeploySmokeTest` / `run-live-oneshot.sh` (`AGENT_LIVE_APP_ID` optional) |

**Current scenario count:** 52 (SCADA, MES, HVAC) including `kind: platform-primitive` velocity fixtures.

Pass-rate reporter:

- Full suite: `AGENT_LIVE_SUITE_MODE=full bash tools/agent-regression/run-live-suite.sh` → `live-suite-results.json` + `--enforce-rate` (BL-178; needs LLM budget)
- Platform / bundle subset: `AGENT_LIVE_SUITE_MODE=platform|bundle` → `--enforce-rate --oneshot` on results present
- One-shot: `validate-scenarios.mjs --results build/agent-regression/live-oneshot-results.json --enforce-rate --oneshot` — S31 BL-177 proof
- Platform gate (no LLM required): `bash tools/agent-regression/run-platform-gate.sh`

Nightly CI runs **platform** live suite when AI secrets are set. Full 52-scenario live gate is manual / on-demand:

```bash
export ISPF_LLM_SMOKE=true ISPF_AI_BASE_URL=... ISPF_AI_API_KEY=...
export AGENT_LIVE_SUITE_MODE=full AGENT_LIVE_SUITE_ENFORCE=true
bash tools/agent-regression/run-live-suite.sh
```

Platform-primitive scenarios exercise **engines** (deploy playbook / generator), not vertical products.

See [competitive-scorecard](competitive-scorecard.md).

## Related

- [ai-development](ai-development.md) — agent tools and playbooks
- [AgentDeployPlaybook](../../packages/ispf-server/src/main/java/com/ispf/server/ai/agent/AgentDeployPlaybook.java) — BL-177 e2e deploy recipe
- [AgentSolutionGeneratorPlaybook](../../packages/ispf-server/src/main/java/com/ispf/server/ai/agent/AgentSolutionGeneratorPlaybook.java) — BL-180 factory spec recipe
- [roadmap](roadmap.md) — BL-177, BL-178
