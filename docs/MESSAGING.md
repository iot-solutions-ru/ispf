# Messaging contract (ISPF)

Контракт шин и синхронных вызовов platform ↔ solution. REQ-FW-32.

## Принцип

| Канал | Семантика | Когда использовать |
|-------|-----------|-------------------|
| **REST / BFF invoke** | Sync request/response | CRUD, функции приложения, deploy, отчёты |
| **WebSocket `/ws/objects`** | Push object tree changes | Live HMI, explorer, variable updates |
| **Event journal (DB)** | Durable async log | Operator event feed, audit, correlators |
| **NATS** | Async pub/sub между репликами | Replica fan-out, workflow side-effects |

Не смешивать sync RPC и async bus в одном «универсальном» канале.

## WebSocket object tree

| Endpoint | Назначение |
|----------|------------|
| `GET /ws/objects?token=...` | Subscribe paths, presence, **event catalog** |

### Сообщения клиента

```json
{ "type": "subscribe", "paths": ["root.platform.devices.demo-sensor-01"] }
```

```json
{ "type": "subscribe_events", "appId": "mes-reference", "events": ["mesRackOverTemp"] }
```

Ответ `subscribe_events_result`: `accepted[]`, `rejected[]` (роли из bundle `events[]` — FW-31).

### Object change push

```json
{
  "type": "VARIABLE_UPDATED",
  "path": "root.platform.devices.demo-sensor-01",
  "variableName": "temperature",
  "timestamp": "2026-06-22T12:00:00Z"
}
```

## NATS subjects (production, `ispf.nats.enabled=true`)

| Subject pattern | Publisher | Consumer | Payload |
|-----------------|-----------|----------|---------|
| `ispf.object.{path}.{changeType}` | `NatsEventBridge` | external integrators | path, type, variableName, timestamp, source |
| `ispf.events.{changeType}` | `NatsEventBridge` (replica fan-out) | `NatsObjectChangeSubscriber` | same |
| `ispf.workflow.{workflowPath}.{event}` | `WorkflowService` | external / analytics | workflowPath, event, payload |
| BPMN `publish_nats` task | workflow engine | custom | task-defined subject + message |

Конфигурация: `ispf.nats.url`, `ispf.nats.replica-id`, `ispf.nats.replica-events-enabled`.

## Sync RPC (primary API)

| API | Примеры |
|-----|---------|
| `POST /api/v1/applications/{appId}/deploy` | Bundle deploy |
| `POST /api/v1/bff/invoke` | Application functions |
| `POST /api/v1/objects/by-path/functions/invoke` | Tree functions |
| Federation proxy | `GET /api/v1/federation/proxy/...` |

Federation proxy — sync read через HTTP; не замена event bus.

## Event catalog в bundle (FW-31)

Секция `events[]` в manifest:

```json
"events": [
  { "id": "mesRackOverTemp", "roles": ["admin"] },
  { "id": "mesOrderUpdated", "roles": ["operator", "admin"] }
]
```

- Deploy загружает каталог в `application_event_catalog`
- `GET /api/v1/applications/{appId}/events` — список для solution developers
- WS `subscribe_events` проверяет роли (admin bypass)
- `POST /api/v1/events/fire?appId={appId}` — optional **fire-time** проверка `payloadSchema` из каталога (FW-31); object-level `EventDescriptor.payloadSchema` применяется всегда

## External NATS consumers

Для интеграции внешних систем с event bus platform (при `ispf.nats.enabled=true`):

### Подключение

```text
NATS_URL=nats://ispf-nats:4222   # тот же кластер, что ispf.nats.url
```

Подписка — **core NATS** (не JetStream по умолчанию). Аутентификация NATS — на уровне сети/VPN; platform не добавляет JWT на subject.

### Рекомендуемые subject patterns

| Pattern | Когда подписываться |
|---------|---------------------|
| `ispf.object.>` | Все object change (VARIABLE_UPDATED, …) |
| `ispf.object.root.platform.devices.*.VARIABLE_UPDATED` | Узкий фильтр по устройствам |
| `ispf.workflow.>` | BPMN lifecycle side-effects |
| Custom из `publish_nats` task | Subject из workflow definition |

### Payload (object change)

```json
{
  "path": "root.platform.devices.demo-sensor-01",
  "type": "VARIABLE_UPDATED",
  "variableName": "temperature",
  "timestamp": "2026-06-22T12:00:00Z",
  "source": "platform"
}
```

Поля стабильны для `NatsEventBridge`; не полагаться на недокументированные ключи.

### Практика consumer

1. Один durable consumer process на интеграцию; idempotent обработка по `(path, variableName, timestamp)`.
2. Не блокировать platform — тяжёлая обработка в своей очереди/worker.
3. Read-after-write для консистентности — `GET /api/v1/objects/by-path` (sync), не ожидать полного снимка из NATS.
4. Federation events не дублируются на hub NATS автоматически — только локальные изменения инстанса.

Пример (nats-cli): `nats sub 'ispf.object.>'`

## Bundle dependencies (FW-12)

```json
"requires": [
  { "appId": "warehouse", "minVersion": "1.0.0" }
]
```

Deploy проверяет активную версию зависимого bundle до apply.

## Связанные документы

- [SOLUTION_DEVELOPER_PUBLIC_API.md](SOLUTION_DEVELOPER_PUBLIC_API.md)
- [AUTOMATION.md](AUTOMATION.md)
- [FEDERATION.md](FEDERATION.md)
- [APPLICATIONS.md](APPLICATIONS.md)
