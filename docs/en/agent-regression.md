# Agent regression suite (BL-178)

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
| PR | `agent-regression` job in [ci.yml](../.github/workflows/ci.yml): `validate-scenarios.mjs` + `AgentRegressionCiTest` (schema + manifest; **fails on schema errors**) |
| Nightly | `run-nightly.sh` validates schemas; **default:** `AGENT_REGRESSION_RESULTS=tools/agent-regression/nightly-stub-results.json` (stub pass rate, not live LLM). Live ≥95% requires real agent run + `--enforce-rate` |
| Manual live | `ISPF_LLM_SMOKE=true` + `AgentLiveDeploySmokeTest` (BL-177 mes-platform one-shot) |

**Current scenario count:** 50 (SCADA, MES, HVAC).

Pass-rate reporter (`validate-scenarios.mjs --results nightly.json --enforce-rate`) — **target** ≥95% **live** agent pass rate. **Not met on 0.9.102** (CI + nightly default: schema/stub only). See [COMPETITIVE_SCORECARD.md](competitive-scorecard.md).

## Related

- [AI_DEVELOPMENT.md](ai-development.md) — agent tools and playbooks
- [AgentDeployPlaybook](../packages/ispf-server/src/main/java/com/ispf/server/ai/agent/AgentDeployPlaybook.java) — BL-177 e2e deploy recipe
- [AgentSolutionGeneratorPlaybook](../packages/ispf-server/src/main/java/com/ispf/server/ai/agent/AgentSolutionGeneratorPlaybook.java) — BL-180 factory spec recipe
- [ROADMAP_PHASE25.md](roadmap-phase-25.md) — BL-177, BL-178
