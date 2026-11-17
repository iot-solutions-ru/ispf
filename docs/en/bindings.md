> **Language:** Canonical English. Russian edition: [ru/bindings.md](../ru/bindings.md).

# Binding Rules (Variable Calculation Rules)

> **Status:** Stable — reactive and historical variable calculations in `@bindingRules`, `PlatformRef` addressing, and loop protection. Status tags: [doc-status.md](doc-status.md).

A **binding rule (`BindingRule`)** is a declarative formula attached to an object that follows the pattern: **when triggered** (`activators`) → **if condition holds** (`condition` in CEL) → **compute expression** (`expression`) → **write result to target** (`target`).

Rules are stored in the object's system variable `@bindingRules` (JSON array) and executed by **`BindingRuleEngine`** (for reactive rules) or **`AnalyticsEngineService`** (for `kind: "historian"` rules).

**Full syntax and function catalog:** **[Expression language reference (`expression-language.md`)](expression-language.md)**.  
**Related guides:** [Object model](object-model.md) · [Blueprints](blueprints.md) · [Common design mistakes (`anti-patterns.md`)](anti-patterns.md) · [ADR-0010](decisions/0010-binding-rules-only.md) · [ADR-0043](decisions/0043-unified-platform-ref.md).

**Demo:** [`examples/analytics-rolling-avg`](../../examples/analytics-rolling-avg/) — historian binding with `avg(...)` on `demo-sensor-01`.

---

<a id="platformref-addresses"></a>

## 1. Unified Entity Addressing (`PlatformRef`)

All references to variables, functions, events, and historian tags use a single slash-separated format (`path/name/field`):

| Entity Kind | Format | Example |
|-------------|--------|---------|
| **Variable** | `<objectPath>/<variableName>[/<field>]` | `@/temperature`, `root.platform.devices.pump-01/temperature/value` |
| **Function** | `<objectPath>/fn/<functionName>` | `@/fn/calculate` |
| **Event** | `<objectPath>/evt/<eventName>` | `@/evt/alarmRaised` |
| **Historian tag** | `<objectPath>/tag/<ruleId>` | `root.platform.devices.sensor-a/tag/avg-temp-5m` |

The shorthand `@` refers to the object on which the rule is defined.

| Built-in Operation | Example | Purpose |
|--------------------|---------|---------|
| `read("<ref>")` | `read("root.platform.devices.a/temperature")` | Read a live variable from another object (always quote the slash reference) |
| `write(<ref>, <val>)` | `write(@/setpoint, 42)` | Write a variable field |
| `call(<fnRef>[, <inRef>])` | `call(@/fn/ack, @/payload)` | Invoke an object function |
| `fire(<evtRef>)` | `fire(@/evt/overload)` | Publish an event on an object |
| `avg(<ref>, <window>)` | `avg(root.../temperature, 5m)` | Windowed historian aggregate (`kind: "historian"`) |
| `queryScalar(<spec>, "<agg>")` | `queryScalar(@/downIfSpec, "count")` | Scalar aggregate from an Object Query specification ([ADR-0044](decisions/0044-object-query.md)) |

---

## 2. Structure of a `BindingRule`

### Example: Reactive Cross-Object Rule on a Logic Hub

```json
{
  "id": "member3-sine",
  "name": "Mirror sensor 3 sine wave",
  "enabled": true,
  "order": 20,
  "activators": {
    "onStartup": false,
    "onVariableChange": [
      { "ref": "root.platform.devices.virt-cluster.dev-03/sineWave" }
    ],
    "onEventRef": null,
    "periodicMs": 0
  },
  "condition": "",
  "expression": "read(\"root.platform.devices.virt-cluster.dev-03/sineWave\")",
  "target": { "variableName": "member3Sine", "field": "value" }
}
```

### Rule Fields

| Field | Description |
|-------|-------------|
| `id`, `name`, `enabled`, `order` | Rule identifier, label, active flag, and ascending execution order on the object |
| `kind` | `"reactive"` (default, immediate evaluation) or `"historian"` (windowed time-series calculation — see [Analytics historian cookbook](analytics-historian-cookbook.md)) |
| `activators.onStartup` | Evaluate once when the server starts or a blueprint is attached |
| `activators.onVariableChange` | Array of variable references (`{ "ref": "@/temperature" }` or `{ "ref": "@/*" }` for any local variable) |
| `activators.onEventRef` | Trigger when a specific event fires (`@/evt/alarmRaised`) |
| `activators.periodicMs` | Periodic timer in ms (`0` = disabled); indexed in `platform_binding_periodic_rules` and woken at `next_run_at` |
| `condition` | Optional boolean Google CEL guard (empty = always run when activated) |
| `expression` | Google CEL formula or a single built-in signal function (`movingAvg`, `counterRate`, `hysteresis`, `read`, …) |
| `target` | Output destination (`variable`, `action`, `context`, or `event`) |
| `windowBucket`, `rollupBuckets` | Used by `kind: "historian"` rules (`5m`, `1h`, `8h`, …) |

---

<a id="execution"></a>

## 3. Execution Guarantees and Loop Prevention

To prevent race conditions and infinite recalculation loops when multiple rules depend on one another, `BindingRuleEngine` enforces strict execution rules:

| Mechanism | Guarantee |
|-----------|-----------|
| **Deterministic order** | Enabled rules on an object execute in ascending `order` |
| **Multi-pass convergence (`MAX_PASSES = 8`)** | If a pass updates local variables, the engine re-evaluates dependent rules on the same object until values stabilize or `8` passes are reached |
| **Cross-object depth / stop-loss (`MAX_DEPTH = 16`)** | Chains of cross-object activations stop at depth `16`; truncation throws, logs an error, and increments Micrometer `ispf.binding.truncations` (System → Metrics `bindingTruncationsTotal`) |
| **Asynchronous side effects** | Setting `activators.async = true` runs independent side effects off the synchronous propagation path |
| **Save-time cycle detection** | `BindingCascadeAnalyzer` warns on save when two rules form a write↔activate cycle or contain overly long expressions |
| **Exponential backoff on periodic errors** | If a periodic rule (`periodicMs > 0`) throws an exception, it stays enabled and backs off up to `max(periodicMs, 5 min)` (`ispf.binding.periodic.max-backoff-ms`) until the next successful run |

> **Best practice:** keep a **single writer rule per variable**, and place multi-step branching in a function on a `SINGLETON` or `INSTANCE` logic object (`call(@/fn/...)`).

---

## 4. Target Kinds (`target.kind`)

| `target.kind` | Required Fields | Behavior |
|---------------|-----------------|----------|
| `variable` *(default)* | `variableName` + `field` (or cross-object `ref`) | Writes the computed result to a variable on the current object or a remote object |
| `action` | — | Evaluates `expression` for its side effects (`call(...)`, `write(...)`, `fire(...)`) without writing a target variable |
| `context` | `path` | Writes to `@dashboardContext` on a `DASHBOARD` object ([Platform logic](platform-logic.md)) |
| `event` | `eventName` (or `ref`) | Publishes a platform event with the evaluated payload |

---

## 5. REST API and Web Console

In the Web Console, select an object → **Computations** tab to edit reactive and historian rules with the built-in **PlatformRef picker** and **Validate** button.

```http
GET    /api/v1/objects/by-path/binding-rules?path={objectPath}
PUT    /api/v1/objects/by-path/binding-rules?path={objectPath}
DELETE /api/v1/objects/by-path/binding-rules/{ruleId}?path={objectPath}
```

> **Difference from SQL bindings:** `@bindingRules` compute values from object tree variables and historian windows. Periodic SQL queries against application database tables (`sqlBinding('appId', 'var')`) are configured separately via `/api/v1/applications/{appId}/bindings` or blueprint `sqlBindings` — see [Applications](applications.md) and [Blueprints](blueprints.md).
