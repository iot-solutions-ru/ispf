> **Language:** Canonical English. Russian edition: [ru/tutorial-ot-workflow-as-tool.md](../ru/tutorial-ot-workflow-as-tool.md).

# Tutorial: workflow as a typed tool

> **Status:** Beta — ADR-0049 Wave 1 / 5. Hub: [OT Automation tutorials](ot-automation-excellence-tutorials.md).

## Goal

Expose an ACTIVE workflow as a **typed callable tool** for the agent (`invoke_workflow_tool`) and optionally as an MCP tool `wf_<name>`.

## Prerequisites

[Hub](ot-automation-excellence-tutorials.md#prerequisites). Prefer a workflow that only reads/logs first (`sideEffectClass=READ` or `WRITE` without CONTROL).

## Steps

### 1. Define schemas on the WORKFLOW object

Set variables (Object inspector or API `set_variable` / tree UI):

| Variable | Example |
|----------|---------|
| `inputSchemaJson` | `{"type":"object","required":["alarmId"],"properties":{"alarmId":{"type":"string"}}}` |
| `outputSchemaJson` | `{"type":"object","properties":{"result":{"type":"string"}},"project":["result"]}` |
| `toolDescription` | `Classify a lab alarm id and return result` |
| `sideEffectClass` | `WRITE` |
| `status` | `ACTIVE` |

`outputSchemaJson` projects from **instance variables** (run `input` keys, AI `outputVariable`, `read_variable` → `contextKey`).  
Note: `set_variable` writes to the **object tree**, not instance variables — use AI/`read_variable`/input keys for tool `output`, or project fields that were passed in `input`.

### 2. Invoke via REST (tool contract)

```bash
curl -s -X POST "$BASE/api/v1/workflows/by-path/invoke-tool?path=root.platform.workflows.alarm-tool" \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"input":{"alarmId":"A-100"}}' | jq .
```

Expect `status=OK`, `output` projected from `outputSchemaJson`. Missing required keys → validation error.

### 3. Invoke from the agent

In AI Studio (admin agent), ask the model to call:

- Tool: `invoke_workflow_tool`
- Args: `{ "path": "root.platform.workflows.alarm-tool", "input": { "alarmId": "A-100" } }`

Only **ACTIVE** workflows succeed.

### 4. Publish on MCP (optional)

Requirements:

- `ispf.mcp.enabled=true`
- Workflow `ACTIVE` + non-blank `toolDescription`

```bash
curl -s -X POST "$BASE/api/v1/ai/mcp" \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"jsonrpc":"2.0","id":1,"method":"tools/list","params":{}}' | jq '.result.tools[] | select(.name|startswith("wf_"))'
```

Call:

```bash
curl -s -X POST "$BASE/api/v1/ai/mcp" \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{
    "jsonrpc":"2.0","id":2,"method":"tools/call",
    "params":{"name":"wf_alarm_tool","arguments":{"alarmId":"A-100"}}
  }' | jq .
```

Tool name is `wf_<last-path-segment>` (collision suffix if needed). Description includes `[workflow:<path>]`.

## Verify

- [ ] `invoke-tool` rejects missing `alarmId`
- [ ] Happy path returns projected `output`
- [ ] Agent tool works on ACTIVE only
- [ ] (Optional) `wf_*` appears in MCP `tools/list`

## Safety

- Prefer `sideEffectClass=READ` / `WRITE` for agent/MCP exposure.
- **CONTROL** effects: keep behind `userTask` + `invoke_function` / `set_variable`, not default operator allowlist.

## Next

[AI in BPMN](tutorial-ot-ai-bpmn.md) · [Credentials vault](tutorial-ot-credentials-vault.md)
