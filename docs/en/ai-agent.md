# AI Agent operations (BL-177…181)

> **Status:** Beta — Agent API; ≥95% gate not met. Hub: [doc-status.md](doc-status.md).

Operator and integrator reference for the ISPF tree-first agent, regression suite, solution generator, and observability widgets.

See also [ai-development](ai-development.md), [agent-regression](agent-regression.md), [0034-agent-observability-and-session-knowledge](decisions/0034-agent-observability-and-session-knowledge.md).

---

## Solution generator (BL-179 / BL-180)

Natural-language plant description → blueprint draft, optional **live** apply (tree + dashboards + alerts).

| Endpoint | Purpose |
|----------|---------|
| `POST /api/v1/ai/solutions/generate` | Prompt → draft; `apply:true` → live deploy (requires LLM) |

Draft request:

```json
{
  "prompt": "SCADA tank farm with 2 pumps and high pressure alert"
}
```

Draft response (`mode: draft` keyword fallback, or `mode: llm` when LLM classifies domain):

```json
{
  "status": "OK",
  "mode": "draft",
  "domain": "scada",
  "domainSelection": "keyword",
  "playbook": "AgentSolutionGeneratorPlaybook",
  "blueprintDraft": {
    "id": "scada-tank-farm-with-2-pumps",
    "title": "SCADA facility overview",
    "domain": "scada",
    "specBrief": { "title": "...", "entities": [], "functionalRequirements": [] },
    "suggestedArtifacts": {
      "rootFolder": "root.platform....",
      "devices": [],
      "dashboards": [],
      "alerts": [],
      "mimics": []
    },
    "referenceBundle": { "appId": "simulator", "manifestPath": "examples/simulator-profiles/bundle.json" },
    "nextSteps": ["POST … with apply=true", "…"]
  }
}
```

Live apply (BL-180):

```json
{
  "prompt": "Building HVAC with one AHU, overview dashboard and status alert",
  "apply": true
}
```

Requires configured LLM (`ISPF_AI_*`). Returns `mode: live` with `hubPath`, `dashboardPath`, `alertPath`, operator UI. Opt-in proof: `AiSolutionGeneratorLiveSmokeTest` (`ISPF_LLM_SMOKE=true`).

Domain detection keywords:

| Domain | Keywords (examples) |
|--------|-------------------|
| `mes` | mes, dispatch, oee, work order, manufacturing |
| `hvac` | hvac, building, comfort, zone, ahu, chiller |
| `scada` | scada, pump, tank, mimic, modbus, snmp, historian |

Full pipeline: [AgentSolutionGeneratorPlaybook](../../packages/ispf-server/src/main/java/com/ispf/server/ai/agent/AgentSolutionGeneratorPlaybook.java).

---

## Agent regression suite (BL-178)

| Path | Purpose |
|------|---------|
| `tools/agent-regression/scenarios/*.json` | 50 curated scenarios (SCADA, MES, HVAC) |
| `tools/agent-regression/validate-scenarios.mjs` | Schema + bundle manifest validation + pass-rate report (`--oneshot` for partial live results) |
| `AgentRegressionCiTest` | Java CI gate (schema only, no LLM) |
| `AgentLiveDeploySmokeTest` | Opt-in live LLM mes-platform deploy (`ISPF_LLM_SMOKE=true`) |
| `tools/agent-regression/run-live-oneshot.sh` | BL-177 one-shot wrapper → results JSON + `--enforce-rate --oneshot` |

### BL-177 live one-shot (manual / nightly with secrets)

```bash
# OpenAI-compatible LLM (NOT the ISPF Admin Console port)
export ISPF_LLM_SMOKE=true
export ISPF_AI_PROVIDER=openai-compatible
export ISPF_AI_BASE_URL=https://api.deepseek.com/v1   # example
export ISPF_AI_MODEL=deepseek-v4-flash
export ISPF_AI_API_KEY=…                              # never commit

bash tools/agent-regression/run-live-oneshot.sh
# or:
./gradlew :packages:ispf-server:test --tests com.ispf.server.ai.agent.AgentLiveDeploySmokeTest
```

PASS when operator UI `mes-platform` is live and BFF `mes_platform_listLines` returns `LINE-A01` without human edits.
Prefer the agent tool **`run_deploy_playbook`** with `{"appId":"mes-platform"}` (loads example bundle → validate → dry-run → import → operator UI).

Validate locally:

```bash
node tools/agent-regression/validate-scenarios.mjs
```

Pass-rate report (full suite vs one-shot):

```bash
node tools/agent-regression/validate-scenarios.mjs --results nightly-results.json --enforce-rate
node tools/agent-regression/validate-scenarios.mjs --results build/agent-regression/live-oneshot-results.json --enforce-rate --oneshot
```

Results file shape:

```json
{
  "scenarios": [
    { "id": "mes-platform-cert", "status": "OK" },
    { "id": "scada-tank-farm", "status": "ERROR" }
  ]
}
```

**Target (not met):** ≥95% live pass rate across all scenarios (full BL-178). As of the code-verified [competitive-scorecard](competitive-scorecard.md), AI-assisted development is **~7.0 PARTIAL** — one-shot / live smoke paths are **REAL**; the full 50-scenario ≥95% gate is **open**. **S31 one-shot** proves BL-177 with `--oneshot`. `nightly-stub-results.json` is **deprecated** — not evidence of live ≥95%.

---

## AI tool metrics dashboard widget (BL-180)

Admin observability for per-tool agent cost and reliability. Data source:

| Endpoint | Fields |
|----------|--------|
| `GET /api/v1/ai/agent/metrics/tools?days=7` | `tools[]`: `tool`, `callCount`, `avgLatencyMs`, `promptTokens`, `completionTokens`, `errorCount`, `errorRate` |

### Reference dashboard layout

Embed in a platform dashboard (`root.platform.dashboards.ai-ops`) using a **chart** widget bound to the tool metrics API via a BFF or script function. Minimal reference layout:

```json
{
  "columns": 84,
  "rowHeight": 8,
  "widgets": [
    {
      "id": "ai-tool-latency",
      "type": "chart",
      "title": "Agent tool latency (7d)",
      "x": 0, "y": 0, "w": 42, "h": 28,
      "objectPath": "root.platform.devices.demo-sensor-01",
      "variableName": "temperature"
    },
    {
      "id": "ai-tool-errors",
      "type": "chart",
      "title": "Agent tool error rate (7d)",
      "x": 42, "y": 0, "w": 42, "h": 28,
      "objectPath": "root.platform.devices.demo-sensor-01",
      "variableName": "temperature"
    },
    {
      "id": "ai-tool-tokens",
      "type": "value",
      "title": "Sample KPI tile",
      "x": 0, "y": 28, "w": 21, "h": 14,
      "objectPath": "root.platform.devices.demo-sensor-01",
      "variableName": "temperature",
      "decimals": 0
    }
  ]
}
```

> Sketch only: bind real charts to `GET /api/v1/ai/agent/metrics/tools` via a BFF/script function when available. Layout uses the canonical **84×8** grid ([dashboards](dashboards.md)).

**BFF sketch** (`ai_toolMetricsChart`): admin HTTP `GET /api/v1/ai/agent/metrics/tools`, map `tools[]` to `{ label: tool, value: metric }` rows for the chart widget.

**AI Studio panel:** Web Console already ships `AgentMetricsPanel` (`apps/web-console/src/components/agent/AgentMetricsPanel.tsx`) for turn-level metrics via `GET /api/v1/ai/agent/metrics`. Extend with a tool breakdown tab calling `/agent/metrics/tools` for the same data in the studio UI.

---

## Related backlog

| ID | Feature | Doc |
|----|---------|-----|
| BL-177 | End-to-end agent deploy | [AgentDeployPlaybook](../../packages/ispf-server/src/main/java/com/ispf/server/ai/agent/AgentDeployPlaybook.java) |
| BL-178 | Agent regression suite | [agent-regression](agent-regression.md) |
| BL-179 | Solution generator draft API | This doc § Solution generator |
| BL-180 | Solution generator live apply + metrics widget | `AiSolutionGeneratorLiveSmokeTest`, playbook |
| BL-181 | Agent observability v2 | `/agent/metrics/tools`, [0034-agent-observability-and-session-knowledge](decisions/0034-agent-observability-and-session-knowledge.md) |
