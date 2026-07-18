> **Язык:** русская версия (вычитка). Канонический английский: [en/tutorial-ot-workflow-as-tool.md](../en/tutorial-ot-workflow-as-tool.md).

# Туториал: workflow как типизированный tool

> **Статус:** Beta — ADR-0049 Wave 1 / 5. Хаб: [OT Automation туториалы](ot-automation-excellence-tutorials.md).

## Цель

Сделать ACTIVE workflow вызываемым tool для агента (`invoke_workflow_tool`) и опционально MCP-инструментом `wf_<name>`.

## Подготовка

[Хаб](ot-automation-excellence-tutorials.md#предварительные-требования). Сначала workflow без CONTROL side-effects.

## Шаги

### 1. Схемы на объекте WORKFLOW

| Переменная | Пример |
|------------|--------|
| `inputSchemaJson` | `{"type":"object","required":["alarmId"],"properties":{"alarmId":{"type":"string"}}}` |
| `outputSchemaJson` | проекция полей выхода (см. EN) |
| `toolDescription` | непустая строка для агента/MCP |
| `sideEffectClass` | `READ` / `WRITE` |
| `status` | `ACTIVE` |

`outputSchemaJson` проецирует **instance variables** (ключи `input`, AI `outputVariable`, `read_variable`→`contextKey`).  
`set_variable` пишет в **дерево объектов**, не в instance vars.

### 2. REST invoke-tool

```bash
curl -s -X POST "$BASE/api/v1/workflows/by-path/invoke-tool?path=root.platform.workflows.alarm-tool" \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"input":{"alarmId":"A-100"}}' | jq .
```

Ожидание: `status=OK` и `output` по схеме. Без обязательных полей — ошибка валидации.

### 3. Агент

AI Studio → tool `invoke_workflow_tool` с `path` + `input`. Работает только для **ACTIVE**.

### 4. MCP (опционально)

Нужны `ispf.mcp.enabled=true`, ACTIVE + непустой `toolDescription`.

```bash
curl -s -X POST "$BASE/api/v1/ai/mcp" \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"jsonrpc":"2.0","id":1,"method":"tools/list","params":{}}' \
  | jq '.result.tools[] | select(.name|startswith("wf_"))'
```

Имя: `wf_<последний-сегмент-пути>`.

## Проверка

- [ ] Отсутствие `alarmId` отклоняется
- [ ] Happy path возвращает `output`
- [ ] (Опционально) `wf_*` в `tools/list`

## Безопасность

CONTROL — через `userTask` + `invoke_function` / `set_variable`, не в default operator allowlist.

## Дальше

[AI в BPMN](tutorial-ot-ai-bpmn.md) · [Credentials vault](tutorial-ot-credentials-vault.md)
