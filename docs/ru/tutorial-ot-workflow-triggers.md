> **Язык:** русская версия. Английский оригинал: [en/tutorial-ot-workflow-triggers.md](../en/tutorial-ot-workflow-triggers.md).

# Урок 4: Запуск процесса по вебхуку, расписанию и обработка сбоев (DLQ)

> **Статус:** Beta — автоматический старт процессов BPMN по входящему HTTP-вебхуку и таймеру, настройка повторных попыток и очереди неуспешных запусков (`dead-letters`). См. также: [Все уроки серии](ot-automation-excellence-tutorials.md).

## Цель урока
Настроить на активном процессе (`status = "ACTIVE"`) запуск по внешнему HTTP-вызову (`webhookSlug`), периодический запуск по таймеру (`cronExpression`) и автоматическое восстановление при ошибках.

---

## 1. Запуск по входящему HTTP-вебхуку (`webhookSlug`)

Задайте на узле `WORKFLOW` переменную `webhookSlug` (например, `"lab-alarm-hook"`). Теперь внешняя система может запускать процесс простым POST-запросом:

```bash
curl -s -X POST "$BASE/api/v1/webhooks/workflows/lab-alarm-hook" \
  -H 'Content-Type: application/json' \
  -d '{"alarmId":"A-9","severity":"high"}' | jq .
```

Поля переданного JSON автоматически попадают во входные переменные экземпляра процесса.

---

## 2. Периодический запуск по таймеру (`cronExpression`)

Задайте на узле `WORKFLOW` переменную `cronExpression`, например `"every:1m"` (или `"every:15m"`, `"every:1h"`). Пока процесс находится в статусе `ACTIVE`, планировщик будет запускать его с указанным интервалом.

---

## 3. Повторные попытки при сбоях и очередь недоставленных запусков (`DLQ`)

Чтобы процесс не терялся при временной ошибке внешнего сервиса, задайте на узле `WORKFLOW` параметры повтора и резервного маршрута:

| Переменная узла | Пример значения | Назначение |
|-----------------|-----------------|------------|
| `retryMaxAttempts` | `2` | Максимальное число попыток выполнения |
| `retryBackoffSeconds` | `30` | Пауза в секундах перед повторной попыткой (`WorkflowRetryScheduler`) |
| `errorWorkflowPath` | `"root.platform.workflows.error-handler"` | Путь к резервному процессу обработки ошибки, запускаемому после исчерпания попыток |

Если все попытки завершились ошибкой, запись сохраняется в таблицу `workflow_dead_letters`. Просмотреть и закрыть такие записи можно методами:

```http
GET  /api/v1/workflows/by-path/dead-letters?path=root.platform.workflows.my-flow&unresolvedOnly=true
POST /api/v1/workflows/dead-letters/{id}/resolve
```

**Следующий урок:** [Урок 5 — Зашифрованное хранилище учётных данных (`Credentials Vault`)](tutorial-ot-credentials-vault.md).
