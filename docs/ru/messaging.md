> **Язык:** русская версия (вычитка). Канонический английский: [en/messaging.md](../en/messaging.md).

# Контракт обмена сообщениями (ISPF)

> **Статус:** Stable — NATS / MQTT notes. Хаб: [doc-status.md](doc-status.md).

Контракт шин и синхронных вызовов платформы ↔ решение. REQ-FW-32.

## Принцип

| Канал | Семантика | Когда использовать |
|-------|-----------|-------------------|
| **Вызов REST/BFF** | Синхронный request/response | CRUD, функции приложения, развертывание, отчёты |
| **WebSocket `/ws/objects`** | Push изменений дерева объектов | Live HMI, проводник, обновление переменных |
| **Журнал событий (БД)** | Надежный асинхронный журнал | Лента событий оператора, аудит, корреляторы |
| **NATS** | Асинхронная публикация/подписка между репликами | Разветвление реплики, побочные эффекты рабочего процесса |

Не смешивайте sync RPC и async-шину в одном «универсальном» канале.

## Дерево объектов WebSocket

| Конечная точка | Назначение |
|----------|------------|
| `GET /ws/objects` + `Sec-WebSocket-Protocol: ispf-bearer, <token>` | Subscribe paths, presence, **event catalog** |

### Сообщения клиента

```json
{ "type": "subscribe", "paths": ["root.platform.devices.demo-sensor-01"] }

Узкий интерес (рекомендуется для HMI-виджетов) — только перечисленные переменные.
Отсутствие `variablesByPath` для path = path-wide (все переменные объекта).
Пустой `paths` = тишина (не broadcast на весь сервер).

```json
{
  "type": "subscribe",
  "paths": ["root.platform.devices.demo-sensor-01"],
  "variablesByPath": {
    "root.platform.devices.demo-sensor-01": ["temperature", "online"]
  }
}
```
```

```json
{ "type": "subscribe_events", "appId": "mes-reference", "events": ["mesRackOverTemp"] }
```

Ответ `subscribe_events_result`: `accepted[]`, `rejected[]` (роли из bundle `events[]` — FW-31).

### Отправка изменения объекта

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
| `ispf.object.{path}.{changeType}` | `NatsEventBridge` | внешние интеграторы | path, type, variableName, timestamp, source |
| `ispf.events.{changeType}` | `NatsEventBridge` (replica fan-out) | `NatsObjectChangeSubscriber` | same |
| `ispf.events.{changeType}` (JetStream) | `NatsEventBridge` когда `jet-stream-enabled` | durable consumer `NatsObjectChangeSubscriber` | та же payload в stream `ispf-automation` |
| `ispf.workflow.{workflowPath}.{event}` | `WorkflowService` | external / analytics | workflowPath, event, payload |
| BPMN `publish_nats` task | workflow engine | custom | task-defined subject + message |

Конфигурация: `ispf.nats.url`, `ispf.nats.replica-id`, `ispf.nats.replica-events-enabled`.

### JetStream (необязательно, 0014)

When `ispf.nats.jet-stream-enabled=true` (requires `ispf.nats.enabled=true`):

| Настройка | Окружение | По умолчанию |
|---------|-----|---------|
| Stream name | `ISPF_NATS_JETSTREAM_STREAM` | `ispf-automation` |
| Retention | `ISPF_NATS_JETSTREAM_MAX_AGE_HOURS` | `24` |
| Consumer prefix | `ISPF_NATS_JETSTREAM_CONSUMER_PREFIX` | `ispf-replica-` |

Разветвление реплик использует публикацию/подписку JetStream вместо основного NATS для `ispf.events.>` субъектов. Надежные потребители для каждой реплики (`ispf-replica-{replicaId}`) позволяют наверстывать упущенное после перезапуска. Субъекты объектного уровня (41) остаются основными NATS для внешних интеграторов.

**Операции:** текущий статус (соединение, потоковые сообщения/байты, ожидание потребителя, доступность `PUBLISH_NATS`) — **Система → Метрики** → Карта NATS и JetStream; API `GET /api/v1/platform/nats/health`.

### Окна коррелятора Redis (необязательно, 0014)

When `ispf.redis.enabled=true` and `ispf.redis.correlator-windows-enabled=true`:

| Настройка | Окружение | По умолчанию |
|---------|-----|---------|
| Correlator windows | `ISPF_REDIS_CORRELATOR_WINDOWS` | `false` |

Состояние коррелятора скользящего окна (обращения COUNT / SEQUENCE / EVENT_CHAIN) хранится в отсортированных наборах Redis (48) вместо PostgreSQL 49. Включает общие окна для реплик; по умолчанию остается JDBC для развертываний с одним узлом.

**Операции:** текущий статус (соединение, серверная часть хранилища коррелятора, серверная часть кэша ACL, TTL, количество ключей) — **Система → Метрики** → Карта Redis; API `GET /api/v1/platform/redis/health`.

## Синхронизация RPC (основной API)

| API | Примеры |
|-----|---------|
| `POST /api/v1/applications/{appId}/deploy` | Bundle deploy |
| `POST /api/v1/bff/invoke` | Application functions |
| `POST /api/v1/objects/by-path/functions/invoke` | Tree functions |
| Federation proxy | `GET /api/v1/federation/proxy/...` |

Прокси федерации — синхронизация чтения через HTTP; не замена событийного автобуса.

## Каталог событий в комплекте (FW-31)

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
- `POST /api/v1/events/fire?appId={appId}` — дополнительная **проверка времени стрельбы** `payloadSchema` из каталога (FW-31); уровень объекта `EventDescriptor.payloadSchema` применяется всегда

## Внешние потребители NATS

Для интеграции внешних систем с event bus platform (при `ispf.nats.enabled=true`):

### Подключение

```text
NATS_URL=nats://ispf-nats:4222   # тот же кластер, что ispf.nats.url
```

Подписка — **core NATS** (не JetStream по умолчанию). Аутентификация NATS — на уровне сети/VPN; платформа не добавляет JWT в тему.

### Рекомендуемые тематические шаблоны

| Узор | Когда подписывать |
|---------|---------------------|
| `ispf.object.>` | Все object change (VARIABLE_UPDATED, …) |
| `ispf.object.root.platform.devices.*.VARIABLE_UPDATED` | Узкий фильтр по устройствам |
| `ispf.workflow.>` | BPMN lifecycle side-effects |
| Custom из `publish_nats` task | Subject из workflow definition |

### Полезная нагрузка (изменение объекта)

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

### Live variable replica sync (ADR-0029, `ispf.cluster.live-variable-sync-enabled=true`)

Разветвление реплики `ispf.events.variable_updated` может включать полноценный снимок (объединенный по владельцу с `ispf.cluster.live-variable-sync-coalesce-ms`, по умолчанию 500 мс):

```json
{
  "type": "VARIABLE_UPDATED",
  "path": "root.platform.devices.snmp-router-01",
  "variableName": "ifInOctets",
  "timestamp": "2026-07-05T12:00:00Z",
  "source": "replica-2",
  "observedAt": "2026-07-05T12:00:00.123Z",
  "value": {
    "schema": { "name": "ifInOctets", "fields": [{ "name": "value", "type": "DOUBLE" }] },
    "rows": [{ "value": 1234567890 }]
  }
}
```

Реплики подчиненных применяются `value` к локальной оперативной памяти (`ClusterVariableReplicaApplier`); сообщения без `value` сохраняют устаревшее поведение только для уведомлений.

Интерес к пути WebSocket в масштабе всего кластера (Redis, `ispf.cluster.cluster-path-interest-enabled`) гарантирует, что владельцы драйверов будут публиковать, когда клиенты пользовательского интерфейса подписываются на любую реплику.

### Практика потребительская

1. Один устойчивый потребительский процесс на интеграцию; идемпотентная обработка по `(path, variableName, timestamp)`.
2. Не блокировать платформу — тяжёлая обработка в очереди своей/рабочей.
3. Чтение после записи для консистентности — `GET /api/v1/objects/by-path` (синхронизация), не ожидается полная фотография из NATS.
4. Мероприятия Федерации не дублируются в хабе NATS автоматически — только локальные изменения инстанса.

Пример (nats-cli): `nats sub 'ispf.object.>'`

## Зависимости пакета (FW-12)

```json
"requires": [
  { "appId": "warehouse", "minVersion": "1.0.0" }
]
```

Затем разверните активную версию зависимого пакета и примените.

## Связанные документы

- [solution-developer-public-api](solution-developer-public-api.md)
- [automation](automation.md)
- [federation](federation.md)
- [applications](applications.md)
