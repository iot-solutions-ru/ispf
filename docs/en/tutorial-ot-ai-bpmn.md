> **Language:** Canonical English. Russian edition: [ru/tutorial-ot-ai-bpmn.md](../ru/tutorial-ot-ai-bpmn.md).

# Tutorial: AI service tasks in BPMN

> **Status:** Beta — ADR-0049 Wave 2. Hub: [OT Automation tutorials](ot-automation-excellence-tutorials.md).

## Goal

Add `llm_complete` and (optionally) `invoke_agent` service tasks from the Workflow Builder palette, run them, and confirm journal redaction.

## Prerequisites

- [Hub](ot-automation-excellence-tutorials.md#prerequisites)
- Platform AI configured (`ISPF_AI_ENABLED` / provider / API key), or accept **noop** stub replies when AI is disabled
- Optional: [credentials vault](tutorial-ot-credentials-vault.md) for non-default `modelRef`

## Steps

### 1. Create AI tasks from the palette

In BPMN editor:

1. Open the left palette.
2. Drag **Create AI: LLM Complete** (or **Create AI: Invoke Agent**).
3. Defaults are pre-filled (`promptTemplate` / `goalTemplate`, `outputVariable`, `modelRef=platform-default`).
4. Adjust attributes in the ISPF properties panel.

| Action | Key attributes |
|--------|----------------|
| `llm_complete` | `promptTemplate`, `outputVariable`, `outputFormat`, `modelRef`, `timeoutMs` |
| `invoke_agent` | `goalTemplate`, `agentMode`, `toolAllowlist`, `maxSteps`, `outputVariable` |

`${var}` in templates is interpolated from workflow instance variables / run input.

### 2. Example: classify with LLM

```xml
<serviceTask id="classify" name="LLM classify"
             ispf:action="llm_complete"
             ispf:promptTemplate="Classify severity for alarm ${alarmId}. Reply JSON {severity,reason}"
             ispf:outputVariable="llmClassification"
             ispf:outputFormat="json"
             ispf:modelRef="platform-default"
             ispf:timeoutMs="30000"/>
```

Run with input:

```bash
curl -s -X POST "$BASE/api/v1/workflows/by-path/run?path=root.platform.workflows.ai-lab" \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"input":{"alarmId":"A-42"}}' | jq .
```

Check `instanceState.variables.llmClassification`.

### 3. `modelRef` options

| Value | Meaning |
|-------|---------|
| `platform-default` | Platform `ISPF_AI_*` settings |
| `gpt-4o-mini` (example) | Model id with platform API key / base URL |
| `root.platform.credentials.openai-lab` | Vault path: secret = API key; metadata may include `baseUrl`, `model` |

### 4. Bounded agent step

```xml
<serviceTask id="brief" name="Agent brief"
             ispf:action="invoke_agent"
             ispf:goalTemplate="Explain trend for ${tagPath} last 4h"
             ispf:agentMode="ask"
             ispf:toolAllowlist="get_variable_history,summarize_trend,detect_anomalies"
             ispf:maxSteps="8"
             ispf:outputVariable="agentBrief"/>
```

This is a **bounded** prompt with allowlist — not a free multi-turn operator session. CONTROL writes stay out of the default allowlist; use `userTask` for human confirmation.

### 5. Journal redaction

Open the instance timeline or `GET .../instances/{id}/steps`. For AI steps you should see `promptHash` / `promptChars`, **not** the full prompt/goal text.

## Verify

- [ ] Palette creates ServiceTask with `llm_complete` / `invoke_agent`
- [ ] Run stores result in `outputVariable`
- [ ] Journal does not store full prompts
- [ ] With AI disabled (noop), step still completes with a stub payload

## Next

[Triggers & recovery](tutorial-ot-workflow-triggers.md) · [Credentials vault](tutorial-ot-credentials-vault.md)
