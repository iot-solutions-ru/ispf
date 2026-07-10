> **Language:** Canonical English. Russian edition: [ru/ai-development.md](../ru/ai-development.md).

# AI Development Layer (REQ-FW-40…44)

Platform-side development infrastructure for solution developers. AI reads curated context, generates **declarative bundle JSON**, runs validation gates, and publishes through the existing deploy API. The **tree-first agent** (FW-44) operates on the live object tree step-by-step.

**Target approach:** AI does not write Java/React in `main`; only validated artifacts (bundle, models, dashboards, functions, events) and tree nodes via platform tools.

See [0004-ai-artifact-generation-gates](decisions/0004-ai-artifact-generation-gates.md) and [0005-tree-first-ai-agent](decisions/0005-tree-first-ai-agent.md).

---

## Components

| ID | Component | Location |
|----|-----------|----------|
| FW-40 | `LlmProvider` SPI | `packages/ispf-ai-api`, adapters `ispf-ai-openai-compatible`, `ispf-ai-ollama` |
| FW-41 | ContextPack | `ai/context/`, `tools/ai-pack/build.py`, classpath `ai/context-pack.json` |
| FW-42 | ToolRegistry | `com.ispf.server.ai.tool.AiToolRegistry`, REST `/api/v1/ai/tools/**` |
| FW-43 | Platform Studio | Web Console tab **AI Studio** (`apps/web-console`) |
| FW-44 | Tree-first agent | `com.ispf.server.ai.agent.*`, REST `/api/v1/ai/agent/**` |
| FW-44b | MCP adapter | `com.ispf.server.ai.mcp.*`, profile `mcp`, REST `/api/v1/ai/mcp` |
| FW-45 | Platform knowledge briefing | `PlatformBriefingService`, `ContextPackSearchService`, agent tools |
| FW-46 | Agent knowledge base | [agent-knowledge](agent-knowledge.md) — application approaches, docs map |
| FW-47 | Agent discovery tools | `AgentDiscoveryTools` — functions, events, variable schemas |
| FW-48 | Agent automation tools | `AgentAutomationTools` — alerts, correlators, operator UI, `create_variable`, cluster playbooks |

---

## ContextPack (FW-41)

Build from docs and `examples/*/bundle.json`:

```bash
python tools/ai-pack/build.py
```

Outputs:

- `ai/context/generated/ispf-context-pack.json`
- `packages/ispf-server/src/main/resources/ai/context-pack.json`

Pack includes bundle schema fields, script steps, widget types, API doc slices, reference examples, **driverCatalog**, **featureIndex**, **exampleSummaries**, **docCatalog** (index of all `docs/*.md`), and **docChunks** for scored search.

Primary agent router doc: [agent-knowledge](agent-knowledge.md) (`search_context topic=agent-knowledge`).

CI and release workflows run `python tools/ai-pack/build.py` before server tests/build (`ISPF_VERSION` from tag in release).

---

## Platform briefing (FW-45)

Each agent chat injects a compact **Platform knowledge (auto)** block into the system prompt via `PlatformBriefingService`:

| Block | Source |
|-------|--------|
| Drivers | Live `DriverCatalog` + maturity |
| Virtual profiles | Built-in cheat sheet |
| Reference examples | ContextPack `exampleSummaries` |
| Features | ContextPack `featureIndex` |
| Live snapshot | Deployed apps + bundle versions + tree children under session `rootPath` |

Config:

```yaml
ispf:
  ai:
    briefing-max-chars: 12000
    briefing-every-turn: false   # true = full static briefing every turn; default first turn only + live always
```

On follow-up turns (default): static drivers/examples/features omitted; **live snapshot** still refreshed.

---

## ToolRegistry (FW-42)

Admin-only REST API:

| Endpoint | Purpose |
|----------|---------|
| `GET /api/v1/ai/tools/context-pack` | Context pack metadata |
| `POST /api/v1/ai/tools/validate-bundle` | Semantic bundle validation (no DB writes) |
| `POST /api/v1/ai/tools/dry-run-deploy` | Validate + list `wouldApply` sections |
| `GET /api/v1/ai/models` | List models from active LLM provider |
| `POST /api/v1/ai/bundles/generate` | Prompt → bundle → validate → dry-run |

Request body for validate/dry-run:

```json
{
  "appId": "warehouse",
  "manifest": { "version": "1.0.0", "...": "..." }
}
```

**Acceptance path:** prompt → generated JSON → `validate-bundle` → CI green → `POST /api/v1/platform/packages/import`.

Audit log: table `ai_tool_audit` (migration `V37__ai_tool_audit.sql`).

**Retention:** rows are append-only; no automatic purge in OSS. Session turns in `agent_sessions` / `agent_turns` follow `ispf.ai.agent-session-ttl-hours` (default 24h). For compliance, export via `GET .../audit?format=csv` before TTL eviction. `app_id` column stores agent `sessionId` for tree-first runs.

---

## Tree-first agent (FW-44)

ReAct loop on the platform with a hard step cap (default 96, `ispf.ai.agent-max-steps`). The agent runs until it finishes or hits the cap — **no mid-run pauses**. Cooperative **cancel** while running (`POST .../cancel`); live steps via `GET .../progress` (UI polls ~1s). Admin-only.

**Multi-turn sessions** (Cursor-like): each chat is a server-side in-memory session with turn history replayed to the LLM (compact user/assistant summaries, no raw tool JSON). `AgentRunState` (validate → import gates) persists for the whole chat.

| Endpoint | Purpose |
|----------|---------|
| `GET /api/v1/ai/agent/tools` | Catalog of platform tools |
| `POST /api/v1/ai/agent/sessions` | Create empty session `{ sessionId, title: "New chat", ... }` |
| `GET /api/v1/ai/agent/sessions/{id}` | Restore chat turns for UI |
| `GET /api/v1/ai/agent/sessions/{id}/progress` | Live steps while a turn is running (`running`, `steps[]`) |
| `POST /api/v1/ai/agent/sessions/{id}/messages` | Send message → run until finish/cancel/limit |
| `POST /api/v1/ai/agent/sessions/{id}/cancel` | Request cooperative cancel of in-flight turn |
| `DELETE /api/v1/ai/agent/sessions/{id}` | Delete session (clear context) |
| `GET /api/v1/ai/agent/sessions/{id}/audit` | Admin-only audit export (`format=json` default, `format=csv` for compliance) |
| `GET /api/v1/ai/agent/sessions/{id}/trace` | Turn trace: steps + audit metrics (latency, tokens) |
| `GET /api/v1/ai/agent/metrics` | Aggregated agent metrics (`days` query param) |
| `POST/GET/DELETE /api/v1/ai/agent/sessions/{id}/documents` | Session-scoped knowledge files |
| `POST /api/v1/ai/agent/run` | **Deprecated** one-shot run (no session store); prefer sessions API |

See [0034-agent-observability-and-session-knowledge](decisions/0034-agent-observability-and-session-knowledge.md) (FW-49…53).

---

## Agent observability (FW-49…53)

| ID | Feature | Location |
|----|---------|----------|
| FW-49 | Audit metrics + trace API + `AgentTracePanel` | `AiToolAuditStore`, `AgentTraceService`, Web Console |
| FW-50 | `agent_session_documents`, `search_session_context` | `AgentSessionDocumentService` |
| FW-51 | Turn graph tab | `AgentTurnGraph.tsx` |
| FW-52 | `GET /agent/metrics`, `AgentPromptVersions` | `AgentMetricsService`, AI Studio |
| FW-53 | `PlanDepth.LITE` default | `AgentSpecPlanValidator`, `AgentPlanPromptSection` |

**Ask mode** uses dedicated [`AgentAskPromptBuilder`](../packages/ispf-server/src/main/java/com/ispf/server/ai/agent/AgentAskPromptBuilder.java) — no planning pipeline.

Audit columns (migration `V67__ai_tool_audit_metrics.sql`): `latency_ms`, `prompt_tokens`, `completion_tokens`, `turn_id`, `step_no`, `interaction_mode`, `prompt_profile`.

Create session:

```json
{ "rootPath": "root" }
```

Send message:

```json
{
  "message": "Create SNMP localhost and a dashboard with CPU",
  "rootPath": "root"
}
```

Legacy one-shot run (still supported):

```json
{
  "goal": "List devices under root.platform.devices and create lab-sensor-1",
  "rootPath": "root"
}
```

Response includes `steps[]` (tool calls + results), `summary`, and `status` (`OK` when the model emits `finish`; `CANCELLED` when the user stops; `ERROR` on parse failure or step limit).

Platform tools (Java handlers, ACL-aware):

| Tool | Purpose |
|------|---------|
| `search_context` | Scored search over docChunks, drivers, functions, examples (`topic` optional) |
| `list_drivers` | Live driver catalog filter by query/maturity |
| `get_driver_help` | Driver config slice from context pack |
| `list_examples` | Reference bundle index |
| `get_example_bundle` | Manifest subset for appId (e.g. mes-reference) |
| `list_applications` | Registered apps + active bundle versions |
| `list_functions` | Callable functions on object (tree + deployed BFF) |
| `get_function` | Function input/output schema |
| `list_event_catalog` | Bundle event catalog for appId |
| `get_event_schema` | Event payload schema for fire_event |
| `describe_variables` | Variable field schemas (writable, history) |
| `invoke_bff` | Application BFF function (`mes_listOrders`, …) with wire result |
| `invoke_tree_function` | Invoke function on object path (raw rows) |
| `search_objects` | Search tree by query, optional type/parentPrefix |
| `list_object_models` | Platform model templates (`templateId` for create_object) |
| `fire_event` | Fire object event (optional appId for catalog schema) |
| `list_events` | Recent event journal entries |
| `list_objects` | List tree children |
| `get_object` | Read one node |
| `create_object` | Create DEVICE, DASHBOARD, CUSTOM, … |
| `delete_object` | Delete tree node by path (stops device driver if running) |
| `get_dashboard_layout` | Read layout JSON from dashboard path or built-in template |
| `set_dashboard_layout` | Replace layout from JSON or template (`snmp-host-monitoring`, `virtual-cluster-overview`, …) |
| `add_dashboard_widget` | Append one widget to `layout.widgets[]` |
| `configure_alert` | Create/update ALERT rule (CEL condition → event) |
| `configure_correlator` | Create/update event correlator |
| `configure_operator_ui` | Operator HMI default dashboard + menu |
| `create_variable` | New variable with refAt/CEL binding (CUSTOM hub logic) |
| `list_automation` | List alert rules and correlators |
| `get_automation_schema` | Reference for alert/correlator/dashboard/binding/operator fields |
| `list_variables` | Read object variables + values |
| `set_variable` | Update variable (config, dashboard layout, …) |
| `configure_driver` | SNMP/driver config + mappings + optional start |
| `driver_control` | start / stop / poll / status |
| `validate_bundle` | 0004 gate (no DB writes) |
| `dry_run_deploy` | Validate + `wouldApply` |
| `import_package` | Deploy bundle (requires prior validate/dry-run OK in same run) |

LLM replies with one JSON object per turn: `{"type":"tool","name":"...","arguments":{...}}` or `{"type":"finish","summary":"...","result":{...}}`.

### Agent reliability (failure modes)

| Failure | Mitigation |
|---------|------------|
| Model returns prose/markdown | `AgentLlmActionResolver` retries with JSON-only nudge (`ispf.ai.agent-parse-retries`, default **5**) |
| `type:function` / missing `type` / nested `function` | `AgentJsonProtocol` normalizes common LLM variants |
| Widget JSON chosen instead of action | Parser accepts only `tool`/`finish` (and aliases); ignores `type:DASHBOARD` in arguments |
| Playbook `%s` crash (`Format specifier`) | Playbooks use concatenation only; `AgentPromptStartupValidator` fails boot if `%s` remains |
| `search_context` loop | `AgentLoopGuard` injects stop hints after 3 repeats; dashboard tools documented inline |
| Step cap reached | Turn ends with `OK` + `stepLimitReached` + suggestion "Continue" (`agent-max-steps`, default 256) |
| Unparseable response after retries | Turn returns `status: ERROR` + human summary (session preserved); audit `agent_parse_error` |

Session history replays compact assistant summaries (max 800 chars per turn).

Configure step cap and output tokens:

```yaml
ispf:
  ai:
    timeout-seconds: 600
    agent-max-steps: 256
    agent-max-tokens: 131072   # ~50% of 256k context for completion; prompt uses the rest
    agent-parse-retries: 5
    agent-max-text-inject-chars: 524288   # ~512 KB TZ/spec inline
    agent-max-attachment-bytes: 33554432  # 32 MB upload
    agent-session-ttl-hours: 24
    agent-max-history-turns: 128
    max-tokens: 65536   # bundle generation completion cap
```

Env: `ISPF_AI_TIMEOUT_SECONDS`, `ISPF_AI_AGENT_MAX_STEPS`, `ISPF_AI_AGENT_MAX_TOKENS`, `ISPF_AI_AGENT_MAX_TEXT_INJECT_CHARS`, `ISPF_AI_AGENT_MAX_ATTACHMENT_BYTES`, `ISPF_AI_AGENT_MAX_HISTORY_TURNS`.

`max-tokens` (default **65536**, env `ISPF_AI_MAX_TOKENS`) — **response** limit for bundle generation.

`agent-max-tokens` (default **131072**, env `ISPF_AI_AGENT_MAX_TOKENS`) — **response** limit per agent turn.

256k on Qwen/vLLM is the **context window** (prompt + completion). Defaults above assume `max-model-len=262144`: up to ~512 KB spec + system/tools/history in the prompt, up to 128k tokens for completion. Do not set `agent-max-tokens=262144` — the prompt leaves no room. vLLM on the inference host must allow `max_tokens` ≥ 131072.

Sessions are **persisted in PostgreSQL** (`agent_sessions`, `agent_turns`) with TTL eviction (default 24 hours, `ispf.ai.agent-session-ttl-hours`). JVM restart keeps chat history until TTL; Web Console stores chat index in `localStorage` and reloads turns via `GET session`.

---

## MCP adapter (0006)

Optional profile **`mcp`** exposes the same platform agent tools to external MCP clients (Cursor, CI) without duplicating handlers.

| Transport | Endpoint/mode | Auth |
|-----------|---------------|------|
| HTTP JSON-RPC | `POST /api/v1/ai/mcp` | Same as agent API (admin bearer/local role header) |
| stdio | `ispf.mcp.stdio-enabled=true` | Actor `mcp-stdio` (local dev only) |

Enable:

```yaml
spring:
  profiles:
    active: local,mcp

ispf:
  mcp:
    enabled: true
    server-name: ispf-platform
    stdio-enabled: false   # true for Cursor stdio subprocess
```

Methods: `initialize`, `tools/list`, `tools/call`, `resources/list`, `resources/read`, `ping`. Tool calls delegate to `PlatformAgentToolRegistry`; optional `sessionId` in arguments binds to DB agent sessions. Audit rows use `source=mcp` and tool prefix `mcp_<name>`.

### ContextPack resources (Phase 17.3)

MCP clients can read static ContextPack slices without tool calls:

| URI | Content |
|-----|---------|
| `contextpack://info` | Version, counts |
| `contextpack://bundle-manifest` | Bundle fields and rules |
| `contextpack://script-steps` | Script step names |
| `contextpack://widget-types` | Widget catalog |
| `contextpack://driver-catalog` | Driver index |
| `contextpack://feature-index` | Platform features |
| `contextpack://example-summaries` | Reference bundles |
| `contextpack://doc-chunks` | Documentation chunks |

Example: `{"method":"resources/read","params":{"uri":"contextpack://script-steps"}}`

Cursor example (HTTP to local server):

```json
{
  "mcpServers": {
    "ispf": {
      "url": "http://localhost:8080/api/v1/ai/mcp",
      "headers": { "Authorization": "Bearer <admin-token>" }
    }
  }
}
```

See [0006-mcp-agent-tool-adapter](decisions/0006-mcp-agent-tool-adapter.md).

---

## LlmProvider SPI (FW-40)

Configure via `application.yml` or env:

```yaml
ispf:
  ai:
    enabled: true
    provider: noop            # noop | openai-compatible | ollama | custom-url
    base-url: https://api.openai.com/v1
    model: gpt-4o-mini
    api-key-env: OPENAI_API_KEY
    timeout-seconds: 60
    max-tokens: 16384
    temperature: 0.2
    agent-max-concurrent-turns-per-user: 2
    agent-max-turns-per-hour-per-user: 120
    agent-require-approval-for-mutate: true   # BL-106; set false in dev profile
```

**Mutating tools approval (BL-106):** when `ispf.ai.agent-require-approval-for-mutate` is `true` (default on prod/local VPS), non-read-only agent tools (`create_object`, `deploy_bundle`, …) are blocked until the user approves the plan. Approval is recorded in `ai_tool_audit` as `agent_plan_approved` with the approver username. UI: sticky **Approve plan** banner when `planPhase=awaiting_approval`.

**Reference scenarios (BL-108):** `docs/en/agent-scenarios/`, classpath `agent-scenarios/catalog.json`, API `GET /api/v1/ai/agent/scenarios`, AI Studio Scenarios tab.

Prometheus (when micrometer enabled): `ispf.agent.turns.started.total`, `ispf.agent.turns.rate_limited.total`, `ispf.agent.turns.completed.total`, `ispf.agent.guard.blocks.total` (tag `guard`), gauges `ispf.agent.turns.last_hour`, `ispf.agent.turn.steps.avg`.

**Agent SLO dashboard (BL-110):** when `ispf.ai.enabled=true`, System → Metrics includes section **AI Agent SLO** (`turnsLastHour`, `turnsRateLimitedTotal`, `guardBlocksByType`, `avgStepsPerTurn`).

| Provider | `base-url` example |
|----------|-------------------|
| `openai-compatible` | `https://api.openai.com/v1` |
| `ollama` | `http://localhost:11434` |
| `custom-url` | Any OpenAI-compatible endpoint |
| `noop` | Default — validate/dry-run work; generate returns 503 |

API keys are read from env var name in `api-key-env`; never stored in audit log.

---

## Platform Studio (FW-43)

Web Console → **AI Studio** tab (admin only). UI in English; sections: **Agent** | **Bundle** | **Settings**.

**Tree-first agent** (Agent tab):

1. Sidebar: horizontal chat strip + "+ New"; tool list on Settings tab
2. Describe the task in plain language — follow-up messages keep context in the same chat
3. Agent runs platform steps and replies in plain text
4. Under "Details (N steps)" — numbered list with `<code>` tool name and status badge; "Open device" / "Open dashboard" links
5. Delete chat — × button on chat pill
6. Chat does not unmount when switching AI Studio tabs or Explorer section; HTTP request continues server-side
7. If browser tab closes during a request — response recovers on next open (poll `GET /agent/sessions/{id}`)
8. On phone: compact toolbar, full-width input

**Settings**: provider and Context Pack status, `defaultRootPath`, `defaultAppId`, restore last chat, agent tool list.

Example: *"Create SNMP localhost, CPU/RAM/network metrics and a dashboard"* — device `snmp-localhost`, driver `snmp`, dashboard `snmp-host-monitoring`.

**Bundle generate** (Bundle tab):

1. Set `appId`
2. Prompt → **Generate** (requires configured LLM)
3. **Validate** / **Dry-run deploy**
4. **Publish** → `POST /api/v1/platform/packages/import`
5. **Operator preview** → `?mode=operator&app={appId}`

Studio does not add new bundle sections; it uses the same manifest contract as manual import.

**Federation** (separate section `root.platform.federation`): Nodes / Tokens / Tunnel / Probe tabs — see [web-console](web-console.md).

---

## Bundle contract

AI output must match [solution-developer-public-api](solution-developer-public-api.md). Optional provenance in `metadata`:

```json
{
  "metadata": {
    "generatedBy": "ai-studio",
    "contextPackVersion": "ispf-0.1.0-SNAPSHOT",
    "promptId": "..."
  }
}
```

Commercial bundles: sign **after** AI edits (`contentSha256` covers manifest body).

---

## Related documents

- [agent-knowledge](agent-knowledge.md) — application approaches, full doc index for agent
- [plugins](plugins.md) — external LLM provider (like drivers)
- [applications](applications.md) — deploy API
- [dashboards](dashboards.md) — widget registry for generated dashboards
- [roadmap.md § Part B (FW-40…48)](roadmap.md)
