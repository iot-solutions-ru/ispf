# ADR-0049: OT Automation Excellence

## Status

**Accepted** (2026-07-19) — Program ADR. Waves land incrementally; Wave 1 (execution journal + workflow tool contracts) is the foundation.

### Wave progress (2026-07-19)

| Wave | Notes |
|------|--------|
| **1** | Landed — `workflow_execution_steps`, REST/UI timeline, tool contracts, `invoke_workflow_tool` |
| **2** | Landed — `LLM_COMPLETE` / `INVOKE_AGENT`, analytics analysis functions + AI summarize path |
| **3** | **Partial** — webhook (`POST /api/v1/webhooks/workflows/{slug}` + `WorkflowWebhookIndex`), cron poller (`cronExpression=every:1m`), failure → `workflow_dead_letters` + `errorWorkflowPath`; DLQ list/resolve REST (`GET …/by-path/dead-letters`, `POST …/dead-letters/{id}/resolve`). Async retry scheduler still deferred (no sync retries). |
| **4–5** | Not started |

## Context

ISPF already has a custom BPMN subset ([0047](0047-custom-bpmn-subset-engine.md)), correlators/alerts ([0014](0014-automation-pipeline-evolution.md), [0039](0039-unified-alarm-architecture.md)), tree-first AI ([0005](0005-tree-first-ai-agent.md)), and an analytics function catalog ([0042](0042-analytics-function-catalog.md)). Gaps vs industrial “best of class” OT automation:

1. Coarse instance history (string list) — no step-level journal with payloads/timings.
2. Workflows are runnable tree objects but not typed callable tools for the agent.
3. No first-class LLM/agent `serviceTask` actions in BPMN.
4. Analytics AI surface is thin (`get_variable_history` / trend only); catalog lacks z-score / anomaly / period-compare helpers.
5. No webhook/cron workflow triggers, retry/DLQ, credentials vault, or MCP publish of workflow tools.

This ADR records the program without changing the BPMN subset freeze rules: new **serviceTask actions** and object variables are allowed; new BPMN **element types** still need an amendment to [0047](0047-custom-bpmn-subset-engine.md).

## Decision

Ship **OT Automation Excellence** in five waves:

| Wave | Scope |
|------|--------|
| **1** | `workflow_execution_steps` journal + REST/UI timeline; WORKFLOW `inputSchemaJson` / `outputSchemaJson` / `toolDescription` / `sideEffectClass`; agent `invoke_workflow_tool` |
| **2** | BPMN actions `LLM_COMPLETE`, `INVOKE_AGENT`; analytics functions `rollingStddev`, `zScore`, `percentile`, `trendSlope`, `periodOverPeriod`, `qualitySummary`, `anomalyScore`; agent analytics tools + `summarize_trend` (deterministic summary then LLM) |
| **3** | `triggerJson` webhook + cron; retry / `errorWorkflowPath` / `workflow_dead_letters` |
| **4** | Credentials vault (`root.platform.credentials.*`); marketplace installer for `workflow-template` |
| **5** | Aggregate MCP: workflow + analytics tools; publish selected ACTIVE workflows as tools |

### AI in BPMN (Wave 2)

- `LLM_COMPLETE` — single chat completion; template → `outputVariable`.
- `INVOKE_AGENT` — bounded turn with tool allowlist and `maxSteps`.
- CONTROL side-effects are **not** in the default agent allowlist; writes go through `userTask` then `INVOKE_FUNCTION` / `SET_VARIABLE`.
- All AI steps must appear in the execution journal (redacted prompt hash + structured output) and `ai_tool_audit` where applicable ([0034](0034-agent-observability-and-session-knowledge.md)).

### Analytics AI (Wave 2)

- Deterministic analysis functions first; LLM only summarizes a server-built summary payload.
- Statistical/ML forecast and stream inference remain **out of scope** for this ADR (track under BL-175+).

### Non-goals

- Embedding an external full BPMN product or a third-party agent-orchestration runtime.
- Arbitrary inline code nodes inside BPMN (use sandboxed functions via [0045](0045-java-function-sandbox.md)).
- Racing a large SaaS connector catalog; OT drivers and tree ACL remain the moat.
- HITL (`userTask`) over MCP — MCP tools are timeout-bounded, non-interactive.

## Consequences

- Operators get step-level audit for compliance and faster MTTR.
- Agents call typed ACTIVE workflows instead of ad-hoc writes.
- Analytics and BPMN share a consistent “compute then narrate” AI pattern.
- Wave order matters: journal before LLM tasks; tool contracts before MCP publish.

### Risks

- Journal volume growth — mitigate with retention cleanup and payload size caps.
- LLM non-determinism in processes — mitigate with schema validation, timeouts, and HITL for CONTROL.
- Subset confusion if docs lag new actions — update [workflows](../workflows.md) in the same change set as parser/executor.

## Related

- [0047-custom-bpmn-subset-engine](0047-custom-bpmn-subset-engine.md)
- [0014-automation-pipeline-evolution](0014-automation-pipeline-evolution.md)
- [0034-agent-observability-and-session-knowledge](0034-agent-observability-and-session-knowledge.md)
- [0042-analytics-function-catalog](0042-analytics-function-catalog.md)
- [0005-tree-first-ai-agent](0005-tree-first-ai-agent.md)
- [0006-mcp-agent-tool-adapter](0006-mcp-agent-tool-adapter.md)
- Product docs: [workflows](../workflows.md), [automation](../automation.md), [ai-development](../ai-development.md)
- Hands-on: [OT Automation Excellence tutorials](../ot-automation-excellence-tutorials.md)
