# Agent regression suite (BL-178)

Foundation for CI agent scenario validation: curated prompts, bundle references, and schema checks before live agent runs against a platform instance.

## Layout

| Path | Purpose |
|------|---------|
| `tools/agent-regression/scenario-schema.json` | JSON Schema for scenario files |
| `tools/agent-regression/scenarios/*.json` | Scenario definitions (SCADA, MES, HVAC) |
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

## CI integration (planned)

| Stage | Gate |
|-------|------|
| PR | `run-agent-regression.sh` (schema + manifest) |
| Nightly | Live agent run against lab VPS; pass rate ≥95% (BL-178 target: 50 scenarios) |

**Current scenario count:** 21 (SCADA, MES, HVAC).

## Related

- [AI_DEVELOPMENT.md](AI_DEVELOPMENT.md) — agent tools and playbooks
- [AgentDeployPlaybook](../packages/ispf-server/src/main/java/com/ispf/server/ai/agent/AgentDeployPlaybook.java) — BL-177 e2e deploy recipe
- [AgentSolutionGeneratorPlaybook](../packages/ispf-server/src/main/java/com/ispf/server/ai/agent/AgentSolutionGeneratorPlaybook.java) — BL-180 factory spec recipe
- [ROADMAP_PHASE25.md](ROADMAP_PHASE25.md) — BL-177, BL-178
