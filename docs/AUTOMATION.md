# Автоматизация: события, алерты, корреляторы

## События

### Descriptor

На объекте задаётся `EventDescriptor`: имя, схема payload, уровень.

Уровни (`EventLevel`): `DEBUG`, `INFO`, `WARNING`, `ERROR`, `CRITICAL`.

### Публикация

```http
POST /api/v1/events/fire
```

1. Валидация по descriptor объекта
2. Запись в `event_history` (Timescale hypertable на prod — [0015](decisions/0015-event-history-timescale.md))
3. `ObjectChangeEvent` → WebSocket
4. Слушатели: correlators, UI

### Журнал

```http
GET /api/v1/events?objectPath=root.platform.devices.demo-sensor-01&limit=50
```

Без `objectPath` — глобальный журнал (до 200 записей).

### Демо

`mqtt-sensor-v1` → событие `thresholdExceeded` при превышении порога (через alert rule или ручной fire).

---

## Хранение в дереве объектов

Alert rules и event correlators — **узлы дерева** под `root.platform`, как dashboards и workflows:

```
root.platform
├── root.platform.alert-rules
│   └── root.platform.alert-rules.temperature-threshold-exceeded
└── root.platform.correlators
    ├── root.platform.correlators.alarm-handler-on-threshold-event
    └── root.platform.correlators.threshold-then-alarm-active-sequence-demo
```

| Сущность | Тип узла | Модель | Папка |
|----------|----------|--------|-------|
| Alert rule | `ALERT` | `alert-rule-v1` | `root.platform.alert-rules` |
| Correlator | `CORRELATOR` | `correlator-v1` | `root.platform.correlators` |

Конфигурация хранится в переменных объекта (`objectPath`, `watchVariable`, `conditionExpr`, …). Сервис `AutomationTreeService` читает и пишет узлы; при старте `AutomationBootstrap` мигрирует legacy-строки из таблиц `alert_rules` / `event_correlators` (если есть) и создаёт демо-правила.

UI: **Обозреватель** → выберите узел в `alert-rules` или `correlators` → inspector (`AlertRuleInspector`, `CorrelatorInspector`). Создание — контекстное меню дерева или `POST /api/v1/alert-rules` / `POST /api/v1/correlators`.

---

## Alert Rules

CEL-правило на изменение переменной. При истинности условия — fire события.

### Поля

| Поле | Описание |
|------|----------|
| `objectPath` | Объект |
| `watchVariable` | Имя переменной |
| `conditionExpr` | CEL (контекст: поля переменной) |
| `eventName` | Имя события для fire |
| `payloadVariable` | Переменная для payload (опц.) |
| `enabled` | Вкл/выкл |
| `edgeTrigger` | Только на фронте false→true |
| `notificationWebhookUrl` | URL для HTTP POST при срабатывании (опц.) |
| `notificationEmailTarget` | Email: `to@host\|subject\|body` (опц., нужен relay) |

При fire события, если задан webhook и/или email — дополнительно вызывается `NotificationDispatchService` (ошибки логируются, alert не блокируется).

### Пример

Объект: `demo-sensor-01`, watch: `alarmActive`, условие: `self.alarmActive["value"] == true`, событие: `thresholdExceeded`.

`AlertRuleListener` реагирует на `VARIABLE_UPDATED`.

### API

| Method | Path | Роли |
|--------|------|------|
| GET | `/api/v1/alert-rules` | admin |
| GET | `/api/v1/alert-rules/by-path?path=` | admin |
| POST | `/api/v1/alert-rules` | admin |
| PUT | `/api/v1/alert-rules/by-path?path=` | admin |
| DELETE | `/api/v1/alert-rules/by-path?path=` | admin |

---

## Event Correlators

Агрегация нескольких событий → действие (запуск workflow).

### Паттерны (`CorrelatorPatternType`)

| Тип | Описание |
|-----|----------|
| `COUNT` | N событий `eventName` за `windowSeconds` |
| `SEQUENCE` | Событие A, затем B на том же объекте |

### Поля

| Поле | Описание |
|------|----------|
| `name` | Имя |
| `patternType` | COUNT / SEQUENCE |
| `eventName` | Первое событие (A) |
| `secondEventName` | Второе (для SEQUENCE) |
| `windowSeconds` | Окно наблюдения |
| `minOccurrences` | Порог (COUNT) |
| `cooldownSeconds` | Пауза после срабатывания |
| `actionType` | См. таблицу ниже |
| `actionTarget` | Зависит от `actionType` |
| `enabled` | Вкл/выкл |

### Типы действий (`CorrelatorActionType`)

| `actionType` | `actionTarget` | Описание |
|--------------|----------------|----------|
| `RUN_WORKFLOW` | path workflow | Запуск BPMN |
| `FIRE_EVENT` | имя события | `EventService.fire` на объекте |
| `SET_VARIABLE` | `varName=value` | Запись переменной |
| `OPEN_OPERATOR_REPORT` | path отчёта | Событие `openOperatorReport` |
| `SEND_WEBHOOK` | URL | HTTP POST JSON (`NotificationDispatchService`) |
| `SEND_EMAIL` | `to\|subject\|body` | POST на email relay (см. конфиг ниже) |

### Пример SEQUENCE

A = `thresholdExceeded`, B = `thresholdExceeded` (повтор), window = 60s → запуск `demo-alarm-handler`.

`EventCorrelatorListener` на `EVENT_FIRED`. Хиты в таблице `correlator_hits`.

### API

| Method | Path | Роли |
|--------|------|------|
| GET | `/api/v1/correlators` | admin |
| GET | `/api/v1/correlators/by-path?path=` | admin |
| POST | `/api/v1/correlators` | admin |
| PUT | `/api/v1/correlators/by-path?path=` | admin |
| DELETE | `/api/v1/correlators/by-path?path=` | admin |

---

## Каналы уведомлений (BL-44)

`NotificationDispatchService` — HTTP webhook и опциональный email relay для alert rules и correlator actions.

### Конфигурация

| Свойство | Env (relaxed binding) | Описание |
|----------|----------------------|----------|
| `ispf.notifications.email-relay-url` | `ISPF_NOTIFICATIONS_EMAIL_RELAY_URL` | HTTP endpoint relay: принимает JSON `{ "to", "subject", "body", ...context }` |
| `ispf.notifications.timeout-seconds` | `ISPF_NOTIFICATIONS_TIMEOUT_SECONDS` | Таймаут HTTP (по умолчанию 15) |

Без `email-relay-url` действие `SEND_EMAIL` и email в alert rule завершатся ошибкой в логе; webhook работает без relay.

### Payload webhook / email context

Базовые поля (`NotificationDispatchService.baseContext`):

```json
{
  "source": "alert-rule | correlator",
  "sourceId": "<path узла правила/коррелятора>",
  "objectPath": "<объект, на котором сработало>",
  "eventName": "<имя события>",
  "timestamp": "2026-06-28T12:00:00Z"
}
```

### Где настраивать

| Источник | UI | Переменные / поля |
|----------|-----|-------------------|
| Alert rule | Explorer → `alert-rules` → inspector | `notificationWebhookUrl`, `notificationEmailTarget` |
| Correlator | Explorer → `correlators` → inspector | `actionType` = `SEND_WEBHOOK` / `SEND_EMAIL`, `actionTarget` |

API alert rules принимает те же поля в `POST/PUT` body (`CreateAlertRuleRequest` / `UpdateAlertRuleRequest`).

---

## CEL в автоматизации

| Место | Контекст |
|-------|----------|
| Variable binding | `self.var.field` |
| Alert rule | поля watch-переменной |
| Workflow gateway | переменные instance |
| Expression validate API | произвольная схема |

Валидация: `POST /api/v1/expressions/validate`.

---

## Operator HMI

В режиме `?mode=operator`:

- **EventJournalPanel** — live-лента событий
- **WorkQueuePanel** — задачи из user tasks

Виджеты дашборда `event-feed` и `work-queue` дублируют функциональность на HMI-экране.
