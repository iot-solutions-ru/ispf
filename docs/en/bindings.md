> **Language:** Canonical English. Russian edition: [ru/bindings.md](../ru/bindings.md).

# Binding rules (mandatory rules)

A **binding rule** is a declarative rule for computing variable values on an object: **when** (activators) → **if** (condition, CEL) → **how** (expression) → **where** (target).

Rules are stored in system variable `@bindingRules` (JSON array, reserved). Runtime — **`BindingRuleEngine`** (unified binding engine since v0.8.0).

See also: [object-model](object-model.md), [blueprints](blueprints.md), ADR [0010-binding-rules-only](decisions/0010-binding-rules-only.md), ADR [0043-unified-platform-ref](decisions/0043-unified-platform-ref.md).

---

## PlatformRef (addresses)

All references to variables, functions, events, and historian tags use one slash grammar (same as REST `path` + `name` + `field`):

| Kind | Form | Example |
|------|------|---------|
| variable | `<object>/<name>[/<field>]` | `@/temperature`, `root.platform.devices.a/temperature/value` |
| function | `<object>/fn/<name>` | `@/fn/calculate` |
| event | `<object>/evt/<name>` | `@/evt/alarmRaised` |
| tag | `<object>/tag/<ruleId>` | `root.platform.devices.a/tag/avg-temp-5m` |

`@` = object where the rule runs.

| Operation | Example | Purpose |
|-----------|---------|---------|
| `read(ref)` | `read(root.platform.devices.a/temperature)` | Live variable field |
| `call(ref[, inputRef])` | `call(@/fn/ack, @/payload)` | Invoke function |
| `fire(ref)` | `fire(@/evt/overload)` | Publish event |
| Historian | `avg(root.../temperature, 5m)`, `live(@/temperature)` | Windowed aggregate / live sample |

**JSON configs** (activators, widgets, mimic, script steps): canonical field **`ref`** (slash string):

```json
{ "ref": "root.platform.devices.virt-cluster.dev-03/sineWave" }
```

---

## `BindingRule` model

```json
{
  "id": "member3-sine",
  "name": "Member3 sine",
  "enabled": true,
  "order": 20,
  "activators": {
    "onStartup": false,
    "onVariableChange": [
      {
        "ref": "root.platform.devices.virt-cluster.dev-03/sineWave"
      }
    ],
    "onEventRef": null,
    "periodicMs": 0
  },
  "condition": "",
  "expression": "read(root.platform.devices.virt-cluster.dev-03/sineWave)",
  "target": { "variableName": "member3Sine", "field": "value" }
}
```

| Field | Purpose |
|------|------------|
| `activators.onStartup` | Recalculate on server start / model attach |
| `activators.onVariableChange` | List of variable refs; `"ref": "@/*"` or legacy `"self"` + `"*"` = any local variable |
| `activators.onEventRef` | Full event ref, e.g. `@/evt/alarmRaised` or cross-object `root.../evt/overload` |
| `activators.periodicMs` | Periodic recalculation (0 = off); indexed in `platform_binding_periodic_rules`, wake by `next_run_at` |
| `condition` | CEL; empty = always |
| `expression` | CEL or single platform function |
| `target` | Where to write result (see **Target kinds** below) |
| `kind` | Optional: `reactive` (default) or `historian` — historian rules are evaluated by the analytics engine, not `BindingRuleEngine` ([0041-multi-tag-historian-computations](decisions/0041-multi-tag-historian-computations.md)) |
| `windowBucket` | Historian only: aggregate window (`5m`, `1h`, `8h`, …) |
| `rollupBuckets` | Historian only: optional materialized rollup windows |

### Historian rules (`kind: historian`)

Same `@bindingRules` array; multiple rules per device; arbitrary output variable names. Tag catalog path = `objectPath/tag/ruleId`.

**Recipes (rolling avg, OEE, tag chains, CEL):** [analytics-historian-cookbook](analytics-historian-cookbook.md)

```json
{
  "id": "shift-oee",
  "kind": "historian",
  "enabled": true,
  "order": 10,
  "activators": { "periodicMs": 300000, "onVariableChange": [] },
  "expression": "oee('root.platform.devices.line-01', 'availabilityPct', 'performancePct', 'qualityPct', '8h')",
  "windowBucket": "8h",
  "target": { "kind": "variable", "variableName": "oeePct", "field": "value" }
}
```

### Periodic execution

Rules with `periodicMs > 0` are parsed into JDBC index `platform_binding_periodic_rules` when `@bindingRules` is saved. **`BindingPeriodicScheduler`** wakes the JVM once at the nearest `next_run_at` and runs only due hits — no per-second full-tree scan. If no periodic rules exist, background wake is a no-op.

### Target kinds (platform rule)

Model extension — ADR [0019-platform-rule-unification](decisions/0019-platform-rule-unification.md). If `target.kind` is absent → **`variable`**.

| `kind` | Fields | Purpose |
|--------|------|------------|
| `variable` | `variableName`, `field` (or `ref` for cross-object write — future) | Write to object variable |
| `context` | `path` (dot-notation) | Write to `@dashboardContext` on `DASHBOARD` object |
| `event` | `eventName` or `ref` | Publish platform event; payload from `expression` |

Example dashboard rule (planned):

```json
{
  "id": "alarm-mode",
  "activators": { "onContextChange": true },
  "condition": "context.selection.device != \"\"",
  "expression": "\"alarm\"",
  "target": { "kind": "context", "path": "params.mode" }
}
```

Activator **`onContextChange`** — recalculate when `@dashboardContext` changes. Full spec: [platform-logic](platform-logic.md).

**Cross-object:** activator with remote `ref` + `read(remote/ref)` or `scale(remote/ref, …)` in expression. On remote variable change (including driver telemetry) `BindingPropagationListener` recalculates rules on consumer objects.

**Default activators** (if not set): remote refs in expression → automatic remote activators; otherwise local `self:*`.

---

## Expressions (`expression`)

| Kind | Example |
|-----|--------|
| **CEL** | `read(@/temperature) + 1.0` or CEL `self.temperature.value + 1.0` on current object |
| **Platform binding** | `counterRate(@/ifInOctets)`, `hysteresis(@/temperature, 80, 70)`, `read(root.../dev-03/sineWave)` |
| **Function / event** | `call(@/fn/dispatch, @/lastIngress)`, `fire(root.../pump/evt/overload)` |

Validation: `POST /api/v1/expressions/validate` or Web Console **Validate**.

Stateful bindings (`counterRate`, `hysteresis`, …) — state in `@bindingState`.

---

## REST API

```http
GET  /api/v1/objects/by-path/binding-rules?path={objectPath}
PUT  /api/v1/objects/by-path/binding-rules?path={objectPath}
DELETE /api/v1/objects/by-path/binding-rules/{ruleId}?path={objectPath}
```

Agent tool: `create_binding_rule` — use slash refs in expression and optional `ref` on activators.

---

## Models

In models — `ModelBindingRule` (full schema or `ModelBindingRule.of(id, target, expression)`). On apply/create, rules merge via `ModelBindingRulesMerger` **after** functions.

`defaultBinding` on model variable **removed** (v0.8.0).

---

## UI

Web Console → Object inspector → **Computations** tab (reactive + historian rules). Expression editor includes **PlatformRef picker** and function catalog. See [0040-unified-computations-ui](decisions/0040-unified-computations-ui.md).

---

## Not to be confused with

- **SQL bindings** (`ApplicationSqlBindingService`) — separate scheduler, not object binding rules
- **Alert rules**, correlators, workflows — separate subsystems
