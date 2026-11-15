> **Язык:** русская версия. Английский оригинал: [en/tutorial-ot-workflow-as-tool.md](../en/tutorial-ot-workflow-as-tool.md).

# Урок 2: Вызов процесса BPMN как типизированного инструмента (`wf_*`)

> **Статус:** Beta — публикация активного процесса BPMN со строгой схемой входов и выходов для вызова из REST API, ИИ-агента и внешних клиентов MCP. См. также: [Все уроки серии](ot-automation-excellence-tutorials.md).

## Цель урока
Задать на узле `WORKFLOW` входную и выходную JSON-схемы, чтобы процесс можно было синхронно вызывать как проверенный инструмент (`invoke-tool`) из ИИ-студии или внешней IDE по протоколу MCP.

---

## Пошаговое выполнение

### Шаг 1. Заполните переменные описания инструмента на узле `WORKFLOW`

| Переменная узла | Пример значения | Назначение |
|-----------------|-----------------|------------|
| `inputSchemaJson` | `{"type":"object","required":["alarmId"],"properties":{"alarmId":{"type":"string"}}}` | JSON Schema обязательных входных параметров |
| `outputSchemaJson` | `{"type":"object","properties":{"summary":{"type":"string"}}}` | Перечень переменных экземпляра процесса, которые вернутся в ответе |
| `toolDescription` | `"Классификация тревоги по её коду"` | Понятное описание назначения инструмента для человека и ИИ-агента |
| `sideEffectClass` | `"READ"` или `"WRITE"` | Класс воздействия (чтение данных или запись изменений) |
| `status` | `"ACTIVE"` | Вызов инструмента разрешён только для активных процессов |

### Шаг 2. Вызовите процесс через REST API (`invoke-tool`)

```bash
curl -s -X POST "$BASE/api/v1/workflows/by-path/invoke-tool?path=root.platform.workflows.alarm-tool" \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"input":{"alarmId":"A-100"}}' | jq .
```

Сервер сначала проверит входные данные по `inputSchemaJson` (при отсутствии обязательного поля `alarmId` вернёт ошибку валидации `400`), выполнит процесс и вернёт отфильтрованный объект `output`.

### Шаг 3. Вызовите процесс из ИИ-агента или по протоколу MCP

- Внутри ИИ-студии агент вызывает такие процессы инструментом **`invoke_workflow_tool`**.
- Если на сервере включён адаптер MCP (`ispf.mcp.enabled=true`), каждый активный процесс с непустым `toolDescription` автоматически публикуется в списке инструментов MCP под именем **`wf_<имя_узла>`**:

```bash
curl -s -X POST "$BASE/api/v1/ai/mcp" \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"jsonrpc":"2.0","id":1,"method":"tools/list","params":{}}' \
  | jq '.result.tools[] | select(.name|startswith("wf_"))'
```

**Следующий урок:** [Урок 3 — Шаги обращения к языковой модели внутри BPMN](tutorial-ot-ai-bpmn.md).
