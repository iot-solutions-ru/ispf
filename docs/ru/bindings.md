> **Язык:** русская версия (вычитка). Канонический английский: [en/bindings.md](../en/bindings.md).

﻿# Привязки цепочки (обязательные правила)

**Обязательное правило** — декларативное правило расчёта значений переменных на объекте: **когда** (активаторы) → **если** (условие, CEL) → **как** (выражение) → **куда** (цель).

Правила хранятся в системной переменной `@bindingRules` (JSON-массив). Runtime — **`BindingRuleEngine`**.

См. также: [object-model](object-model.md), [blueprints](blueprints.md), ADR [0010-binding-rules-only](decisions/0010-binding-rules-only.md), ADR [0043-unified-platform-ref](decisions/0043-unified-platform-ref.md).

---

## PlatformRef (адреса)

Единая slash-грамматика для переменных, функций, событий и historian-тегов (как REST `path` + `name` + `field`):

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
| `call(ref[, inputRef])` | `call(@/fn/ack, @/payload)` | Вызов function |
| `fire(ref)` | `fire(@/evt/overload)` | Публикация event |
| Historian | `avg(root.../temperature, 5m)`, `live(@/temperature)` | Агрегат по окну / live |
| Object Query | `queryScalar(@/downIfSpec, "count")`, `queryRows(@/downIfSpec)` | KPI / полная таблица (JSON) по дереву |
| Writeback | `write(@/setpoint, 42)` | Запись в удалённое поле переменной |

**Object Query (OQ):** spec хранится в функциях `sourceType=object-query`; в выражениях — `@/variable/value`. Reactive bindings — только скаляры; полные таблицы — `queryRows` / `executeQuery` или invoke функции. ADR [0044-object-query](decisions/0044-object-query.md).

**JSON-конфиги** (активаторы, виджеты, mimic, script steps): каноническое поле **`ref`**:

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
      { "ref": "root.platform.devices.virt-cluster.dev-03/sineWave" }
    ],
    "onEventRef": null,
    "periodicMs": 0
  },
  "condition": "",
  "expression": "read(root.platform.devices.virt-cluster.dev-03/sineWave)",
  "target": { "variableName": "member3Sine", "field": "value" }
}
```

| Поле | Назначение |
|------|------------|
| `activators.onVariableChange` | Список ref переменных; `@/*` или `self` + `*` = любая локальная переменная |
| `activators.onEventRef` | Полный event ref, напр. `@/evt/alarmRaised` |
| `expression` | CEL или platform binding |
| `kind` | `reactive` (по умолчанию) или `historian` |

Historian-тег в каталоге: `objectPath/tag/ruleId`. Рецепты: [analytics-historian-cookbook](analytics-historian-cookbook.md).

**Cross-object:** activator с remote `ref` + `read(remote/ref)` в выражении; `BindingPropagationListener` пересчитывает правила на consumer-объектах.

**Активаторы по умолчанию:** remote refs в expression → автоматические remote activators; иначе `self:*`.

---

## Выражения

| Вид | Пример |
|-----|--------|
| **CEL** | `read(@/temperature) + 1.0` |
| **Platform binding** | `counterRate(@/ifInOctets)`, `hysteresis(@/temperature, 80, 70)` |
| **Function / event** | `call(@/fn/dispatch, @/lastIngress)`, `fire(root.../pump/evt/overload)` |

Валидация: `POST /api/v1/expressions/validate` или **Validate** в Web Console.

---

## UI

Web Console → Object inspector → **Computations** → редактор с **PlatformRef picker** и каталогом функций.

---

## Не путать с

- **SQL bindings** — отдельный планировщик
- **Alert rules**, correlators, workflows — отдельные подсистемы
