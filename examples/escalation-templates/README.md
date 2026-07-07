# Escalation workflow templates (BL-123)

Copy-paste BPMN and correlator patterns for **alarm → operator ack → timeout escalation** and **recurring threshold → workflow**.

| Artifact | Purpose |
|----------|---------|
| `ack-timeout-escalation.bpmn.xml` | User task + **boundary timer** (300 s) → NATS escalation |
| `recurring-threshold-correlator.json` | COUNT correlator template (3 events / 5 min) |

Walkthrough: [docs/REFERENCE_ESCALATION_TEMPLATES.md](../../docs/REFERENCE_ESCALATION_TEMPLATES.md).

## Deploy workflow template

1. Create workflow object under `root.platform.workflows.*` with model `workflow-v1`.
2. Paste BPMN from `ack-timeout-escalation.bpmn.xml` into `bpmnXml`.
3. Set `status` = `ACTIVE`, `operatorAppId` = your Operator App id.
4. Trigger manually or via correlator / alert rule.

## Timer API

When instance waits on user task with boundary timer, call when deadline passes:

```http
POST /api/v1/workflows/instances/{instanceId}/timer
Content-Type: application/json

{"operatorId":"operator-1"}
```

Or rely on an external scheduler that polls `instanceState` and fires due timers.

## Correlator template

```http
POST /api/v1/correlators
Content-Type: application/json

< contents of recurring-threshold-correlator.json >
```

Platform bootstrap already seeds **Recurring threshold escalation** on demo sensor (fixtures profile).
