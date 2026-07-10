> **Язык:** русская версия (вычитка). Канонический английский: [en/platform-logic.md](../en/platform-logic.md).

# Единая логика платформы (Правило платформы)

Один механизм для привязки функций, логики дашборда и побочных эффектов.

**Статус:** спецификация (ADR [0019-platform-rule-unification](decisions/0019-platform-rule-unification.md), **Предлагается**). Продление времени выполнения — по фазам 1–3.

---

## Модель для человека

```
КОГДА  →  ЕСЛИ (CEL)  →  ТОГДА (записать результат)
```

Та же ментальная модель, что [обязательные правила](bindings.md):

- **Обязательное правило** на устройстве → пишется в переменную.
- **Dashboard rule** на объекте `DASHBOARD` → пишет в `@dashboardContext` или fire event.
- **Правило оповещения** на `ALERT` → отдельный объект, но условие = CEL (будущая унификация пользовательского интерфейса — не в области действия v1).

---

## JSON: правило платформы (= BindingRule)

### Effect `variable` (сегодня, default)

```json
{
  "id": "avg-temp",
  "enabled": true,
  "order": 10,
  "activators": {
    "onVariableChange": [{ "objectPath": "self", "variableName": "temperature" }]
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
  "condition": "context.selection.device != \"\" && refAt(context.selection.device, temperature).value > 80",
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
| `onStartup` | есть | старт / attach модели |
| `onVariableChange` | есть | телеметрия, переменные |
| `onEvent` | есть | имя platform event |
| `periodicMs` | есть | периодический пересчет; индекс `platform_binding_periodic_rules`, пробуждение по `next_run_at` ([bindings](bindings.md)) |
| `onContextChange` | **planned** | изменение `@dashboardContext` |

---

## `@dashboardContext` (planned, фаза 1)

Reserved JSON-переменная на объекте `DASHBOARD` (модель `dashboard-v1`).

| Поле | Тип | Назначение |
|------|-----|------------|
| `selection` | `Record<string, string>` | слоты выбора (`device`, `order`, …) |
| `params` | `Record<string, unknown>` | скаляры, режимы, параметры отчёта |
| `widgets` | `Record<string, { visible?: boolean }>` | runtime visibility по id виджета |
| `updatedAt` | ISO string | audit |
| `updatedBy` | string | username |

Web-console `DashboardSession` зеркалирует эту структуру.

### Публикация контекста (издатели)

| Источник | Действие |
|----------|----------|
| object-table / card-grid / map | `selection.*` + `params.*` из строки |
| report widget | `rowParamsFromRowJson` → `params` |
| function-form | `syncFieldsToSessionJson` → `params` |
| dashboard-link | `contextSelectionJson` / `contextParamsJson` |
| operator URL `?ctx=` | начальный snapshot |

Все publishers (фаза 1+) → **PUT `@dashboardContext`**, не только React state.

### Потребление (потребители)

| Поле виджета | Читает из контекста |
|--------------|-------------------|
| `selectionKey` | `selection[key]` |
| `paramKey` | `params[key]` |
| `contextPathKey` | `params[key]` как object path |
| visibility | `widgets[widgetId].visible` via rules + runtime merge |

---

## Инвентарь наследие → Правило платформы

| Наследие | Где | Миграция | Статус |
|--------|-----|----------|--------|
| `showWhenJson` | поле функциональной формы | Правило CEL для объекта формы или правило на дашборде `context.params.*` | время выполнения сохранён; Миграция пользовательского интерфейса — этап 3 |
| `payloadFilterExpr` | лента событий | CEL `condition` на правило/фильтр на стороне сервера | время выполнения сохранён; подсказка об устаревании в редакторе |
| `requireSessionParamsJson` | ссылка, форма | `condition` на непустые ключи в контексте | время выполнения сохранён; подсказка об устаревании в редакторе |
| Dashboard session only (sessionStorage) | operator | `@dashboardContext` + WS | фаза 1 ✅ |
| Для каждого виджета `visible` логическое значение | макет | статическое значение по умолчанию; время выполнения → правила → `widgets.*.visible` | фаза 2 ✅ |
| Большой двоичный объект сеанса электронной таблицы | `params[sheet:…]` | связывающие клетки + `onContextChange`; дополнительные правила экспорта | запланировано |
| Оповещение CEL | ПРЕДУПРЕЖДЕНИЕ объект | остается; тот же редактор CEL | — |

**Не добавлять:** `behaviorJson`, `visibleWhen` на виджете, отдельный dashboard DSL.

---

## Пользовательский интерфейс

| Место | Компонент |
|-------|-----------|
| Object Inspector | `BindingRulesPanel` |
| Dashboard Builder | `DashboardRulesPanel` + templates (фаза 2) |
| AI agent | `create_binding_rule` + dashboard templates |

---

## REST/движок (планируется)

- Существующий CRUD `/binding-rules` — без изменений URL.
- `BindingRuleEngine.evaluateOnContextChange(dashboardPath, contextJson)` — фаза 1.
- CEL validate: `POST /api/v1/expressions/validate` (уже есть).

---

## Пример: SNMP-мониторинг

**Layout:** object-table `selectionKey: device` + value/chart с тем же key.

**Rules на `root.platform.dashboards.snmp-host-monitoring`:**

1. Когда температура на выбранном устройстве > 80 → `params.mode = "alarm"`.
2. Когда `params.mode == "alarm"` → `widgets.alarm-panel.visible = true`.
3. Когда `params.mode == "normal"` → `widgets.alarm-panel.visible = false`.
4. В режиме → Тревога → Пожар `hmi.dashboard.alarm`.

Виджеты **без** условных полей в формате JSON.

---

## Связанные документы

- [bindings](bindings.md) — правила привязки, CEL, API
- [dashboards](dashboards.md) — макет, виджеты, контекст
- [decisions/0019-platform-rule-unification.md](decisions/0019-platform-rule-unification.md)
