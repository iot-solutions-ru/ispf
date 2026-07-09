> **Язык:** русская версия (вычитка). Канонический английский: [en/analytics-historian-cookbook.md](../en/analytics-historian-cookbook.md).

# Cookbook: historian-вычисления

Рецепты для **historian binding rules** (`kind: historian` в `@bindingRules`). Одно правило = один analytics-тег со своей выходной переменной, расписанием и выражением.

См. [ADR-0041](decisions/0041-multi-tag-historian-computations.md) и [analytics-tag-catalog.md](analytics-tag-catalog.md).

---

## Модель

| Понятие | Значение |
|---------|----------|
| Хранение | `@bindingRules` на **целевом устройстве** (тот же JSON-массив, что и реактивные правила) |
| Kind | `"historian"` — `BindingRuleEngine` пропускает; analytics engine компилирует и считает |
| Путь тега | `objectPath#ruleId` (напр. `root.devices.sensor-a#avg-temp-5m`) |
| Live-значение | `target.variableName` на устройстве (любое имя, не только `derivedValue`) |
| Метаданные | `@historianRuleMeta` — quality и last eval по id правила |
| Reactive vs historian | Reactive — мгновенный CEL; historian — окна historian и DAG |

---

## Форма правила

```json
{
  "id": "avg-temp-5m",
  "name": "Скользящее среднее 5m",
  "enabled": true,
  "order": 10,
  "kind": "historian",
  "activators": {
    "onStartup": false,
    "onVariableChange": [
      { "objectPath": "root.platform.devices.sensor-a", "variableName": "temperature" }
    ],
    "onEvent": null,
    "periodicMs": 60000
  },
  "condition": "",
  "expression": "rollingAvg(root.platform.devices.sensor-a.temperature, 5m)",
  "windowBucket": "5m",
  "target": { "kind": "variable", "variableName": "avgTemp5m", "field": "value" }
}
```

### Виды выражений

| Форма | Пример | Helper |
|-------|--------|--------|
| Builtin | `rollingAvg(path.var, 5m)` | `rollingAvg` |
| Builtin | `rateOfChange(path.var, 1h)` | `rateOfChange` |
| Builtin | `oee('path', 'avail', 'perf', 'qual', 8h)` | `oee` |
| CEL + historian | `hist.avg('path', 'var', '5m')` | `cel` |
| CEL-композит | `(hist.avg('a', 't', '5m') + hist.avg('b', 't', '5m')) / 2.0` | `cel` |

В CEL используйте **литералы double** (`2.0`, не `2`) при смешении с развёрткой `hist.*`.

---

## Сохранение (REST)

```http
GET  /api/v1/objects/by-path/binding-rules?path={devicePath}
PUT  /api/v1/objects/by-path/binding-rules?path={devicePath}
```

Historian-правила автоматически создают целевую переменную, если её нет.

**UI:** инспектор объекта → **Вычисления** → правило или кнопка пресета.

---

## Встроенные пресеты

Статические рецепты (не объекты в дереве). Id совпадают с `HistorianComputationPresets` на сервере (API / cookbook).

| id | Выход (по умолчанию) | Шаблон |
|----|----------------------|--------|
| `rollingAvg` | `avgValue` | `hist.avg(…)` |
| `rateOfChange` | `rocValue` | `rateOfChange(…)` |
| `oee` | `oeePct` | `oee(…)` |
| `customCel` | `computedValue` | CEL с `hist.*` |

---

## Рецепт 1 — скользящее среднее

**Цель:** среднее `temperature` за 5m на `sensor-a` → переменная `avgTemp5m`.

```json
{
  "id": "avg-temp-5m",
  "kind": "historian",
  "expression": "rollingAvg(root.platform.devices.sensor-a.temperature, 5m)",
  "windowBucket": "5m",
  "target": { "kind": "variable", "variableName": "avgTemp5m", "field": "value" }
}
```

(Добавьте `activators`, `enabled`, `order` как в полном примере выше.)

**Путь тега:** `root.platform.devices.sensor-a#avg-temp-5m`

---

## Рецепт 2 — OEE (A × P × Q)

**Цель:** OEE смены на линии из `availabilityPct`, `performancePct`, `qualityPct` (0–100, с historian).

```json
{
  "id": "shift-oee",
  "kind": "historian",
  "expression": "oee('root.platform.devices.line-01', 'availabilityPct', 'performancePct', 'qualityPct', '8h')",
  "windowBucket": "8h",
  "activators": { "periodicMs": 300000, "onVariableChange": [ … три переменные … ] },
  "target": { "kind": "variable", "variableName": "oeePct", "field": "value" }
}
```

**Формула:** `oeePct = (A/100) × (P/100) × (Q/100) × 100` по buckets в `windowBucket`.

**Тег:** `root.platform.devices.line-01#shift-oee`

---

## Рецепт 3 — цепочка из трёх тегов

**Цель:** сырой сенсор → сглаживание → ещё сглаживание → финальный KPI.

```mermaid
flowchart LR
  S["demo-sensor-01.temperature"] --> A["chain-a / derived-a"]
  A --> B["chain-b / derived-b"]
  B --> C["chain-c / derived-c"]
```

1. **analytics-chain-a:** `rollingAvg(demo-sensor-01.temperature, 1h)` → `derived-a`
2. **analytics-chain-b:** источник `analytics-chain-a.derived-a` → `derived-b`
3. **analytics-chain-c:** источник `analytics-chain-b.derived-b` → `derived-c`

Каждое правило на **своём** устройстве; id правила уникален в `@bindingRules` этого устройства.

**Проверка lineage:**

```http
GET /api/v1/platform/analytics/tags/by-path?path=root.platform.devices.analytics-chain-c#analytics-chain-c-rule
```

В ответе: `upstreamTagPaths`, `downstreamTagPaths`, `lineage`.

---

## Рецепт 4 — CEL-композит между устройствами

**Цель:** среднее температур двух сенсоров на третьем устройстве.

```json
{
  "id": "avg-ab-5m",
  "kind": "historian",
  "expression": "(hist.avg('root.platform.devices.analytics-demo.sensor-a', 'temperature', '5m') + hist.avg('root.platform.devices.analytics-demo.sensor-b', 'temperature', '5m')) / 2.0",
  "windowBucket": "5m",
  "target": { "kind": "variable", "variableName": "temperature", "field": "value" }
}
```

Валидация: `POST /api/v1/platform/analytics/expression/validate`.

---

## Catalog API

| Метод | Путь | Описание |
|-------|------|----------|
| GET | `/api/v1/platform/analytics/tags?path=` | Список тегов |
| GET | `/api/v1/platform/analytics/tags/by-path?path=` | Один тег (`objectPath#ruleId`) |
| POST | `/api/v1/platform/analytics/query` | Мультитеговый запрос для графиков |

Полная таблица — в [английской версии](../en/analytics-historian-cookbook.md).

---

## Дашборды

- **Live:** path устройства + **имя выходной переменной** правила.
- **История нескольких тегов:** `/analytics/query`, без id шаблонов из дерева.

---

## Устарело: `ANALYTICS_TEMPLATE`

Поток «шаблон в `root.platform.analytics` → Apply → `derivedValue`» **снят с поддержки** для новых конфигураций (ADR-0041). Используйте binding rules и этот cookbook.

---

## См. также

- [bindings.md](bindings.md)
- [ADR-0040](decisions/0040-unified-computations-ui.md), [ADR-0041](decisions/0041-multi-tag-historian-computations.md)
