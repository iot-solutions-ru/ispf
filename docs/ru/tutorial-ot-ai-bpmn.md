> **Язык:** русская версия. Английский оригинал: [en/tutorial-ot-ai-bpmn.md](../en/tutorial-ot-ai-bpmn.md).

# Урок 3: Шаги обращения к языковой модели (ИИ) внутри схемы BPMN

> **Статус:** Beta — использование блоков `llm_complete` и `invoke_agent` в визуальном редакторе процессов BPMN. См. также: [Все уроки серии](ot-automation-excellence-tutorials.md).

## Цель урока
Добавить на диаграмму BPMN шаг обращения к языковой модели (`llm_complete`) для подготовки краткой текстовой сводки по событию и проверить запись результата в переменную процесса.

---

## Пошаговое выполнение

### Шаг 1. Добавьте блок ИИ из палитры редактора BPMN

В конструкторе процессов перетащите на холст элемент **«LLM Complete»** (или **«Invoke Agent»**) и настройте его свойства в правой панели:

| Тип действия (`ispf:action`) | Основные атрибуты шага |
|------------------------------|------------------------|
| `llm_complete` | `promptTemplate` (шаблон запроса с подстановками `${имя_переменной}`), `outputVariable` (куда сохранить ответ), `outputFormat`, `modelRef`, `timeoutMs` |
| `invoke_agent` | `goalTemplate` (цель задачи), `agentMode`, `outputVariable` (в текущей реализации шаг работает в одношаговом режиме запроса к модели: `toolAllowlist` должен оставаться пустым, а `maxSteps <= 1` — многошаговый цикл вызова инструментов внутри BPMN является открытой задачей в [ADR-0049](decisions/0049-ot-automation-excellence.md)) |

### Шаг 2. Выберите подключение к модели (`modelRef`)

| Значение `modelRef` | Какие настройки используются |
|---------------------|------------------------------|
| `platform-default` | Общесерверные настройки `ISPF_AI_*` из `application.yml` |
| `root.platform.credentials.<имя>` | Отдельный зашифрованный ключ и URL из [хранилища учётных данных](tutorial-ot-credentials-vault.md) |

### Шаг 3. Запустите процесс с входными параметрами

```bash
curl -s -X POST "$BASE/api/v1/workflows/by-path/run?path=root.platform.workflows.ai-lab" \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"input":{"alarmId":"A-42"}}' | jq .
```

Ответ модели сохраняется в переменной экземпляра `instanceState.variables.<outputVariable>`. При этом в журнале выполнения шагов (`GET .../steps`) в целях безопасности сохраняются только длина и контрольный хэш запроса (`promptChars`, `promptHash`), а не полный текст с чувствительными данными.

**Следующий урок:** [Урок 4 — Запуск по вебхуку, расписанию и обработка сбоев](tutorial-ot-workflow-triggers.md).
