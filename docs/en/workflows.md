> **Language:** Canonical English. Russian edition: [ru/workflows.md](../ru/workflows.md).

# Workflow & BPMN

## Overview

A workflow is an object of type `WORKFLOW` with model `workflow-v1`. The engine is pure Java in `ispf-plugin-workflow` (no Camunda/Flowable).

Workflow object variables:

| Variable | Description |
|----------|-------------|
| `title` | Display name |
| `status` | `DRAFT` / `ACTIVE` / `STOPPED` |
| `bpmnXml` | BPMN 2.0 XML |
| `triggerJson` | Variable-change trigger |
| `operatorAppId` | Operator App for user tasks in the sidebar |
| `instanceState` | JSON state of the last instance |
| `lastRunAt` | Time of the last run |
| `lastAction` | Last service task action |

## Lifecycle

**Workflow object:**
- `DRAFT` — editing, manual run
- `ACTIVE` — listens for triggers + manual run
- `STOPPED` — does not start

**Instance (`InstanceStatus`):**
- `RUNNING` — executing
- `WAITING` — waiting for user task, signal catch, or timer
- `COMPLETED` — finished
- `FAILED` — error

## Supported BPMN elements

| Element | Support |
|---------|---------|
| `startEvent` | Yes |
| `endEvent` | Yes |
| `serviceTask` | LOG, SET_VARIABLE, PUBLISH_NATS, **INVOKE_FUNCTION** |
| `userTask` | Operator queue, claim/complete |
| `intermediateCatchEvent` | Signal wait (`ispf:signal`) or timer (`ispf:durationSeconds`) |
| `boundaryEvent` | Timer on user task (`attachedToRef`, `ispf:durationSeconds`, `cancelActivity`) |
| `messageTask` | NATS publish (if enabled) |
| `exclusiveGateway` | Conditional transitions (CEL) |
| `parallelGateway` | Fork/join, execution tokens |
| `subProcess` | Embedded subprocess — enter inner start, exit on inner end (BL-176 stub) |
| `sequenceFlow` | `ispf:condition`, `ispf:default` |

Extension namespace: `http://ispf.io/bpmn` (prefix `ispf:`).

## ISPF attributes

### serviceTask

```xml
<serviceTask id="log1" name="Log alarm"
             ispf:action="log"
             ispf:message="Threshold exceeded"/>
```

| Action | Parameters |
|--------|------------|
| `log` | `ispf:message` |
| `set_variable` | `ispf:targetPath`, `ispf:variable`, `ispf:value` |
| `publish_nats` | `ispf:subject`, `ispf:message`, `ispf:channel` |
| `invoke_function` | `ispf:objectPath`, `ispf:functionName`, `ispf:inputMap`, `ispf:outputMap` |

Example `invoke_function` (application functions — [applications.md](applications.md)):

```xml
<serviceTask id="assign" name="Assign tank"
             ispf:action="invoke_function"
             ispf:objectPath="root.platform.devices.demo-sensor-01"
             ispf:functionName="myapp_acknowledge"
             ispf:inputMap="orderId=${workflow.orderId}"
             ispf:outputMap="assignResult=result"/>
```

### userTask

```xml
<userTask id="approve" name="Approve"
          ispf:title="Confirm alarm"
          ispf:instructions="Check the sensor"
          ispf:assigneeRole="operator"
          ispf:targetObjectPath="root.platform.devices.demo-sensor-01"
          ispf:function="acknowledgeAlarm"/>
```

### sequenceFlow

```xml
<sequenceFlow sourceRef="gw" targetRef="approve" ispf:condition="needsApproval"/>
<sequenceFlow sourceRef="gw" targetRef="end" ispf:default="true"/>
```

The condition is a CEL expression evaluated in the context of workflow instance variables.

### messageTask

```xml
<messageTask id="notify" name="Notify"
             ispf:subject="ispf.ops.alarm"
             ispf:message="Alarm fired"
             ispf:channel="nats"/>
```

### intermediateCatchEvent (signal)

```xml
<intermediateCatchEvent id="waitIncident" name="Wait incident"
                        ispf:signal="incidentRegistered"/>
```

The instance moves to `WAITING` until the signal is delivered. As an alternative to cancellation via the cancel API, an incident can "wake" the process and continue the handling branch.

### intermediateCatchEvent (timer)

```xml
<intermediateCatchEvent id="waitDelay" name="Wait delay"
                        ispf:durationSeconds="300"/>
```

The instance waits until the deadline expires; continuation via `POST .../timer` (see below) or the scheduler.

### boundaryEvent (timer on user task)

```xml
<boundaryEvent id="ackTimeout" attachedToRef="operatorAck"
               cancelActivity="true" ispf:durationSeconds="300"/>
<sequenceFlow sourceRef="ackTimeout" targetRef="escalate"/>
```

While the user task is in `WAITING`, an SLA timer runs in parallel. When the timer fires, the escalation branch runs (interrupting). Template: [reference-escalation-templates.md](reference-escalation-templates.md).

## Work queue

User tasks appear in `GET /api/v1/work-queue`.

### Operator App binding

On the workflow object (model `workflow-v1`), set the **`operatorAppId`** variable — the identifier of the Operator App whose sidebar **Tasks** panel receives user tasks from this process.

- Configuration: workflow editor → **Operator App** panel (or `PUT /api/v1/workflows/by-path/operator-app`)
- When a user task is created, the value is copied to `workflow_user_tasks.operator_app_id`
- Sidebar filter: `GET /api/v1/work-queue?operatorAppId=platform`

If `operatorAppId` is empty, the task **does not appear** in the Operator Apps sidebar (the `work-queue` widget on a dashboard still shows all tasks).

| Task status | Action |
|-------------|--------|
| `OPEN` | claim → `CLAIMED` |
| `CLAIMED` | complete → workflow continues |

```http
POST /api/v1/work-queue/claim?taskId=...&operatorId=operator
POST /api/v1/work-queue/complete?taskId=...&operatorId=operator
```

When `ispf:function` is set on a user task, the function is invoked on complete.

## Triggers

`triggerJson` on the workflow object. `WorkflowTriggerListener` compares variable changes against conditions and starts an ACTIVE workflow.

## Event correlators → workflow

Correlator with action `RUN_WORKFLOW` and `actionTarget` = workflow path. See [automation.md](automation.md).

## Demo workflow

`root.platform.workflows.demo-alarm-handler` — BPMN with gateway, user task, service log.

Definition: `WorkflowDefinitions.DEMO_ALARM_HANDLER`.

## UI

- **WorkflowBuilder** — status, run, BPMN editor (bpmn-js)
- **BpmnDiagramEditor** / **BpmnDiagramViewer** — custom moddle `ispf-moddle.json`

### Diagram without layout (DI)

BPMN from the engine or scripts often contains only process logic, **without a `bpmndi` section** (Diagram Interchange).  
In that case `bpmn-js` reports `no diagram to display`.

The Web Console automatically invokes **`bpmn-auto-layout`** (`src/bpmn/ensureDiagram.ts`) before rendering.  
The new-process template (`EMPTY_BPMN` in `constants.ts`) already includes minimal layout markup.

If the diagram does not display: open the **Source** tab, ensure `bpmnXml` is not empty, save — layout will be generated on the next open.

## Instance cancellation

```http
POST /api/v1/workflows/instances/{instanceId}/cancel
Content-Type: application/json

{
  "reason": "incident",
  "detailJson": "{\"incidentId\":\"...\"}",
  "cancelledBy": "operator-1"
}
```

The instance moves to `FAILED`; a record is written to `workflow_cancel_journal`.  
Roles: `operator`, `admin`.

## Signal delivery

```http
POST /api/v1/workflows/instances/{instanceId}/signal
Content-Type: application/json

{"signal":"incidentRegistered","operatorId":"operator-1"}
```

Broadcast to all `WAITING` workflow instances waiting for this signal:

```http
POST /api/v1/workflows/signal
Content-Type: application/json

{
  "workflowPath": "root.platform.workflows.signal-demo",
  "signal": "incidentRegistered",
  "operatorId": "operator-1"
}
```

## Timer firing

```http
POST /api/v1/workflows/instances/{instanceId}/timer
Content-Type: application/json

{"operatorId":"operator-1"}
```

Continues the instance when a deadline boundary timer or intermediate timer catch has fired.

## API

```http
GET  /api/v1/workflows/by-path?path=...
PUT  /api/v1/workflows/by-path/bpmn?path=...
PUT  /api/v1/workflows/by-path/status?path=...   body: { "status": "ACTIVE" }
POST /api/v1/workflows/by-path/run?path=...
POST /api/v1/workflows/instances/{instanceId}/cancel
POST /api/v1/workflows/instances/{instanceId}/signal
POST /api/v1/workflows/instances/{instanceId}/timer
POST /api/v1/workflows/signal
```

## Persistence

Tables: `workflow_instances`, `workflow_user_tasks` (Flyway V2).

## Tests

`WorkflowEngineTest`, `WorkflowEngineV2Test`, `WorkflowEngineV3Test`, `WorkflowEngineSignalTest`, `WorkflowEngineTimerTest`, `EscalationTemplateSmokeTest`, `BpmnParserTest`, `WorkflowApiTest`, `WorkflowSignalApiTest`, `WorkQueueApiTest`.
