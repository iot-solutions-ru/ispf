> **Язык:** русская версия (вычитка). Канонический английский: [en/platform-logic.md](../en/platform-logic.md).

# Единая логика платформы (Platform Rule)

> **Статус:** Beta — Правила; @dashboardContext — зрелость разная. Теги: [doc-status](../en/doc-status.md).

Один механизм для variable bindings, логики дашборда и event side effects.

**Статус:** спецификация (ADR [0019-platform-rule-unification](decisions/0019-platform-rule-unification.md), **Proposed**). Runtime-расширения — фазы 1–3.

---

## Модель для человека

```
WHEN  →  IF (CEL)  →  THEN (write result)
```

Тот же паттерн, что у [binding rules](bindings.md):

- **Binding rule** на устройстве → пишет в переменную.
- **Dashboard rule** на объекте `DASHBOARD` → пишет в `@dashboardContext` или публикует event.
- **Alert rule** на `ALERT` → отдельный объект, но условие = CEL (будущая унификация UI — вне scope v1).

---

## JSON: Platform Rule (= BindingRule)

### Effect `variable` (сегодня, default)

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

Legacy без `kind`:

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

`context.*` в CEL — snapshot `@dashboardContext` на момент eval (фаза 1).

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

## Активаторы

| Поле | Статус | Назначение |
|------|--------|------------|
| `onStartup` | exists | старт / attach модели |
| `onVariableChange` | exists | телеметрия, переменные |
| `onEvent` | exists | имя platform event |
| `periodicMs` | exists | периодический пересчёт; индекс `platform_binding_periodic_rules`, wake по `next_run_at` ([bindings](bindings.md)) |
| `onContextChange` | **planned** | изменение `@dashboardContext` |

---

## `@dashboardContext` (planned, фаза 1)

Reserved JSON-переменная на объекте `DASHBOARD` (blueprint `dashboard-v1`).

| Поле | Тип | Назначение |
|------|-----|------------|
| `selection` | `Record<string, string>` | слоты выбора (`device`, `order`, …) |
| `params` | `Record<string, unknown>` | скаляры, режимы, параметры отчёта |
| `widgets` | `Record<string, { visible?: boolean }>` | runtime visibility по id виджета |
| `updatedAt` | ISO string | audit |
| `updatedBy` | string | username |

Web-console `DashboardSession` зеркалирует эту структуру.

### Издатели контекста

| Источник | Действие |
|----------|----------|
| object-table / card-grid / map | `selection.*` + `params.*` из строки |
| report widget | `rowParamsFromRowJson` → `params` |
| function-form | `syncFieldsToSessionJson` → `params` |
| dashboard-link | `contextSelectionJson` / `contextParamsJson` |
| operator URL `?ctx=` | начальный snapshot |

Все publishers (фаза 1+) → **PUT `@dashboardContext`**, не только React state.

### Потребители

| Поле виджета | Читает из контекста |
|--------------|---------------------|
| `selectionKey` | `selection[key]` |
| `paramKey` | `params[key]` |
| `contextPathKey` | `params[key]` как object path |
| visibility | `widgets[widgetId].visible` via rules + runtime merge |

---

## Legacy inventory → Platform Rule

| Legacy | Где | Миграция | Статус |
|--------|-----|----------|--------|
| `showWhenJson` | поле function-form | CEL rule на объекте формы или dashboard rule на `context.params.*` | runtime сохранён; UI migration — фаза 3 |
| `payloadFilterExpr` | event-feed | CEL `condition` на правиле / server-side filter | runtime сохранён; deprecation hint в редакторе |
| `requireSessionParamsJson` | link, form | `condition` на непустые ключи в context | runtime сохранён; deprecation hint в редакторе |
| Dashboard session only (sessionStorage) | operator | `@dashboardContext` + WS | фаза 1 ✅ |
| Per-widget `visible` boolean | layout | static default; runtime → rules → `widgets.*.visible` | фаза 2 ✅ |
| Spreadsheet session blob | `params[sheet:…]` | binding cells + `onContextChange`; optional export rules | planned |
| Alert CEL | объект ALERT | остаётся; тот же CEL editor | — |

**Не добавлять:** `behaviorJson`, `visibleWhen` на виджете, отдельный dashboard DSL.

---

## UI

| Место | Компонент |
|-------|-----------|
| Object Inspector | `BindingRulesPanel` |
| Dashboard Builder | `DashboardRulesPanel` + templates (фаза 2) |
| AI agent | `create_binding_rule` + dashboard templates |

---

## REST / engine (planned)

- Существующий CRUD `/binding-rules` — URL без изменений.
- `BindingRuleEngine.evaluateOnContextChange(dashboardPath, contextJson)` — фаза 1.
- CEL validate: `POST /api/v1/expressions/validate` (уже есть).

---

## Пример: SNMP-мониторинг

**Layout:** object-table `selectionKey: device` + value/chart с тем же key.

**Rules на `root.platform.dashboards.snmp-host-monitoring`:**

1. Когда температура выбранного устройства > 80 → `params.mode = "alarm"`.
2. Когда `params.mode == "alarm"` → `widgets.alarm-panel.visible = true`.
3. Когда `params.mode == "normal"` → `widgets.alarm-panel.visible = false`.
4. Когда mode → alarm → fire `hmi.dashboard.alarm`.

Виджеты **без** условных полей в JSON layout.

---

## Связанные документы

- [bindings](bindings.md) — binding rules, CEL, API
- [dashboards](dashboards.md) — layout, widgets, context
- [decisions/0019-platform-rule-unification.md](decisions/0019-platform-rule-unification.md)
