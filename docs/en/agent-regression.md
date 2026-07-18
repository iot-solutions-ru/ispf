# Agent regression suite (BL-178)

> **Status:** Lab — Scenario CI gates. Hub: [doc-status.md](doc-status.md).

Foundation for CI agent scenario validation: curated prompts, human UI journeys, bundle references, and schema checks before live agent runs against a platform instance.

## Layout

| Path | Purpose |
|------|---------|
| `tools/agent-regression/scenario-schema.json` | JSON Schema for scenario files |
| `tools/agent-regression/scenarios/*.json` | Scenario definitions (SCADA, MES, HVAC) |
| `tools/agent-regression/human-ui-rewrites.json` | Source map for human/agent lane rewrites |
| `tools/agent-regression/apply-human-ui-rewrites.mjs` | Applies rewrite map to scenario files |
| `tools/agent-regression/run-nightly.sh` | Nightly stub: validate-scenarios + optional live pass-rate gate |
| `scripts/run-agent-regression.sh` | Validates scenario + bundle manifest shape |

## Scenario file

Each scenario describes a plant-engineer ask (no tool names in `prompt`), optional UI journey, and optional bundle:

```json
{
  "id": "mes-reference-deploy",
  "version": "1",
  "domain": "mes",
  "lane": "human",
  "title": "MES reference deploy (UI)",
  "prompt": "Install the mes-reference application from Solutions/Marketplace…",
  "uiJourney": "Admin → System → Solutions → Install → Operator → Work Queue",
  "humanSteps": [
    "Sign in as admin; open System → Solutions…",
    "Deploy the full bundle; open Operator…"
  ],
  "playbook": "mesReferenceLifecycle",
  "bundle": {
    "appId": "mes-reference",
    "manifestPath": "examples/mes-reference/bundle.json"
  },
  "acceptance": {
    "validateBundle": true,
    "requiredTools": ["validate_bundle", "import_package", "invoke_bff"],
    "operatorSurfaces": ["solutions", "marketplace", "operator", "work-queue"]
  }
}
```

| Field | Meaning |
|-------|---------|
| `lane` | `human` = click-through in web console; `agent` = AI Studio/tool playbook + UI verify; `both` = either |
| `uiJourney` | Ordered console path (Explorer / System→Solutions / Mimic / Dashboard / Operator / Alarms / Work Queue / AI Studio) |
| `humanSteps` | Click-level steps a person can follow without naming agent tools |
| `acceptance.operatorSurfaces` | UI surfaces that must show success |
| `acceptance.requiredTools` | Still used by live agent suite / playbooks |

Domains: `scada`, `mes`, `hvac`.

### Human golden set (`lane: human`)

Reproduce these on a console (local JAR or demostand [ispf.iot-solutions.ru](https://ispf.iot-solutions.ru/), `admin`/`admin`):

| Domain | Scenario ids |
|--------|----------------|
| SCADA | `scada-pump-station`, `scada-mimic-facility`, `scada-tank-farm`, `scada-alarm-shelf`, `scada-historian-trends`, `scada-solution-generator`, `scada-virtual-cluster`, `scada-water-treatment`, `scada-modbus-device` |
| MES | `mes-reference-deploy`, `mes-dispatch-workflow`, `mes-defect-demo`, `mes-oee-dispatch`, `mes-platform-production`, `mes-solution-generator-factory`, `mes-workflow-escalation` |
| HVAC | `hvac-building-app`, `hvac-zone-comfort`, `hvac-ahu-comfort`, `hvac-scheduler-setpoints`, `hvac-solution-generator-site`, `hvac-chiller-plant` |

Typical admin chrome (RU labels on demostand): **Исследователь** (Explorer), **Система → Решения** (Solutions/Marketplace), **ИИ-студия** (AI Studio). Operator: `/?mode=operator&app=<appId>`, starter apps **Work Queue** and **Alarm Console**.

**Caveats for hand UI journeys:**

- Marketplace listings on a public demostand may install a *catalog stub* without `operatorUi`. Golden deploy scenarios require the **full** `examples/<app>/bundle.json` (AI Studio → Bundle, or Application Deploy) when Operator reports “UI not found”.
- Applying dashboard template `scada-facility-overview` must **auto-create** `root.platform.mimics.facility-overview` when missing (see `DashboardService.applyTemplateLayout` + `MimicService.ensureMimicExists`). A 404 on that path is a platform bug, not a user mistake.

Remaining scenarios use `lane: agent` or `both` (still ≥50 total for CI).

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
|------|------|
| PR | `agent-regression` job in [ci.yml](../../.github/workflows/ci.yml): `validate-scenarios.mjs` + `AgentRegressionCiTest` (schema + manifest; **fails on schema errors**) |
| Nightly | `run-nightly.sh` schema validation only. Optional BL-177 live one-shot when secrets `ISPF_AI_API_KEY` + `ISPF_AI_BASE_URL` are set (`run-live-oneshot.sh`). **`nightly-stub-results.json` is deprecated** — not live ≥95% proof |
| Platform gate | `run-platform-gate.sh` — primitive fixture + generator + **`AgentBundleDeploySuiteTest`** (≥95% bundles) + **BL-179** `OperatorAgentContinuityIntegrationTest` |
| Live suite | `run-live-suite.sh` — hybrid: tool playbook first, LLM fallback; modes `platform`/`bundle`/`full` |
| Manual live | `ISPF_LLM_SMOKE=true` + `AgentLiveDeploySmokeTest` / `run-live-oneshot.sh` (`AGENT_LIVE_APP_ID` optional) |

**Current scenario count:** 52 (SCADA, MES, HVAC) including `kind: platform-primitive` velocity fixtures — about 22 `human`, 18 `agent`, 12 `both`.

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
- [operator-guide](operator-guide.md) — Operator HMI / Work Queue
- [web-console](web-console.md) — admin Explorer / System / AI Studio
- [AgentDeployPlaybook](../../packages/ispf-server/src/main/java/com/ispf/server/ai/agent/AgentDeployPlaybook.java) — BL-177 e2e deploy recipe
- [AgentSolutionGeneratorPlaybook](../../packages/ispf-server/src/main/java/com/ispf/server/ai/agent/AgentSolutionGeneratorPlaybook.java) — BL-180 factory spec recipe
- [roadmap](roadmap.md) — BL-177, BL-178
