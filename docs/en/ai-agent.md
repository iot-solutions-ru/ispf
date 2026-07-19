# AI Agent operations (BL-177…181)

> **Status:** Beta — Agent API; BL-178 live ≥95% **met** (52/52 @100%). Hub: [doc-status.md](doc-status.md).

Operator and integrator reference for the ISPF tree-first agent, regression suite, solution generator, and observability widgets.

See also [ai-development](ai-development.md), [agent-regression](agent-regression.md), [0034-agent-observability-and-session-knowledge](decisions/0034-agent-observability-and-session-knowledge.md).

---

## Solution generator (BL-179 / BL-180)

![AI Studio agent chat](../assets/ispf-ai-studio.png)

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

**BL-178 (met):** full live suite ≥95% — **52/52 @100%** via `AGENT_LIVE_SUITE_MODE=full` / `run-live-suite.sh` (`build/agent-regression/live-suite-results.json`, ~2026-07-18/19). Nightly CI still runs **platform** mode. **BL-177 / BL-180 Done** — multi-app / multi-domain live smoke harness in repo; live runs require `ISPF_LLM_SMOKE=true`. [competitive-scorecard](competitive-scorecard.md) AI dimension **~9.0 REAL**. `nightly-stub-results.json` is **deprecated** — not evidence of live ≥95%.

---

## Agent observability v2 (BL-181)

Admin observability for turn aggregates and per-tool cost/latency/reliability.

| Endpoint | Auth | Fields |
|----------|------|--------|
| `GET /api/v1/ai/agent/metrics?days=7` | admin | `turnsByStatus`, `avgStepsPerTurn`, `topFailingTools`, token/latency sums, `toolLatencyBreakdown` |
| `GET /api/v1/ai/agent/metrics/tools?days=7` | admin | `tools[]`: `tool`, `callCount`, `avgLatencyMs`, `maxLatencyMs`, `promptTokens`, `completionTokens`, `errorCount`, `errorRate` |

**AI Studio (REAL):** Status tab → `AgentMetricsPanel` loads both endpoints — turn cards plus a **Cost / latency by tool** table (`fetchAgentToolMetrics`).

**Failure auto-retry (REAL):**
- LLM JSON action parse: `AgentLlmActionResolver` nudges and retries
- Transient tool **exceptions** (timeout / connection / 5xx / rate-limit): one automatic retry in `TreeFirstAgentService` (`AgentToolTransientRetry`); result may include `retried: true`. Business `status: ERROR` maps are not retried.

### Optional dashboard / BFF

Reference layout and BFF mapping live under `examples/agent-metrics-dashboard/`. Bind chart widgets to `GET /api/v1/ai/agent/metrics/tools` when you want the same series on an operator dashboard (canonical **84×8** grid — [dashboards](dashboards.md)).

---

## Related backlog

| ID | Feature | Doc |
|----|---------|-----|
| BL-177 | End-to-end agent deploy | [AgentDeployPlaybook](../../packages/ispf-server/src/main/java/com/ispf/server/ai/agent/AgentDeployPlaybook.java) |
| BL-178 | Agent regression suite | [agent-regression](agent-regression.md) |
| BL-179 | Solution generator draft API | This doc § Solution generator |
| BL-180 | Solution generator live apply + metrics widget | `AiSolutionGeneratorLiveSmokeTest`, playbook |
| BL-181 | Agent observability v2 | `/agent/metrics/tools`, [0034-agent-observability-and-session-knowledge](decisions/0034-agent-observability-and-session-knowledge.md) |
