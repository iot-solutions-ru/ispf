> **Язык:** русская версия (вычитка). Канонический английский: [en/analytics-tag-catalog.md](../en/analytics-tag-catalog.md).

# Каталог analytics-тегов (BL-209 / ADR-0041)

Развёрнутые **historian-вычисления** обнаруживаются из `@bindingRules` с `kind: historian` на объектах `DEVICE`. Одно правило = один тег каталога и одна live-переменная.

**Cookbook (OEE, цепочки):** [analytics-historian-cookbook.md](analytics-historian-cookbook.md)

## Идентичность тега

| Поле | Значение |
|------|----------|
| Путь тега | `objectPath#ruleId` |
| Объект | Устройство с правилом и выходной переменной |
| Выход | `target.variableName` (несколько правил на устройство — норма) |
| Id правила | Стабильный id в `@bindingRules` |

Пример: правило `avg-temp-5m` на `root.platform.devices.sensor-a` → тег `…sensor-a#avg-temp-5m` → переменная `avgTemp5m`.

## Метаданные правила (`@historianRuleMeta`)

Системная переменная `@historianRuleMeta` (JSON по id правила):

| Ключ | Назначение |
|------|------------|
| `quality` | `ok`, `uncertain`, `error`, `disabled` |
| `lastEvalAt` | Время последнего расчёта |
| `lastEvalStatus` | `ok`, `error`, `skipped` |

Обновляется analytics engine при каждом tick. **Не** является входом для `hist.avg` — только диагностика и quality в каталоге. Подробнее: [cookbook § `@historianRuleMeta`](analytics-historian-cookbook.md#historianrulemeta--что-это-и-чего-не-делать).

## Эталон на prod

Полная цепочка и дашборд: [cookbook, рецепт 5](analytics-historian-cookbook.md#рецепт-5--полный-пример-на-prod-analytics-demo) — `root.platform.devices.analytics-demo`, скрипты `deploy/local/tools/setup-historian-chain-*.py`.

## REST API

| Метод | Путь | Описание |
|-------|------|----------|
| GET | `/api/v1/platform/analytics/tags?path=` | Список тегов |
| GET | `/api/v1/platform/analytics/tags/by-path?path=` | Один тег (`objectPath#ruleId`) |
| POST | `/api/v1/platform/analytics/tags/backfill` | Backfill окна |
| POST | `/api/v1/platform/analytics/expression/validate` | Валидация CEL + `hist.*` |
| POST | `/api/v1/platform/analytics/query` | Мультитеговый запрос |

| GET | `/api/v1/platform/analytics/catalog` | Единый каталог функций Tier A/B/C (BL-212) |
| GET | `/api/v1/platform/analytics/catalog/{functionId}` | Метаданные одной функции |
| POST | `/api/v1/platform/analytics/catalog/validate` | Валидация выражения |
| GET | `/api/v1/platform/analytics/formulas?scope=site` | Список формул Tier B площадки (BL-214) |
| GET | `/api/v1/platform/analytics/formulas?scope=app&appId=` | Формулы приложения |
| POST | `/api/v1/platform/analytics/formulas` | Создать формулу |
| PUT | `/api/v1/platform/analytics/formulas/{id}` | Обновить; перепривязка `formulaRef` |
| DELETE | `/api/v1/platform/analytics/formulas/{id}` | Удалить формулу |
| POST | `/api/v1/platform/analytics/formulas/{id}/expand` | Развернуть шаблон `{{param}}` |
| GET | `/api/v1/platform/analytics/tags/evaluate?path=` | Probe historian-тега (`objectPath#ruleId`) |

Поля ответа каталога, CEL-over-historian и propagation quality — см. [английскую версию](../en/analytics-tag-catalog.md).

**Формулы и пакеты расширений (Tier B/C):** [analytics-formulas-and-packs.md](analytics-formulas-and-packs.md).

## UI

Вкладка **Вычисления**: единый список правил (reactive + historian), статус historian-тегов каталога, журнал audit. Пресеты — в каталоге функций модального редактора выражения, не отдельные кнопки на панели.

## Устарело (ADR-0041)

- Только `derivedValue` / `oeePct` как признак тега
- `ANALYTICS_TEMPLATE` в `root.platform.analytics`
- `templates/apply`

## См. также

- [ADR-0041](decisions/0041-multi-tag-historian-computations.md)
- [analytics-historian-cookbook.md](analytics-historian-cookbook.md)
- [analytics-formulas-and-packs.md](analytics-formulas-and-packs.md)
