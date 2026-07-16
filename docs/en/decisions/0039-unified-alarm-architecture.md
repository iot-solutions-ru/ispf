# ADR-0039: Alert rule evolution

## Status

**Proposed** (2026-07-09)

## Context

ISPF alarm behavior is spread across **alert rules** (`ALERT` / `alert-rule-v1`), **correlators**, **bindings**, **workflows**, **alarm shelf**, and partial **process programs** (BL-172). Mature SCADA stacks combine triggers, active/pending instances, escalation, and notifications in one definition; ISPF covers parts of that across several mechanisms.

We want that **semantics** without a parallel object model. ISPF stores automation as **tree nodes + intrinsic blueprint variables** ([0010-binding-rules-only](0010-binding-rules-only.md), [automation](../automation.md)). A new `ObjectType` or `@alarmDefinition` JSON would fork operators, bundles, MCP `configure_alert`, and `AutomationTreeService`.

**Process programs (BL-172)** remain cyclic **control** — not part of alert-rule evolution.

## Decision

**Evolve the existing alert rule end-to-end**: same `ObjectType.ALERT`, same intrinsic blueprint **`alert-rule-v1`**, same paths under `root.platform.alert-rules`, same REST `/api/v1/alert-rules`. New behavior arrives as **optional blueprint fields** + **`AlertRule` / `AlertRuleService` / `AlertRuleListener`** extensions. Empty or absent new fields preserve today's behavior.

No new blueprint name. No reserved JSON blob replacing the rule. Correlators and BPMN stay separate.

### 1. Principle: one mechanism, additive fields

| Layer | Evolution |
|-------|-----------|
| Blueprint | Add optional variables to `buildAlertRuleModel()` in `BlueprintBootstrap` |
| Runtime record | Extend `AlertRule` + `AutomationTreeService.toAlertRule()` |
| API | Extend `CreateAlertRuleRequest` / `UpdateAlertRuleRequest` (all new fields optional) |
| Engine | Extend `AlertRuleService.processVariableChange()` + new schedulers/listeners as needed |
| Index | Extend `AutomationRuleIndex` for mask / event triggers when added |
| UI | Extend `AlertRuleInspector` (Explorer) |
| Bundles | Existing `alertRules[]` in manifests gain optional keys |

Existing fields unchanged:

| Field | Role today |
|-------|------------|
| `targetObjectPath` | Watched object |
| `watchVariable` | Activator variable |
| `conditionExpr` | CEL — **activate** condition (keep name for compatibility) |
| `eventName` | Fire when activate condition met |
| `payloadVariable`, `enabled`, `edgeTrigger` | As today |
| `delaySeconds`, `sustainWhileTrue`, `rateLimitSeconds` | Activate delay / sustain |
| `priority`, `ackRequired` | Alarm metadata |
| `notificationWebhookUrl`, `notificationEmailTarget` | Notifications |
| `anomalyModelId` | Optional SPI path |
| `lastConditionMet`, `conditionTrueSince`, `lastFiredAt` | Runtime |

### 2. Phase B — variable latch

Add to **`alert-rule-v1`**:

| New field | Type | Default |
|-----------|------|---------|
| `deactivateExpr` | string (CEL) | empty → clear when `conditionExpr` false |
| `deactivateDelaySeconds` | int | 0 |
| `pollIntervalMs` | int | 0 → evaluate only on `watchVariable` change |
| `triggerMessage` | string (CEL) | empty → use rule description |

Engine:

- Track **latched active** per `(rulePath, sourceObjectPath[, recordKey])` in `AlertRuleRuntimeStore` (extend, do not replace).
- On activate path: existing fire logic + optional `triggerMessage` in event enrichment.
- On deactivate path: when `deactivateExpr` true (or `conditionExpr` false) for `deactivateDelaySeconds`, fire optional **`clearEventName`** (new field, phase B.1) or set runtime `active=false` without new event if `clearEventName` empty.
- `pollIntervalMs > 0`: register rule in `platform_alert_periodic_rules` index (same pattern as binding periodic scheduler).

### 3. Phase C — instances + operator ack

Add runtime table **`platform_alert_instances`** keyed by **`alertRulePath`** (not a new model):

- `alertRulePath`, `sourceObjectPath`, `recordKey`, `state`, `raisedAt`, `clearedAt`, `ackedAt`, `ackedBy`, `message`, `priority`
- States: `ACTIVE`, `PENDING_ACK`, `CLEARED`, `SHELVED` (shelf integrates `AlarmShelfService`)

Optional blueprint fields:

| Field | Purpose |
|-------|---------|
| `clearEventName` | Event on deactivate (e.g. `thresholdCleared`) |
| `instanceMessage` | Static template; overridden by evaluated `triggerMessage` |

Standard events on **source object**: prefer existing names (`thresholdExceeded`) + new optional clears; add `alarmAcknowledged` only if `ackRequired` and no device function.

Runtime read-only variables on **the alert rule node**: `activeInstanceCount`, `pendingInstanceCount`, `escalated`.

### 4. Phase D — event-driven alert rules (same blueprint)

Add:

| Field | Type | When |
|-------|------|------|
| `triggerKind` | `VARIABLE` \| `EVENT` | default `VARIABLE` |
| `watchEventName` | string | required if `EVENT` |
| `eventFilterExpr` | CEL on event payload | optional filter |
| `deactivateEventName` | string | correlated clear event |
| `deactivateEventFilterExpr` | CEL | clear filter |
| `eventCountThreshold` | int | 0 = every match |
| `eventWindowSeconds` | int | with threshold |

`AlertRuleListener` today handles `VARIABLE_UPDATED`. Add **`AlertRuleEventListener`** on `EVENT_FIRED` indexed by `(targetObjectPath, watchEventName)` — same `AlertRule` node, no correlator duplication for simple activate/deactivate pairs.

Complex CEP (`WINDOW`, MES chains) stays in **`CORRELATOR`** nodes.

### 5. Phase E — multi-source and advanced variable rules

| Field | Purpose |
|-------|---------|
| `sourceObjectMask` | Glob on tree paths; index expands to matching devices at save time + on tree changes |
| `recordKeyExpr` | CEL per table row → multiple instances per source |
| `flappingEnabled` | bool |
| `flappingActivatePercent` / `flappingDeactivatePercent` | float thresholds |
| `alarmGroupId` | Optional string — groups sibling rules as one logical alarm in operator UI (OR without multi-trigger JSON) |

**Multi-trigger OR without a new model:** several `ALERT` nodes sharing `alarmGroupId` (e.g. `temperature-high`) — each row is one trigger, operator UI can collapse the group.

### 6. Phase F — escalation on the rule (optional fields)

Inline escalation, still on **`alert-rule-v1`**:

| Field | Purpose |
|-------|---------|
| `escalationEnabled` | bool |
| `escalationPendingRequired` | bool |
| `escalationCountThreshold` | int |
| `escalationTimeSeconds` | long |
| `escalationWebhookUrl` | string |
| `escalationWorkflowPath` | string |

Heavy SLA flows remain **workflows** ([reference-escalation-templates](../reference-escalation-templates.md)).

Corrective actions: optional `onRaiseWorkflowPath`, `onClearWorkflowPath` — not a separate action table.

### 7. Example: temperature > 80 (evolved alert rule)

Same node type as today — extended `POST /api/v1/alert-rules` body:

```json
{
  "name": "temperature-threshold-exceeded",
  "objectPath": "root.platform.devices.demo-sensor-01",
  "watchVariable": "temperature",
  "conditionExpr": "self.temperature[\"value\"] > 80.0",
  "deactivateExpr": "self.temperature[\"value\"] < 70.0",
  "deactivateDelaySeconds": 60,
  "delaySeconds": 0,
  "sustainWhileTrue": false,
  "eventName": "thresholdExceeded",
  "clearEventName": "thresholdCleared",
  "triggerMessage": "Temperature exceeded 80 C",
  "payloadVariable": "temperature",
  "enabled": true,
  "edgeTrigger": true,
  "ackRequired": true,
  "priority": "HIGH",
  "pollIntervalMs": 0
}
```

See [examples/alert-rule-evolution/](../../../examples/alert-rule-evolution/).

### 8. Implementation order

| Phase | Scope |
|-------|--------|
| **A** | ADR + docs; no runtime change |
| **B** | `deactivateExpr`, `deactivateDelaySeconds`, `pollIntervalMs`, `triggerMessage`, `clearEventName` |
| **C** | `platform_alert_instances`, operator alarm bar, ack API |
| **D** | `triggerKind=EVENT` + event listener index |
| **E** | `sourceObjectMask`, `recordKeyExpr`, flapping, `alarmGroupId` |
| **F** | Escalation + workflow hooks on rule fields |

Each phase ships backward-compatible: old nodes missing new variables behave exactly as now.

### 9. Explicit non-goals

- New blueprint (`alarm-v2`) or `@alarmDefinition` JSON variable.
- Merging **correlator** CEP into alert rules.
- **Process programs** as alarm pollers.
- Third-party expression DSL or dynamic baselining v1.

## Consequences

- One object type, one inspector, one API, one bundle key (`alertRules`).
- Bundles and MCP tools keep working; new fields are optional.
- Latch, hysteresis, and instances without a parallel alarm object model.

Risks:

- Many fields on one blueprint — mitigated by phased UI sections (Activate / Deactivate / Event / Escalation).
- `sourceObjectMask` indexing cost on large trees.
- `alarmGroupId` OR-groups are a convention until operator UI groups them.

## Related

- [automation](../automation.md)
- [0014-automation-pipeline-evolution](0014-automation-pipeline-evolution.md) — indexes and lanes
- [reference-escalation-templates](../reference-escalation-templates.md)
