> **Язык:** русская версия (вычитка). Канонический английский: [en/tutorial-ot-ai-bpmn.md](../en/tutorial-ot-ai-bpmn.md).

# Туториал: AI service tasks в BPMN

> **Статус:** Beta — ADR-0049 Wave 2. Хаб: [OT Automation туториалы](ot-automation-excellence-tutorials.md).

## Цель

Добавить `llm_complete` / `invoke_agent` из палитры Workflow Builder, запустить и проверить redaction в журнале.

## Подготовка

- [Хаб](ot-automation-excellence-tutorials.md#предварительные-требования)
- Настроенный AI или режим **noop**
- Опционально: [credentials vault](tutorial-ot-credentials-vault.md)

## Шаги

### 1. Палитра

В BPMN-редакторе перетащите **Create AI: LLM Complete** или **Create AI: Invoke Agent**. Дополнительно правьте атрибуты в панели ISPF.

| Action | Ключевые атрибуты |
|--------|-------------------|
| `llm_complete` | `promptTemplate`, `outputVariable`, `outputFormat`, `modelRef`, `timeoutMs` |
| `invoke_agent` | `goalTemplate`, `agentMode`, `toolAllowlist`, `maxSteps`, `outputVariable` |

`${var}` подставляется из переменных instance / input запуска.

### 2. Запуск с input

```bash
curl -s -X POST "$BASE/api/v1/workflows/by-path/run?path=root.platform.workflows.ai-lab" \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"input":{"alarmId":"A-42"}}' | jq .
```

Проверьте `instanceState.variables.<outputVariable>`.

### 3. `modelRef`

| Значение | Смысл |
|----------|--------|
| `platform-default` | настройки `ISPF_AI_*` |
| id модели | модель + platform API key |
| `root.platform.credentials.…` | vault: secret = API key; metadata `baseUrl` / `model` |

### 4. Журнал

В timeline / `GET .../steps` для AI-шагов — `promptHash` / `promptChars`, **не** полный промпт.

## Проверка

- [ ] Палитра создаёт ServiceTask с нужным action
- [ ] Результат в `outputVariable`
- [ ] Полный промпт в journal не сохраняется

## Дальше

[Триггеры](tutorial-ot-workflow-triggers.md) · [Credentials](tutorial-ot-credentials-vault.md)
