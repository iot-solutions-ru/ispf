> **Language:** Canonical English. Russian edition: [ru/workflows.md](../ru/workflows.md).

# Workflow & BPMN

> **Status:** Beta — BPMN subset (not full 2.0). Hub: [doc-status.md](doc-status.md).

## Overview

A workflow is an object of type `WORKFLOW` with model `workflow-v1`. The engine is pure Java in `ispf-plugin-workflow` (no Camunda/Flowable).

Workflow object variables:

| Variable | Description |
|----------|-------------|
| `title` | Display name |
| `status` | `DRAFT` / `ACTIVE` / `STOPPED` |
| `bpmnXml` | BPMN 2.0 XML |
| `triggerJson` | Variable-change or event trigger |
| `operatorAppId` | Operator App for user tasks in the sidebar |
| `instanceState` | JSON state of the last instance |
| `lastRunAt` | Time of the last run |
| `lastAction` | Last service task action |
| `inputSchemaJson` | Tool input schema (ADR-0049) |
| `outputSchemaJson` | Tool output projection schema |
| `toolDescription` | Agent/MCP tool description |
| `sideEffectClass` | `READ` / `WRITE` / `CONTROL` |
| `retryMaxAttempts` | Max retries after FAILED (metadata for DLQ) |
| `retryBackoffSeconds` | Backoff hint |
| `errorWorkflowPath` | Workflow started on failure |
| `webhookSlug` | Inbound webhook slug |
| `cronExpression` | e.g. `every:1m` |

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

ISPF implements a **BPMN subset**, not full BPMN 2.0. Engine: pure Java in `ispf-plugin-workflow`. Decision record: [0047-custom-bpmn-subset-engine](decisions/0047-custom-bpmn-subset-engine.md) (**Accepted** 2026-07-18 — subset freeze).

| Element | Support |
|---------|---------|
| `startEvent` | Yes |
| `endEvent` | Yes |
| `serviceTask` | LOG, SET_VARIABLE, PUBLISH_NATS, **INVOKE_FUNCTION** |
| `userTask` | Operator queue, claim/complete |
| `intermediateCatchEvent` | Signal wait (`ispf:signal`), timer (`ispf:durationSeconds`), or message (`messageEventDefinition` / `ispf:message`) |
| `boundaryEvent` | Timer on user task (`attachedToRef`, `ispf:durationSeconds`, `cancelActivity`) — escalation path |
| `messageTask` | NATS publish (if enabled) |
| `exclusiveGateway` | Conditional transitions (CEL) |
| `parallelGateway` | Fork/join, execution tokens |
| `subProcess` | Embedded subprocess — enter inner start, exit on inner end (nested embedded OK; not event subprocess) |
| `sequenceFlow` | `ispf:condition`, `ispf:default` |
| Message catch / throw | Yes — catch waits until `deliverMessage`; throw is `intermediateThrowEvent` + `messageEventDefinition` (non-message throw rejected at parse) |

Extension namespace: `http://ispf.io/bpmn` (prefix `ispf:`).

## Not supported (explicit non-goals)

These are **out of the ISPF subset**. The parser **rejects** them with a clear error (ADR-0047 freeze); do not rely on silent ignore. WorkflowBuilder palette must not imply support.

| Element / feature | Status |
|-------------------|--------|
| `callActivity` | Not supported |
| Multi-instance (`multiInstanceLoopCharacteristics`) | Not supported |
| `inclusiveGateway` | Not supported |
| `eventBasedGateway` | Not supported |
| Compensation / compensate events | Not supported |
| Event subprocess | Not supported |
| DMN / business rule task | Not supported |
| Full BPMN 2.0 import from Camunda/Flowable models | Not a goal — design against this page |

Generic BPMN pasted from other tools will often fail or behave incorrectly until it is reduced to the supported table.

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
| `fire_event` | `ispf:objectPath`, `ispf:eventName`, `ispf:payloadVariable` |
| `read_variable` | `ispf:objectPath`, `ispf:variable` / `sourceVariable`, `ispf:contextKey` |
| `start_workflow` | `ispf:workflowPath`, `ispf:objectPath` |
| `llm_complete` | `ispf:promptTemplate`, `ispf:outputVariable`, `ispf:outputFormat`, `ispf:modelRef`, `ispf:timeoutMs` |
| `invoke_agent` | `ispf:goalTemplate`, `ispf:agentMode`, `ispf:toolAllowlist`, `ispf:maxSteps`, `ispf:outputVariable` |

`modelRef`: `platform-default` (platform AI settings), a model id, or a credentials vault path (`root...`) whose secret is the API key and optional metadata `baseUrl` / `model`.

Tool contracts (ADR-0049): WORKFLOW variables `inputSchemaJson`, `outputSchemaJson`, `toolDescription`, `sideEffectClass`. Agent tool `invoke_workflow_tool` requires `ACTIVE`. ACTIVE workflows with non-blank `toolDescription` are also published on MCP as `wf_<name>` tools. Execution journal: `GET /api/v1/workflows/instances/{id}/steps`, `GET /api/v1/workflows/by-path/runs`. Webhook: `POST /api/v1/webhooks/workflows/{slug}` when `webhookSlug` is set. Cron: `cronExpression=every:1m`. AI palette entries in the BPMN editor create `llm_complete` / `invoke_agent` service tasks with defaults.

Example `invoke_function` (application functions — [applications](applications.md)):

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

### intermediateCatchEvent (message)

```xml
<intermediateCatchEvent id="waitAck" name="Wait ERP ack">
  <messageEventDefinition messageRef="erpAck"/>
</intermediateCatchEvent>
```

Or `ispf:message="erpAck"` on the catch event. The instance waits until `deliverMessage` supplies the matching name.

### intermediateThrowEvent (message)

```xml
<intermediateThrowEvent id="shipMsg" name="Ship order">
  <messageEventDefinition messageRef="orderShipped"/>
</intermediateThrowEvent>
```

Publishes via the message executor (`channel=bpmn-throw`, subject = message name). Non-message throw definitions are rejected at parse.

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

While the user task is in `WAITING`, an SLA timer runs in parallel. When the timer fires, the escalation branch runs (interrupting). Template: [reference-escalation-templates](reference-escalation-templates.md).

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

Correlator with action `RUN_WORKFLOW` and `actionTarget` = workflow path. See [automation](automation.md).

## Demo workflow

`root.platform.workflows.demo-alarm-handler` — BPMN with gateway, user task, service log.

Definition: `WorkflowDefinitions.DEMO_ALARM_HANDLER`.

## UI

![BPMN workflow editor — MES work-order dispatch](../assets/ispf-bpmn-workflow.png)

- **WorkflowBuilder** — status, run, BPMN editor (bpmn-js); product status **Beta — BPMN subset** (see tables above)
- **BpmnDiagramEditor** / **BpmnDiagramViewer** — custom moddle `ispf-moddle.json`; palette filtered to the ISPF subset (`ispfPaletteFilter.ts` — no pool/participant, data objects/stores, generic `task`, or group). Hard gate remains parser reject (ADR-0047).

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

## Message delivery

```http
POST /api/v1/workflows/instances/{instanceId}/message
Content-Type: application/json

{"message":"erpAck","operatorId":"operator-1"}
```

Continues a `WAITING` instance that is at a message catch for the matching name. Wrong name → clear error.

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
POST /api/v1/workflows/by-path/run?path=...     body: { "input": { ... } }
POST /api/v1/workflows/by-path/invoke-tool?path=...
GET  /api/v1/workflows/by-path/runs?path=...
GET  /api/v1/workflows/instances/{instanceId}/steps
POST /api/v1/workflows/instances/{instanceId}/cancel
POST /api/v1/workflows/instances/{instanceId}/signal
POST /api/v1/workflows/instances/{instanceId}/message
POST /api/v1/workflows/instances/{instanceId}/timer
POST /api/v1/workflows/signal
POST /api/v1/webhooks/workflows/{slug}
GET  /api/v1/workflows/by-path/dead-letters?path=...&unresolvedOnly=true
POST /api/v1/workflows/dead-letters/{id}/resolve
```

## Persistence

Tables: `workflow_instances`, `workflow_user_tasks`, `workflow_execution_steps`, `workflow_dead_letters` (ADR-0049 / Flyway V81).

Related: [0047-custom-bpmn-subset-engine](decisions/0047-custom-bpmn-subset-engine.md), [0049-ot-automation-excellence](decisions/0049-ot-automation-excellence.md), hands-on [OT Automation tutorials](ot-automation-excellence-tutorials.md).

## Tests

`WorkflowEngineTest`, `WorkflowEngineV2Test`, `WorkflowEngineV3Test`, `WorkflowEngineSignalTest`, `WorkflowEngineTimerTest`, `WorkflowEngineSubProcessTest`, `WorkflowEngineMessageTest`, `EscalationTemplateSmokeTest`, `BpmnParserTest` (includes unsupported-element reject list), `WorkflowApiTest`, `WorkflowSignalApiTest`, `WorkQueueApiTest`, `WorkflowWebhookApiTest`, `WorkflowDeadLetterApiTest`, `WorkflowDeadLetterServiceTest`.
