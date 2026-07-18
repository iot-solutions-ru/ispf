> **Language:** Canonical English. Russian edition: [ru/ot-automation-excellence-tutorials.md](../ru/ot-automation-excellence-tutorials.md).

# OT Automation Excellence — tutorials

> **Status:** Beta — Hands-on guides for ADR-0049. Hub: [doc-status.md](doc-status.md).

Program decision: [0049-ot-automation-excellence](decisions/0049-ot-automation-excellence.md).  
Reference (API tables, attribute lists): [workflows](workflows.md), [analytics formulas](analytics-formulas-and-packs.md), [AI development](ai-development.md).

These tutorials are **step-by-step**. Use them after you can open Web Console and create a `WORKFLOW` object ([workflows](workflows.md) lifecycle).

## Prerequisites

- ISPF ≥ **0.9.177** (ADR-0049 waves landed)
- Admin (or equivalent) Bearer token
- Web Console → Automation → Workflows (or object tree → `WORKFLOW`)
- Optional: AI enabled (`ISPF_AI_*`) for LLM / Ask AI labs; MCP enabled (`ispf.mcp.enabled=true`) for MCP publish labs

```bash
export BASE=https://ispf.iot-solutions.ru   # or http://localhost:8080
export TOKEN=$(curl -s -X POST "$BASE/api/v1/auth/login" \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin"}' | jq -r .token)
```

## Learning path

| # | Tutorial | Time | You will learn |
|---|----------|------|----------------|
| 1 | [Execution journal](tutorial-ot-workflow-journal.md) | ~10 min | Run a workflow and read the step timeline |
| 2 | [Workflow as tool](tutorial-ot-workflow-as-tool.md) | ~15 min | Typed input/output + `invoke_workflow_tool` + MCP `wf_*` |
| 3 | [AI in BPMN](tutorial-ot-ai-bpmn.md) | ~20 min | Palette `llm_complete` / `invoke_agent`, `modelRef` |
| 4 | [Triggers & recovery](tutorial-ot-workflow-triggers.md) | ~15 min | Webhook, cron, `errorWorkflowPath`, DLQ |
| 5 | [Credentials vault](tutorial-ot-credentials-vault.md) | ~10 min | Store API keys; point `modelRef` at vault paths |
| 6 | [Analytics AI](tutorial-ot-analytics-ai.md) | ~15 min | Agent analysis tools + Ask AI on a tag |

Recommended order: **1 → 2 → 3**; then 4–6 as needed. Journal (1) before AI steps (3); tool contracts (2) before MCP publish.

## What is intentionally out of scope

- New BPMN **element types** (still frozen by [ADR-0047](decisions/0047-custom-bpmn-subset-engine.md))
- Inline code nodes in BPMN
- Forecast / ML inference (BL-175+)
- HITL (`userTask`) over MCP

## Related

- [workflows](workflows.md) — canonical reference
- [automation](automation.md) — alerts / correlators
- [ai-agent](ai-agent.md) — agent API
- [0006-mcp-agent-tool-adapter](decisions/0006-mcp-agent-tool-adapter.md)
