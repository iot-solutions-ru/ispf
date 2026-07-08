> **Язык:** русская версия (вычитка). Канонический английский: [en/automation.md](../en/automation.md).

# Автоматизация: события, оповещения, корреляторы

## События

### Дескриптор

На объекте задаётся `EventDescriptor`: имя, схема payload, уровень.

Уровни (`EventLevel`): `DEBUG`, `INFO`, `WARNING`, `ERROR`, `CRITICAL`.

###Публикация

**API (ручной огонь, любители):**

```http
POST /api/v1/events/fire
```

**Вход драйвера (высокая частота, без HTTP):** при `telemetryPublishMode=EVENT_JOURNAL_ONLY` каждом обновлении переменной от драйвера возникает `EventService.fireIngress` → асинхронный журнал. Событие задаётся `ingressEventName` в конфигурации драйвера (по умолчанию `messageReceived`). См. [ADR-0027](decisions/0027-event-journal-ingress-fast-path.md).

Общий горячий путь после валидации дескриптора:

1. Запись в `event_history` (асинхронный пакет — Timescale [0015](decisions/0015-event-history-timescale.md) или ClickHouse [0016](decisions/0016-clickhouse-event-journal.md))
2. `ObjectChangeEvent` → WebSocket
3. Слушатели: корреляторы, интерфейс.

### Журнал

```http
GET /api/v1/events?objectPath=root.platform.devices.demo-sensor-01&limit=50
```

Без `objectPath` — глобальный журнал (до 200 записей).

### Демо

`mqtt-sensor-v1` → событие `thresholdExceeded` при превышении порога (через правило оповещения или ручной огонь); `messageReceived` — для loadtest/audit ingress ([0027](decisions/0027-event-journal-ingress-fast-path.md)).

---

## Хранение в дереве объектов

Правила оповещений и корреляторы событий — **узлы дерева** под `root.platform`, как дашборды и рабочие процессы:

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

Конфигурация сохраняется в объекте запроса (`objectPath`, `watchVariable`, `conditionExpr`, …). Сервис `AutomationTreeService` читает и записывает узлы; при старте `AutomationBootstrap` перемещает Legacy-строки из таблиц `alert_rules` / `event_correlators` (если есть) и создаёт демо-правила.

Пользовательский интерфейс: **Обозреватель** → выберите узел в `alert-rules` или `correlators` → инспектор (`AlertRuleInspector`, `CorrelatorInspector`). Создание — контекстное меню дерева или `POST /api/v1/alert-rules` / `POST /api/v1/correlators`.

---

## Правила оповещений

CEL-правило изменения переменной. При истинности условий — пожарные события.

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

При возникновении пожарных событий, если задан вебхук и/или адрес электронной почты — дополнительно возникает `NotificationDispatchService` (ошибки регистрируются, оповещение не блокируется).

### Пример

Объект: `demo-sensor-01`, watch: `alarmActive`, условие: `self.alarmActive["value"] == true`, событие: `thresholdExceeded`.

`AlertRuleListener` реагирует на `VARIABLE_UPDATED`.

### API

| Метод | Путь | Роли |
|--------|------|------|
| GET | `/api/v1/alert-rules` | admin |
| GET | `/api/v1/alert-rules/by-path?path=` | admin |
| POST | `/api/v1/alert-rules` | admin |
| PUT | `/api/v1/alert-rules/by-path?path=` | admin |
| DELETE | `/api/v1/alert-rules/by-path?path=` | admin |

---

## Корреляторы событий

Агрегация нескольких событий → действие (запуск рабочего процесса).

### Паттерны (`CorrelatorPatternType`)

| Тип | Описание |
|-----|----------|
| `COUNT` | N событий `eventName` за `windowSeconds` |
| `SEQUENCE` | Событие A, затем B на том же объекте |
| `WINDOW` | Набор событий (A + список в `secondEventName`) — каждое хотя бы раз за `windowSeconds` |

###ОКНО (BL-171)

Шаблон **неупорядоченного окна**: все перечисленные события должны происходить на одном объекте внутри `windowSeconds`, порядок не важен.

| Поле | Роль для ОКНА |
|------|-----------------|
| `eventName` | Первое обязательное событие |
| `secondEventName` | Дополнительные события через запятую (напр. `workOrderReleased,workOrderStarted`) |
| `windowSeconds` | Ширина скользящего окна (> 0) |
| `minOccurrences` | Не используется (оставьте 1) |

Пример: MES dispatch — `workOrderCreated` + `workOrderReleased` + `workOrderStarted` за 120 с на `mes-platform-hub` → `RUN_WORKFLOW`.

`EventCorrelatorService.processWindowPattern` записывает каждое попадание в магазин окон и реализует, когда множество уникальных событий соответствует требуемому набору.

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

### Пример ПОСЛЕДОВАТЕЛЬНОСТЬ

A = `thresholdExceeded`, B = `thresholdExceeded` (повтор), window = 60s → запуск `demo-alarm-handler`.

`EventCorrelatorListener` на `EVENT_FIRED`. Хиты в таблице `correlator_hits`.

### API

| Метод | Путь | Роли |
|--------|------|------|
| GET | `/api/v1/correlators` | admin |
| GET | `/api/v1/correlators/by-path?path=` | admin |
| POST | `/api/v1/correlators` | admin |
| PUT | `/api/v1/correlators/by-path?path=` | admin |
| DELETE | `/api/v1/correlators/by-path?path=` | admin |

---

## Каналы уведомлений (BL-44)

`NotificationDispatchService` — веб-перехватчик HTTP и дополнительная ретрансляция электронной почты для правил оповещений и действий коррелятора.

### Конфигурация

| Свойство | Env (слабая привязка) | Описание |
|----------|----------------------|----------|
| `ispf.notifications.email-relay-url` | `ISPF_NOTIFICATIONS_EMAIL_RELAY_URL` | HTTP endpoint relay: принимает JSON `{ "to", "subject", "body", ...context }` |
| `ispf.notifications.timeout-seconds` | `ISPF_NOTIFICATIONS_TIMEOUT_SECONDS` | Таймаут HTTP (по умолчанию 15) |

Без действия `email-relay-url` `SEND_EMAIL` и электронная почта в правиле оповещения завершатся ошибкой в ​​логе; вебхук работает без реле.

### Вебхук полезной нагрузки/контекст электронной почты

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

### Где настройки

| Источник | Пользовательский интерфейс | Переменные / поля |
|----------|-----|-------------------|
| Alert rule | Explorer → `alert-rules` → inspector | `notificationWebhookUrl`, `notificationEmailTarget` |
| Correlator | Explorer → `correlators` → inspector | `actionType` = `SEND_WEBHOOK` / `SEND_EMAIL`, `actionTarget` |

API alert rules принимает те же поля в `POST/PUT` body (`CreateAlertRuleRequest` / `UpdateAlertRuleRequest`).

---

## CEL в автоматизации

| Место | Контекст |
|-------|----------|
| Variable binding | `self.var.field` |
| Правило оповещения | поля часы-переменной |
| Шлюз рабочего процесса | переменный экземпляр |
| API проверки выражений | произвольная схема |

Валидация: `POST /api/v1/expressions/validate`.

---

## HMI оператора

В режиме `?mode=operator`:

- **EventJournalPanel** — события в прямом эфире
- **WorkQueuePanel** — задачи из пользовательских задач.

Виджеты дашборда `event-feed` и `work-queue` дублируют функциональность на HMI-экране.
