> **Язык:** русская версия (вычитка). Канонический английский: [en/ot-automation-excellence-tutorials.md](../en/ot-automation-excellence-tutorials.md).

# OT Automation Excellence — туториалы

> **Статус:** Beta — Практические гайды по ADR-0049. Хаб: [doc-status](../en/doc-status.md).

Решение: [0049-ot-automation-excellence](decisions/0049-ot-automation-excellence.md).  
Справочник: [workflows](workflows.md), [формулы аналитики](analytics-formulas-and-packs.md), [AI development](ai-development.md).

Это **пошаговые** руководства. Сначала умейте открыть Web Console и создать объект `WORKFLOW` ([workflows](workflows.md)).

## Предварительные требования

- ISPF ≥ **0.9.177**
- Bearer-токен admin (или эквивалент)
- Web Console → Automation → Workflows (или дерево → `WORKFLOW`)
- Опционально: AI (`ISPF_AI_*`) для LLM / Ask AI; MCP (`ispf.mcp.enabled=true`) для публикации `wf_*`

```bash
export BASE=https://ispf.iot-solutions.ru   # или http://localhost:8080
export TOKEN=$(curl -s -X POST "$BASE/api/v1/auth/login" \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin"}' | jq -r .token)
```

## Траектория обучения

| # | Туториал | Время | Чему научитесь |
|---|----------|-------|----------------|
| 1 | [Журнал выполнения](tutorial-ot-workflow-journal.md) | ~10 мин | Запуск workflow и timeline шагов |
| 2 | [Workflow как tool](tutorial-ot-workflow-as-tool.md) | ~15 мин | input/output schema + `invoke_workflow_tool` + MCP `wf_*` |
| 3 | [AI в BPMN](tutorial-ot-ai-bpmn.md) | ~20 мин | Палитра `llm_complete` / `invoke_agent`, `modelRef` |
| 4 | [Триггеры и восстановление](tutorial-ot-workflow-triggers.md) | ~15 мин | Webhook, cron, `errorWorkflowPath`, DLQ |
| 5 | [Credentials vault](tutorial-ot-credentials-vault.md) | ~10 мин | API-ключи; `modelRef` → vault |
| 6 | [Analytics AI](tutorial-ot-analytics-ai.md) | ~15 мин | Agent analysis tools + Ask AI |

Порядок: **1 → 2 → 3**; затем 4–6 по необходимости.

## Вне scope

- Новые **типы элементов** BPMN ([ADR-0047](decisions/0047-custom-bpmn-subset-engine.md))
- Inline code nodes в BPMN
- Forecast / ML inference (BL-175+)
- HITL (`userTask`) через MCP

## Связанное

- [workflows](workflows.md) · [automation](automation.md) · [ai-agent](ai-agent.md)
