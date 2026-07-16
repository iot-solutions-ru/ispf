> **Language:** Canonical English. Russian edition: [ru/platform-logic.md](../ru/platform-logic.md).

# Unified platform logic (Platform Rule)

> **Status:** Beta — Rules; `@dashboardContext` readiness varies. Hub: [doc-status.md](doc-status.md).

One mechanism for variable bindings, dashboard logic, and event side effects.

**Status:** specification (ADR [0019-platform-rule-unification](decisions/0019-platform-rule-unification.md), **Proposed**). Runtime extensions — phases 1–3.

---

## Human model

```
WHEN  →  IF (CEL)  →  THEN (write result)
```

Same pattern as [binding rules](bindings.md):

- **Binding rule** on device → writes to a variable.
- **Dashboard rule** on `DASHBOARD` object → writes to `@dashboardContext` or fires event.
- **Alert rule** on `ALERT` → separate object, but condition = CEL (future UI unification — not in scope v1).

---

## JSON: Platform Rule (= BindingRule)

### Effect `variable` (today, default)

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

Legacy without `kind`:

```json
"target": { "variableName": "avgTemp", "field": "value" }
```

### Effect `context` (dashboard UI state)

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
  "condition": "context.selection.device != \"\" && read(context.selection.device + \"/temperature\") > 80",
  "expression": "\"alarm\"",
  "target": {
    "kind": "context",
    "path": "params.mode"
  }
}
```

`context.*` in CEL — snapshot of `@dashboardContext` at eval time (phase 1).

### Effect `event` (journal / workflow)

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

## Activators

| Field | Status | Purpose |
|-------|--------|---------|
| `onStartup` | exists | start / model attach |
| `onVariableChange` | exists | telemetry, variables |
| `onEvent` | exists | platform event name |
| `periodicMs` | exists | periodic recompute; index `platform_binding_periodic_rules`, wake by `next_run_at` ([bindings](bindings.md)) |
| `onContextChange` | **planned** | `@dashboardContext` change |

---

## `@dashboardContext` (planned, phase 1)

Reserved JSON variable on `DASHBOARD` object (model `dashboard-v1`).

| Field | Type | Purpose |
|-------|------|---------|
| `selection` | `Record<string, string>` | selection slots (`device`, `order`, …) |
| `params` | `Record<string, unknown>` | scalars, modes, report parameters |
| `widgets` | `Record<string, { visible?: boolean }>` | runtime visibility by widget id |
| `updatedAt` | ISO string | audit |
| `updatedBy` | string | username |

Web-console `DashboardSession` mirrors this structure.

### Context publishers

| Source | Action |
|--------|--------|
| object-table / card-grid / map | `selection.*` + `params.*` from row |
| report widget | `rowParamsFromRowJson` → `params` |
| function-form | `syncFieldsToSessionJson` → `params` |
| dashboard-link | `contextSelectionJson` / `contextParamsJson` |
| operator URL `?ctx=` | initial snapshot |

All publishers (phase 1+) → **PUT `@dashboardContext`**, not only React state.

### Consumers

| Widget field | Reads from context |
|--------------|-------------------|
| `selectionKey` | `selection[key]` |
| `paramKey` | `params[key]` |
| `contextPathKey` | `params[key]` as object path |
| visibility | `widgets[widgetId].visible` via rules + runtime merge |

---

## Legacy inventory → Platform Rule

| Legacy | Where | Migration | Status |
|--------|-------|-----------|--------|
| `showWhenJson` | function-form field | CEL rule on form object or dashboard rule on `context.params.*` | runtime kept; UI migration — phase 3 |
| `payloadFilterExpr` | event-feed | CEL `condition` on rule / server-side filter | runtime kept; deprecation hint in editor |
| `requireSessionParamsJson` | link, form | `condition` on non-empty keys in context | runtime kept; deprecation hint in editor |
| Dashboard session only (sessionStorage) | operator | `@dashboardContext` + WS | phase 1 ✅ |
| Per-widget `visible` boolean | layout | static default; runtime → rules → `widgets.*.visible` | phase 2 ✅ |
| Spreadsheet session blob | `params[sheet:…]` | binding cells + `onContextChange`; optional export rules | planned |
| Alert CEL | ALERT object | remains; same CEL editor | — |

**Do not add:** `behaviorJson`, `visibleWhen` on widget, separate dashboard DSL.

---

## UI

| Location | Component |
|----------|-----------|
| Object Inspector | `BindingRulesPanel` |
| Dashboard Builder | `DashboardRulesPanel` + templates (phase 2) |
| AI agent | `create_binding_rule` + dashboard templates |

---

## REST / engine (planned)

- Existing CRUD `/binding-rules` — URL unchanged.
- `BindingRuleEngine.evaluateOnContextChange(dashboardPath, contextJson)` — phase 1.
- CEL validate: `POST /api/v1/expressions/validate` (already exists).

---

## Example: SNMP monitoring

**Layout:** object-table `selectionKey: device` + value/chart with same key.

**Rules on `root.platform.dashboards.snmp-host-monitoring`:**

1. When temperature on selected device > 80 → `params.mode = "alarm"`.
2. When `params.mode == "alarm"` → `widgets.alarm-panel.visible = true`.
3. When `params.mode == "normal"` → `widgets.alarm-panel.visible = false`.
4. When mode → alarm → fire `hmi.dashboard.alarm`.

Widgets **without** conditional fields in JSON layout.

---

## Related documents

- [bindings](bindings.md) — binding rules, CEL, API
- [expression-language](expression-language.md) — full function / literal / example reference
- [dashboards](dashboards.md) — layout, widgets, context
- [decisions/0019-platform-rule-unification.md](decisions/0019-platform-rule-unification.md)
