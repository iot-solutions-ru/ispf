> **Язык:** русская версия (вычитка). Канонический английский: [en/reference-escalation-templates.md](../en/reference-escalation-templates.md).

# Шаблоны workflow эскалации (BL-123)

Эталонные паттерны для цепочки **коррелятор событий → workflow → задача оператора → эскалация по тайм-ауту** в ISPF без custom Java.

Артефакты: [examples/escalation-templates/](../examples/escalation-templates/).

## Паттерн A — повторяющийся порог → workflow

| Шаг | Компонент | Конфигурация |
|------|-----------|--------|
| 1 | Alert rule (optional) | CEL по переменной → событие `thresholdExceeded` |
| 2 | Event correlator | COUNT: N событий в окне → `RUN_WORKFLOW` |
| 3 | Workflow | User task + параллельное уведомление (см. `demo-alarm-handler`) |

Шаблон JSON: `recurring-threshold-correlator.json`.

Bootstrap (fixtures): коррелятор **Recurring threshold escalation** на `root.platform.devices.demo-sensor-01` → `root.platform.workflows.demo-alarm-handler`.

Приёмка: `EscalationChainAcceptanceTest` — 3× `thresholdExceeded` → элемент work queue «Подтвердите тревогу».

## Паттерн B — тайм-аут подтверждения с boundary timer (BL-122)

Если оператор должен подтвердить тревогу в рамках SLA, прикрепите к user task **boundary timer**:

```xml
<userTask id="operatorAck" ispf:title="Подтвердите тревогу" .../>
<boundaryEvent id="ackTimeout" attachedToRef="operatorAck"
               cancelActivity="true" ispf:durationSeconds="300"/>
<sequenceFlow sourceRef="ackTimeout" targetRef="escalate"/>
```

Полный BPMN: `examples/escalation-templates/ack-timeout-escalation.bpmn.xml`.

| Фаза | Статус экземпляра | Действие |
|-------|-----------------|--------|
| После старта | `WAITING` @ user task | Оператор видит элемент work queue |
| Оператор завершает вовремя | `COMPLETED` | Обычный путь закрытия |
| Срабатывает таймер | `COMPLETED` | Путь эскалации (NATS + log) |

Запустите просроченный таймер вручную или из планировщика:

```http
POST /api/v1/workflows/instances/{instanceId}/timer
{"operatorId":"operator-1"}
```

## Паттерн C — пробуждение по signal (существующий)

Для внешних систем (CMMS, тикетинг) используйте **signal catch** вместо таймера:

```xml
<intermediateCatchEvent id="waitTicket" ispf:signal="ticketClosed"/>
```

```http
POST /api/v1/workflows/instances/{instanceId}/signal
{"signal":"ticketClosed","operatorId":"operator-1"}
```

## Комбинация паттернов

Типичная production-цепочка:

1. Коррелятор запускает workflow при повторных тревогах.
2. Workflow логирует и уведомляет ops (parallel gateway).
3. User task с **boundary timer** для SLA подтверждения.
4. Ветка эскалации публикует в NATS / вызывает BFF-функцию для пейджинга супервизора.

## CI

`EscalationTemplateSmokeTest` разбирает `ack-timeout-escalation.bpmn.xml` и проверяет путь эскалации по boundary timer.

См. также: [WORKFLOWS.md](workflows.md), [AUTOMATION.md](automation.md).
