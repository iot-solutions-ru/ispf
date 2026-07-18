> **Язык:** русская версия (вычитка). Канонический английский: [en/tutorial-ot-analytics-ai.md](../en/tutorial-ot-analytics-ai.md).

# Туториал: Analytics AI

> **Статус:** Beta — ADR-0049 Wave 2. Хаб: [OT Automation туториалы](ot-automation-excellence-tutorials.md).

## Цель

Использовать детерминированные analysis tools и кнопку **Ask AI** в inspector тега.

## Подготовка

Тег / переменная с историей. Справка: [формулы аналитики](analytics-formulas-and-packs.md).

## A. Tools агента

| Tool | Назначение |
|------|------------|
| `list_analytics_catalog` | Каталог функций |
| `get_analytics_tag` | Метаданные тега |
| `query_analytics_tags` | Поиск по каталогу |
| `evaluate_analytics_expression` | Вычисление expression |
| `detect_anomalies` | z-score / anomaly |
| `compare_periods` | period-over-period |
| `summarize_trend` | summary → (опц.) LLM narrative |

Сначала убедитесь, что tool вернул **структурированный summary**, и только потом prose.

## B. Ask AI в Web Console

Object tree → analytics tag → **Ask AI**.

API:

```bash
curl -s -X POST "$BASE/api/v1/platform/analytics/tags/ask?objectPath=root.platform.devices.demo-sensor-01&variable=temperature&hours=4" \
  -H "Authorization: Bearer $TOKEN" | jq '{status, summary, narrative}'
```

## C. Из BPMN (опционально)

`invoke_agent` с allowlist `summarize_trend,detect_anomalies,get_variable_history` — см. [AI в BPMN](tutorial-ot-ai-bpmn.md).

## Проверка

- [ ] Каталог показывает analysis kinds
- [ ] Ask AI / `summarize_trend` дают `summary` + `narrative`
- [ ] При выключенном AI narrative — noop stub, summary есть

## Вне scope

Forecast / streaming ML — BL-175+, не ADR-0049.

## Дальше

[Хаб](ot-automation-excellence-tutorials.md)
