> **Язык:** русская редакция. Канонический английский: [en/reference-asset-analytics.md](../en/reference-asset-analytics.md).

# Аналитика активов (BL-160 / BL-201)

Облегчённый AF-подобный каркас: каталог KPI-шаблонов, historian rollup, runtime **производные теги**, typed inspector в Explorer и apply workflow.

**Статус:** БЛ-201 Done — editor + CRUD API + `derivedValue` runtime + example.

## Модель объектов

| Путь | Тип | Назначение |
| --- | --- | --- |
| `root.platform.analytics` | `ANALYTICS` | Каталог |
| `root.platform.analytics.<templateId>` | `ANALYTICS_TEMPLATE` | Определение rollup/KPI |

Переменные шаблона: `templateId`, `helper`, `sourcePath`, `sourceVariable`, `sourceField`, `windowBucket`, `blueprintName`, `enabled`.

## Встроенные шаблоны

- **rollingAvg** — среднее historian за `windowBucket` → `derivedValue`
- **rateOfChange** — дельта между bucket → `derivedValue`
- **oee** — A×P×Q → `oeePct` и компоненты

Blueprints: `rolling-avg-v1`, `rate-of-change-v1`, `oee-v1`.

## Планировщик derived tags

`AnalyticsDerivedTagRunner` периодически обновляет runtime-переменные через `VariableHistoryService.aggregate`.

Конфиг `ispf.analytics`: `derived-tag-enabled`, `derived-tag-tick-ms`.

Ручной refresh: `POST /api/v1/platform/analytics/derived-tags/refresh?devicePath=…`

## Web console

В Explorer: папка analytics → список; шаблон → инспектор **Шаблон аналитики** (форма, превью, применение к устройству).

Виджет chart: поле `analyticsTemplateId` задаёт helper и bucket агрегации.

## Пример

[examples/analytics-rolling-avg/README.md](../../examples/analytics-rolling-avg/README.md)
