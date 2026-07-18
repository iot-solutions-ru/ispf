> **Language:** Canonical English. Russian edition: [ru/tutorial-ot-workflow-journal.md](../ru/tutorial-ot-workflow-journal.md).

# Tutorial: workflow execution journal

> **Status:** Beta — ADR-0049 Wave 1. Hub: [OT Automation tutorials](ot-automation-excellence-tutorials.md).

## Goal

Run a simple BPMN workflow and inspect the **step-level timeline** (action, timing, redacted parameters) in the UI and via REST.

## Prerequisites

See [hub](ot-automation-excellence-tutorials.md#prerequisites). Create a `WORKFLOW` under `root.platform.workflows` (or your tenant path), e.g. `root.platform.workflows.journal-lab`.

## Steps

### 1. Minimal BPMN

In Workflow Builder, save BPMN with a `serviceTask` that logs:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
             xmlns:ispf="http://ispf.io/bpmn"
             targetNamespace="http://ispf.io/bpmn">
  <process id="journalLab" isExecutable="true">
    <startEvent id="start"/>
    <sequenceFlow sourceRef="start" targetRef="log1"/>
    <serviceTask id="log1" name="Log hello"
                 ispf:action="log"
                 ispf:message="journal-lab ok"/>
    <sequenceFlow sourceRef="log1" targetRef="end"/>
    <endEvent id="end"/>
  </process>
</definitions>
```

(Or draw Start → Service Task → End and set `ispf:action=log` in the ISPF properties panel.)

### 2. Run

**UI:** Workflow Builder → **Run**.

**API:**

```bash
curl -s -X POST "$BASE/api/v1/workflows/by-path/run?path=root.platform.workflows.journal-lab" \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"input":{}}'
```

Note `instanceId` from the response / instance list.

### 3. Open the timeline (UI)

In the same builder, select the instance. You should see a **timeline** of steps (`log` / service tasks) with status and timing.

### 4. Read steps (API)

```bash
# Recent runs
curl -s "$BASE/api/v1/workflows/by-path/runs?path=root.platform.workflows.journal-lab" \
  -H "Authorization: Bearer $TOKEN" | jq .

# Steps for one instance
curl -s "$BASE/api/v1/workflows/instances/$INSTANCE_ID/steps" \
  -H "Authorization: Bearer $TOKEN" | jq .
```

## Verify

- [ ] Run completes (`COMPLETED`)
- [ ] Timeline shows at least the `log` step
- [ ] `GET .../steps` returns ordered journal rows

## Notes

- AI steps (`llm_complete` / `invoke_agent`) store **prompt hash / length**, not the full prompt — see [AI in BPMN tutorial](tutorial-ot-ai-bpmn.md).
- Persistence table: `workflow_execution_steps` (Flyway V81).

## Next

[Workflow as tool](tutorial-ot-workflow-as-tool.md)
