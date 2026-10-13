# ADR-0049: OT Automation Excellence

## Status

**Accepted** (2026-07-19) — **program ADR**: five waves over the BPMN subset freeze ([0047](0047-custom-bpmn-subset-engine.md)). Wave progress is a honesty table, not a second Decision.

## Context

ISPF already has a custom BPMN subset, correlators/alerts, tree-first AI, and an analytics catalog. Gaps vs industrial OT automation: step-level workflow journal, typed workflow-as-tool, AI `serviceTask` actions, richer analytics AI helpers, webhook/cron + retry/DLQ, credentials vault, MCP publish of workflow tools.

This record sets the program boundary without unfreezing BPMN **element types** (new **actions** / variables OK; new element types need a [0047](0047-custom-bpmn-subset-engine.md) amendment).

## Decision

Ship **OT Automation Excellence** as five waves:

| Wave | Scope | Status |
|------|--------|--------|
| **1** | Execution journal + workflow tool contracts + `invoke_workflow_tool` | **Done** |
| **2** | BPMN `LLM_COMPLETE` / `INVOKE_AGENT`; analytics analysis helpers + summarize path | **Done** |
| **3** | Webhook + cron triggers; retry / DLQ / `errorWorkflowPath` | **Done** |
| **4** | Credentials vault; marketplace `workflow-template` installer | **Partial** — encrypt + [0065](0065-secrets-key-rotation.md) Done; marketplace installer **open** |
| **5** | Aggregate MCP publish of workflow + analytics tools | **Gap** |

### Invariants (all waves)

- AI steps journaled (redacted) + `ai_tool_audit` where applicable ([0034](0034-agent-observability-and-session-knowledge.md)).
- CONTROL side-effects stay out of the default agent allowlist (HITL / explicit function).
- Analytics AI: deterministic compute first; LLM only narrates a server-built summary.
- No external full BPMN product; no arbitrary inline code nodes ([0045](0045-java-function-sandbox.md)).

### Non-goals

- Racing a large SaaS connector catalog.
- HITL `userTask` over MCP (timeout-bounded, non-interactive tools).

## Consequences

- Operators get step-level audit; agents call typed ACTIVE workflows.
- Wave order: journal before LLM tasks; tool contracts before MCP publish.
- How-to and demos: [ot-automation-excellence-tutorials](../ot-automation-excellence-tutorials.md) — not duplicated here.
- Risks: journal volume (retention caps); LLM non-determinism (schema + HITL for CONTROL).

## Related

- [0047-custom-bpmn-subset-engine](0047-custom-bpmn-subset-engine.md)
- [0065-secrets-key-rotation](0065-secrets-key-rotation.md) — vault envelope (Wave 4)
- [0006-mcp-agent-tool-adapter](0006-mcp-agent-tool-adapter.md) — Wave 5 target surface
- [workflows](../workflows.md), [automation](../automation.md), [ai-development](../ai-development.md)
- [ot-automation-excellence-tutorials](../ot-automation-excellence-tutorials.md)
