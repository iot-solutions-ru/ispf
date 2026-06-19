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
2. Запись в `event_history`
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

### Пример

Объект: `demo-sensor-01`, watch: `alarmActive`, условие: `value == true`, событие: `thresholdExceeded`.

`AlertRuleListener` реагирует на `VARIABLE_UPDATED`.

### API

CRUD: `/api/v1/alert-rules` (только **admin**).

UI: вкладка **Автоматизация** → Alert Rules.

Bootstrap: `AlertRuleBootstrap` создаёт демо-правило при старте.

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
| `actionType` | `RUN_WORKFLOW` |
| `actionTarget` | Path workflow |
| `enabled` | Вкл/выкл |

### Пример SEQUENCE

A = `thresholdExceeded`, B = `thresholdExceeded` (повтор), window = 60s → запуск `demo-alarm-handler`.

`EventCorrelatorListener` на `EVENT_FIRED`. Хиты в таблице `correlator_hits`.

### API

CRUD: `/api/v1/correlators` (только **admin**).

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
