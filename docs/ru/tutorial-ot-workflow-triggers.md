> **Язык:** русская версия (вычитка). Канонический английский: [en/tutorial-ot-workflow-triggers.md](../en/tutorial-ot-workflow-triggers.md).

# Туториал: webhook, cron и восстановление после ошибок

> **Статус:** Beta — ADR-0049 Wave 3. Хаб: [OT Automation туториалы](ot-automation-excellence-tutorials.md).

## Цель

Стартовать workflow по HTTP webhook и cron; настроить error path / DLQ.

## Подготовка

Workflow должен быть **ACTIVE**.

## A. Webhook

| Переменная | Пример |
|------------|--------|
| `webhookSlug` | `lab-alarm-hook` |
| `status` | `ACTIVE` |

```bash
curl -s -X POST "$BASE/api/v1/webhooks/workflows/lab-alarm-hook" \
  -H 'Content-Type: application/json' \
  -d '{"alarmId":"A-9","severity":"high"}' | jq .
```

Проверьте новый run в `GET .../by-path/runs`.

## B. Cron

| Переменная | Пример |
|------------|--------|
| `cronExpression` | `every:1m` |
| `status` | `ACTIVE` |

Через 1–2 минуты появятся периодические запуски. На demostand — лёгкий BPMN (`log`). Текущий формат — shorthand `every:…` ([workflows](workflows.md)).

## C. Failure recovery

| Переменная | Пример |
|------------|--------|
| `errorWorkflowPath` | `root.platform.workflows.error-handler` |
| `retryMaxAttempts` | `2` |
| `retryBackoffSeconds` | `30` |

При FAILED — journal failed instance; записи DLQ в `workflow_dead_letters`.
Список: `GET /api/v1/workflows/by-path/dead-letters?path=...&unresolvedOnly=true`.
Resolve: `POST /api/v1/workflows/dead-letters/{id}/resolve`. Async retry scheduler ещё не подключён.

## Проверка

- [ ] Webhook создаёт run с полями payload
- [ ] Cron даёт периодические runs при ACTIVE
- [ ] Ошибка видна в journal; error path / DLQ по конфигу

## Дальше

[Workflow как tool](tutorial-ot-workflow-as-tool.md)
