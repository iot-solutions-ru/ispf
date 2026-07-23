> **Язык:** русская версия (вычитка). Канонический английский: [en/bindings.md](../en/bindings.md).

# Binding rules (правила привязки)

> **Статус:** Stable — CEL и platform bindings. Теги: [doc-status](../en/doc-status.md).

**Binding rule** — декларативное правило расчёта значений переменных на объекте: **когда** (активаторы) → **если** (условие, CEL) → **как** (выражение) → **куда** (цель).

Правила хранятся в системной переменной `@bindingRules` (JSON-массив, reserved). Runtime — **`BindingRuleEngine`** (единый движок привязок с v0.8.0).

**Полный справочник языка** (литералы/операторы CEL, все platform bindings, historian helpers, примеры): **[expression-language](expression-language.md)**.

См. также: [object-model](object-model.md), [blueprints](blueprints.md), ADR [0010-binding-rules-only](decisions/0010-binding-rules-only.md), ADR [0043-unified-platform-ref](decisions/0043-unified-platform-ref.md).

---

## PlatformRef (адреса)

Все ссылки на переменные, функции, события и historian-теги используют одну slash-грамматику (как REST `path` + `name` + `field`):

| Kind | Форма | Пример |
|------|--------|--------|
| variable | `<object>/<name>[/<field>]` | `@/temperature`, `root.platform.devices.a/temperature/value` |
| function | `<object>/fn/<name>` | `@/fn/calculate` |
| event | `<object>/evt/<name>` | `@/evt/alarmRaised` |
| tag | `<object>/tag/<ruleId>` | `root.platform.devices.a/tag/avg-temp-5m` |

`@` — объект, на котором выполняется правило.

| Операция | Пример | Назначение |
|----------|--------|------------|
| `read(ref)` | `read(root.platform.devices.a/temperature)` | Live-поле переменной |
| `call(ref[, inputRef])` | `call(@/fn/ack, @/payload)` | Вызов функции |
| `fire(ref)` | `fire(@/evt/overload)` | Публикация события |
| Historian | `avg(root.../temperature, 5m)`, `live(@/temperature)` | Агрегат по окну / live-сэмпл |
| Object Query | `queryScalar(@/downIfSpec, "count")`, `queryRows(@/downIfSpec)` | KPI / полные строки (JSON) по дереву |
| Writeback | `write(@/setpoint, 42)` | Запись в удалённое поле переменной |

**Object Query (OQ):** spec хранится в функциях `sourceType=object-query`; в выражениях — `@/variable/value`. Reactive bindings — только скаляры (`queryScalar`, `countScan`, `sumScan`); полные таблицы — `queryRows` / `executeQuery` или invoke функции. См. ADR [0044-object-query](decisions/0044-object-query.md).

**JSON-конфиги** (активаторы, виджеты, mimic, script steps): каноническое поле **`ref`** (slash-строка):

```json
{ "ref": "root.platform.devices.virt-cluster.dev-03/sineWave" }
```

---

## Модель `BindingRule`

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

Межобъектная запись (правило на hub, результат на удалённом устройстве):

```json
{
  "id": "mirror-remote-sine",
  "enabled": true,
  "order": 20,
  "activators": {
    "onVariableChange": [{ "ref": "root.platform.devices.cluster.dev-03/sineWave" }]
  },
  "expression": "read(root.platform.devices.cluster.dev-03/sineWave)",
  "target": {
    "kind": "variable",
    "ref": "root.platform.devices.cluster.dev-03/mirroredSine"
  }
}
```

| Поле | Назначение |
|------|------------|
| `activators.onStartup` | Пересчёт при старте сервера / attach модели |
| `activators.onVariableChange` | Список ref переменных; `"ref": "@/*"` или legacy `"self"` + `"*"` = любая локальная переменная |
| `activators.onEventRef` | Полный event ref, напр. `@/evt/alarmRaised` или межобъектный `root.../evt/overload` |
| `activators.periodicMs` | Периодический пересчёт (0 = выкл.); индекс в `platform_binding_periodic_rules`, пробуждение по `next_run_at` |
| `condition` | CEL; пусто = всегда |
| `expression` | CEL или одна platform-функция |
| `target` | Куда записать результат (см. **Типы цели** ниже) |
| `kind` | Опционально: `reactive` (по умолчанию) или `historian` — historian-правила считает analytics engine, не `BindingRuleEngine` ([0041-multi-tag-historian-computations](decisions/0041-multi-tag-historian-computations.md)) |
| `windowBucket` | Только historian: окно агрегата (`5m`, `1h`, `8h`, …) |
| `rollupBuckets` | Только historian: опциональные materialized rollup windows |

### Historian-правила (`kind: historian`)

Тот же массив `@bindingRules`; несколько правил на устройство; произвольные имена выходных переменных. Путь тега в каталоге = `objectPath/tag/ruleId`.

**Рецепты (rolling avg, OEE, цепочки тегов, CEL):** [analytics-historian-cookbook](analytics-historian-cookbook.md)

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

### Периодическое выполнение

Правила с `periodicMs > 0` парсятся в JDBC-индекс `platform_binding_periodic_rules` при сохранении `@bindingRules`. **`BindingPeriodicScheduler`** будит JVM один раз к ближайшему `next_run_at` и выполняет только due-хиты — без посекундного полного скана дерева. Если периодических правил нет, фоновое пробуждение — no-op.

### Типы цели (`target.kind`)

Расширение модели — ADR [0019-platform-rule-unification](decisions/0019-platform-rule-unification.md). Если `target.kind` отсутствует → **`variable`**.

| `kind` | Поля | Назначение |
|--------|------|------------|
| `variable` | `variableName` + `field` на `@`, или канонический **`ref`** (slash) | Запись в переменную объекта (локально или на другом объекте) |
| `action` | — | Только оценка выражения; side-effect через `call()`, `write()`, `queryRows()` |
| `context` | `path` (dot-notation) | Запись в `@dashboardContext` на объекте `DASHBOARD` |
| `event` | `eventName` на `@`, или **`ref`** (`…/evt/…`) | Публикация platform event; payload из `expression` (локально или межобъектно) |

Пример правила дашборда (planned):

```json
{
  "id": "alarm-mode",
  "activators": { "onContextChange": true },
  "condition": "context.selection.device != \"\"",
  "expression": "\"alarm\"",
  "target": { "kind": "context", "path": "params.mode" }
}
```

Активатор **`onContextChange`** — пересчёт при изменении `@dashboardContext`. Полная спецификация: [platform-logic](platform-logic.md).

**Межобъектно:** activator с remote `ref` + `read(remote/ref)` в expression; **target** с remote `ref` пишет результат на другой объект (оркестратор / singleton blueprint). Правила только с side-effect — `target.kind=action` и `write(ref, …)` внутри `expression`. При изменении remote-переменной `BindingPropagationListener` пересчитывает правила на consumer-объектах.

**Активаторы по умолчанию** (если не заданы): remote refs в expression → автоматические remote activators; иначе локальный `self:*`.

---

## Выражения (`expression`)

| Вид | Пример |
|-----|--------|
| **CEL** | `self.temperature.value + 1.0` на текущем объекте (лучше double) |
| **Platform binding** (целое выражение) | `counterRate(@/ifInOctets)`, `hysteresis(@/temperature, 80, 70)`, `read(root.../dev-03/sineWave)`, `queryScalar(@/oqSpec, "count")` |
| **Function / event** | `call(@/fn/dispatch, @/lastIngress)`, `fire(root.../pump/evt/overload)` |

> **Важно:** `read(...)` — **platform binding**, не CEL-функция; нельзя смешивать с `+` в одной строке. Локальная арифметика — CEL `self.*`. Полные таблицы: [expression-language](expression-language.md).

Валидация: `POST /api/v1/expressions/validate` или **Validate** в Web Console.

Stateful bindings (`counterRate`, `hysteresis`, …) — состояние в `@bindingState`.

---

## REST API

```http
GET  /api/v1/objects/by-path/binding-rules?path={objectPath}
PUT  /api/v1/objects/by-path/binding-rules?path={objectPath}
DELETE /api/v1/objects/by-path/binding-rules/{ruleId}?path={objectPath}
```

Agent tool: `create_binding_rule` — slash refs в expression и опциональный `ref` на активаторах.

---

## Модели

В моделях — `ModelBindingRule` (полная схема или `ModelBindingRule.of(id, target, expression)`). При apply/create правила мержатся через `ModelBindingRulesMerger` **после** функций.

`defaultBinding` на переменной модели **удалён** (v0.8.0).

---

## UI

Web Console → Object inspector → вкладка **Computations** (reactive + historian). Редактор выражений включает **PlatformRef picker** и каталог функций.

Для целей **variable** или **event**: выберите **Effect type**, затем либо локальное имя на текущем объекте, либо **путь целевого объекта** + PlatformRef picker (`target.ref`) для записи на другой объект (оркестратор / singleton blueprint).

---

## Не путать с

- **SQL bindings** (`ApplicationSqlBindingService`) — отдельный планировщик, не object binding rules
- **Alert rules**, correlators, workflows — отдельные подсистемы
