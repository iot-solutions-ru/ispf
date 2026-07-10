> **Язык:** русская версия (вычитка). Канонический английский: [../../en/decisions/0041-multi-tag-historian-computations.md](../../en/decisions/0041-multi-tag-historian-computations.md).

# ADR-0041: Мультитеговые historian-вычисления (binding rules)

## Статус

**Принято** (2026-07-09)

## Контекст

После ADR-0040 historian-теги оставались на MVP-модели: один `derivedValue`/`oeePct` на устройство, объекты `ANALYTICS_TEMPLATE` в `root.platform.analytics`, метаданные на уровне устройства.

До 1.0 нет prod-легаси. MVP путал операторов: «шаблон в дереве» vs «живой тег на устройстве».

## Решение

### 1. Historian = `BindingRule` с `kind: historian`

В `@bindingRules` рядом с reactive:

```json
{
  "id": "avg-temp-5m",
  "kind": "historian",
  "expression": "rollingAvg(root.platform.devices.sensor-a.temperature, 5m)",
  "windowBucket": "5m",
  "target": { "kind": "variable", "variableName": "avgTemp5m", "field": "value" }
}
```

- **Несколько правил на DEVICE** — произвольное `target.variableName`
- **Путь тега** — `objectPath#ruleId`
- **Reactive engine** пропускает `historian`; **analytics engine** компилирует и считает

Рецепты (OEE, цепочки, CEL): [analytics-historian-cookbook](../../analytics-historian-cookbook.md). Эталон на prod: cookbook **рецепт 5** (`analytics-demo`).

### 2. Убрать каталог `ANALYTICS_TEMPLATE`

- Нет bootstrap `root.platform.analytics.*`
- **Пресеты** — статический код и cookbook
- `/templates/*` устарело

### 3. Метаданные по правилу

`@historianRuleMeta` на устройстве — quality и last eval по id правила.

### 4. Устарело

- `derivedValue`/`oeePct` как единственный признак тега
- `analyticsExpression` / `applyTemplate` для новых конфигураций

## Последствия

- Один список правил на вкладке «Вычисления»
- Виджеты ссылаются на выходные переменные правил
- DAG и lineage по цепочкам тегов

## Реализация (2026-07-09)

| Решение ADR | Статус |
|-------------|--------|
| `kind: historian` в `@bindingRules` | ✅ |
| Каталог `objectPath#ruleId`, DAG / lineage | ✅ |
| `@historianRuleMeta` по rule id | ✅ |
| Вкладка «Вычисления» (ADR-0040) | ✅ |
| Без bootstrap `ANALYTICS_TEMPLATE` | ✅ |
| Пресеты в коде + каталог редактора (без toolbar) | ✅ |
| Эталон prod + дашборд + скрипты deploy/tools | ✅ |
| API `/templates/*` | устарело, не для новых конфигураций |

Полная таблица сверки — [cookbook § Сверка с планом](../../analytics-historian-cookbook.md#сверка-с-планом-adr-0040--adr-0041).

## См. также

- [0040-unified-computations-ui](0040-unified-computations-ui.md)
- [analytics-historian-cookbook](../../analytics-historian-cookbook.md)
- [analytics-tag-catalog](../../analytics-tag-catalog.md)
