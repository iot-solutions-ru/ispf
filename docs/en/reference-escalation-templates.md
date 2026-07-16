> **Language:** Canonical English. Russian edition: [ru/reference-escalation-templates.md](../ru/reference-escalation-templates.md).

# Escalation workflow templates (BL-123)

Reference patterns for **event correlator → workflow → operator task → timeout escalation** on ISPF without custom Java.

Artifacts: [examples/escalation-templates/](../../examples/escalation-templates/).

## Pattern A — Recurring threshold → workflow

| Step | Component | Config |
|------|-----------|--------|
| 1 | Alert rule (optional) | CEL on variable → `thresholdExceeded` event |
| 2 | Event correlator | COUNT: N events in window → `RUN_WORKFLOW` |
| 3 | Workflow | User task + parallel notify (see `demo-alarm-handler`) |

Template JSON: `recurring-threshold-correlator.json`.

Bootstrap (fixtures): correlator **Recurring threshold escalation** on `root.platform.devices.demo-sensor-01` → `root.platform.workflows.demo-alarm-handler`.

Acceptance: `EscalationChainAcceptanceTest` — 3× `thresholdExceeded` → work queue item "Acknowledge alarm".

## Pattern B — Ack timeout with boundary timer (BL-122)

When operator must ack within SLA, attach a **boundary timer** to the user task:

```xml
<userTask id="operatorAck" ispf:title="Acknowledge alarm" .../>
<boundaryEvent id="ackTimeout" attachedToRef="operatorAck"
               cancelActivity="true" ispf:durationSeconds="300"/>
<sequenceFlow sourceRef="ackTimeout" targetRef="escalate"/>
```

Full BPMN: `examples/escalation-templates/ack-timeout-escalation.bpmn.xml`.

| Phase | Instance status | Action |
|-------|-----------------|--------|
| After start | `WAITING` @ user task | Operator sees work queue item |
| Operator completes in time | `COMPLETED` | Normal close path |
| Timer fires | `COMPLETED` | Escalation path (NATS + log) |

Fire due timer manually or from scheduler:

```http
POST /api/v1/workflows/instances/{instanceId}/timer
{"operatorId":"operator-1"}
```

## Pattern C — Signal wake-up (existing)

For external systems (CMMS, ticketing), use **signal catch** instead of timer:

```xml
<intermediateCatchEvent id="waitTicket" ispf:signal="ticketClosed"/>
```

```http
POST /api/v1/workflows/instances/{instanceId}/signal
{"signal":"ticketClosed","operatorId":"operator-1"}
```

## Combining patterns

Typical production chain:

1. Correlator starts workflow on repeated alarms.
2. Workflow logs + notifies ops (parallel gateway).
3. User task with **boundary timer** for ack SLA.
4. Escalation branch publishes to NATS / invokes BFF function for supervisor paging.

## CI

`EscalationTemplateSmokeTest` parses `ack-timeout-escalation.bpmn.xml` and verifies boundary timer escalation path.

See also: [workflows](workflows.md), [automation](automation.md).
