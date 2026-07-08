# AI Agent operations (BL-177…181)

Operator and integrator reference for the ISPF tree-first agent, regression suite, solution generator stub, and observability widgets.

See also [AI_DEVELOPMENT.md](AI_DEVELOPMENT.md), [AGENT_REGRESSION.md](AGENT_REGRESSION.md), [ADR-0034](decisions/0034-agent-observability-and-session-knowledge.md).

---

## Solution generator stub (BL-179)

Keyword-driven blueprint draft without LLM — first step toward «опиши завод» automation.

| Endpoint | Purpose |
|----------|---------|
| `POST /api/v1/ai/solutions/generate` | Prompt → `blueprintDraft` (domain `mes` / `scada` / `hvac`) |

Request:

```json
{
  "prompt": "SCADA tank farm with 2 pumps and high pressure alert"
}
```

Response (`mode: stub`):

```json
{
  "status": "OK",
  "mode": "stub",
  "domain": "scada",
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
    "referenceBundle": { "appId": "simulator-profiles", "manifestPath": "examples/simulator-profiles/bundle.json" },
    "nextSteps": ["create_object CUSTOM folder", "create_virtual_device", "..."]
  }
}
```

Domain detection keywords:

| Domain | Keywords (examples) |
|--------|-------------------|
| `mes` | mes, dispatch, oee, work order, manufacturing |
| `hvac` | hvac, building, comfort, zone, ahu, chiller |
| `scada` | scada, pump, tank, mimic, modbus, snmp, historian |

Full pipeline: [AgentSolutionGeneratorPlaybook](../packages/ispf-server/src/main/java/com/ispf/server/ai/agent/AgentSolutionGeneratorPlaybook.java).

---

## Agent regression suite (BL-178)

| Path | Purpose |
|------|---------|
| `tools/agent-regression/scenarios/*.json` | 40 curated scenarios (SCADA, MES, HVAC) |
| `tools/agent-regression/validate-scenarios.mjs` | Schema + bundle manifest validation + pass-rate report |
| `AgentRegressionCiTest` | Java CI gate (schema only, no LLM) |

Validate locally:

```bash
node tools/agent-regression/validate-scenarios.mjs
```

Pass-rate report (nightly live runs):

```bash
node tools/agent-regression/validate-scenarios.mjs --results nightly-results.json --enforce-rate
```

Results file shape:

```json
{
  "scenarios": [
    { "id": "mes-reference-deploy", "status": "OK" },
    { "id": "scada-tank-farm", "status": "ERROR" }
  ]
}
```

Target: **≥95%** live pass rate across all scenarios.

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
  "version": 2,
  "widgets": [
    {
      "id": "ai-tool-latency",
      "type": "chart",
      "title": "Agent tool latency (7d)",
      "grid": { "x": 0, "y": 0, "w": 6, "h": 4 },
      "options": {
        "chartType": "bar",
        "binding": {
          "source": "bff",
          "function": "ai_toolMetricsChart",
          "args": { "days": 7, "metric": "avgLatencyMs" }
        }
      }
    },
    {
      "id": "ai-tool-errors",
      "type": "chart",
      "title": "Agent tool error rate (7d)",
      "grid": { "x": 6, "y": 0, "w": 6, "h": 4 },
      "options": {
        "chartType": "bar",
        "binding": {
          "source": "bff",
          "function": "ai_toolMetricsChart",
          "args": { "days": 7, "metric": "errorRate" }
        }
      }
    },
    {
      "id": "ai-tool-tokens",
      "type": "indicator",
      "title": "Agent tokens (7d)",
      "grid": { "x": 0, "y": 4, "w": 4, "h": 2 },
      "options": {
        "binding": {
          "source": "bff",
          "function": "ai_toolMetricsTotals",
          "args": { "days": 7 }
        },
        "format": "integer"
      }
    }
  ]
}
```

**BFF sketch** (`ai_toolMetricsChart`): admin HTTP `GET /api/v1/ai/agent/metrics/tools`, map `tools[]` to `{ label: tool, value: metric }` rows for the chart widget.

**AI Studio panel:** Web Console already ships `AgentMetricsPanel` (`apps/web-console/src/components/agent/AgentMetricsPanel.tsx`) for turn-level metrics via `GET /api/v1/ai/agent/metrics`. Extend with a tool breakdown tab calling `/agent/metrics/tools` for the same data in the studio UI.

---

## Related backlog

| ID | Feature | Doc |
|----|---------|-----|
| BL-177 | End-to-end agent deploy | [AgentDeployPlaybook](../packages/ispf-server/src/main/java/com/ispf/server/ai/agent/AgentDeployPlaybook.java) |
| BL-178 | Agent regression suite | [AGENT_REGRESSION.md](AGENT_REGRESSION.md) |
| BL-179 | Solution generator stub | This doc § Solution generator |
| BL-180 | Solution generator GA + metrics widget | [AgentSolutionGeneratorPlaybook](../packages/ispf-server/src/main/java/com/ispf/server/ai/agent/AgentSolutionGeneratorPlaybook.java) |
| BL-181 | Agent observability v2 | `/agent/metrics/tools`, [ADR-0034](decisions/0034-agent-observability-and-session-knowledge.md) |
