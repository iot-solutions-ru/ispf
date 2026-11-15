> **Language:** Canonical English. Russian edition: [ru/platform-logic.md](../ru/platform-logic.md).

# Unified Platform Rules (`WHEN → IF → THEN` & `@dashboardContext`)

> **Status:** Beta — unified rule model across object variables, dashboard session state (`@dashboardContext`), and platform events ([ADR-0019](decisions/0019-platform-rule-unification.md)). Status tags: [doc-status.md](doc-status.md).

In ISPF, variable recalculations, dynamic dashboard behavior, and event triggers share a single declarative rule structure:

```text
WHEN activator fires  →  IF condition holds (CEL)  →  THEN write result to target
```

---

## 1. Three Rule Target Kinds (`target.kind`)

### 1. Write to an Object Variable (`kind: "variable"`, default)

```json
{
  "id": "avg-temp",
  "enabled": true,
  "order": 10,
  "activators": {
    "onVariableChange": [{ "ref": "@/temperature" }]
  },
  "condition": "",
  "expression": "self.temperature.value",
  "target": {
    "kind": "variable",
    "variableName": "avgTemp",
    "field": "value"
  }
}
```

### 2. Update Dashboard UI Context (`kind: "context"`)

Defined on a `DASHBOARD` node to update `@dashboardContext` parameters or toggle widget visibility dynamically:

```json
{
  "id": "alarm-mode",
  "enabled": true,
  "order": 20,
  "activators": {
    "onVariableChange": [
      { "objectPath": "self", "variableName": "@dashboardContext" }
    ],
    "onContextChange": true
  },
  "condition": "context.selection.device != \"\" && read(context.selection.device + \"/temperature\") > 80.0",
  "expression": "\"alarm\"",
  "target": {
    "kind": "context",
    "path": "params.mode"
  }
}
```

### 3. Publish a Platform Event (`kind: "event"`)

```json
{
  "id": "fire-alarm",
  "enabled": true,
  "order": 30,
  "activators": { "onContextChange": true },
  "condition": "context.params.mode == \"alarm\"",
  "expression": "{\"device\": context.selection.device, \"mode\": \"alarm\"}",
  "target": {
    "kind": "event",
    "eventName": "hmi.dashboard.alarm"
  }
}
```

---

## 2. Rule Activators (`activators`)

| Activator Field | When It Fires |
|-----------------|---------------|
| `onStartup` | Once at server startup or when a blueprint is attached |
| `onVariableChange` | Whenever any referenced variable changes |
| `onEvent` / `onEventRef` | Whenever the specified platform event fires |
| `periodicMs` | On a recurring timer in milliseconds ([Binding rules](bindings.md)) |
| `onContextChange` | Whenever `@dashboardContext` changes on a dashboard |

---

## 3. Dashboard Session Context (`@dashboardContext`)

Every `DASHBOARD` object (`dashboard-v1`) includes the reserved JSON variable `@dashboardContext`:

| Field | Type | Purpose |
|-------|------|---------|
| `selection` | `Record<string, string>` | Active object selection slots (`device`, `order`, `line`, …) |
| `params` | `Record<string, unknown>` | Filter parameters, screen modes, and report arguments |
| `widgets` | `Record<string, { visible?: boolean }>` | Dynamic runtime visibility per widget ID |
| `updatedAt`, `updatedBy` | `string` | Timestamp and username of the last context update |

- **Context publishers:** `object-table`, `card-grid`, `map` (row click sets `selection.*` and `params.*`), `report` widget (`rowParamsFromRowJson`), `function-form` (`syncFieldsToSessionJson`), `dashboard-link` (`contextSelectionJson` / `contextParamsJson`), and URL query parameter `?ctx=`.
- **Context consumers:** widgets bound via `selectionKey`, `paramKey`, `contextPathKey`, and dynamic visibility rules (`widgets.<widgetId>.visible`).

---

## 4. Example: Context-Driven Alarm Panel on an Operator Dashboard

On dashboard `root.platform.dashboards.snmp-host-monitoring`, an `object-table` publishes the selected device path to `selectionKey: "device"`. Four rules on the dashboard object drive the screen without embedding logic inside individual widgets:

1. When the selected device's temperature exceeds `80.0` → set `params.mode = "alarm"`.
2. When `context.params.mode == "alarm"` → set `widgets.alarm-panel.visible = true`.
3. When `context.params.mode == "normal"` → set `widgets.alarm-panel.visible = false`.
4. When transitioning to `"alarm"` → fire event `hmi.dashboard.alarm`.

**Related guides:** [Binding rules (`bindings.md`)](bindings.md) · [Expression language (`expression-language.md`)](expression-language.md) · [Dashboards (`dashboards.md`)](dashboards.md) · [ADR-0019](decisions/0019-platform-rule-unification.md).
