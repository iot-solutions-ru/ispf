> **Язык:** русская версия. Английский оригинал: [en/messaging.md](../en/messaging.md).

# Шины сообщений и каналы обмена данными

> **Статус:** Stable — назначение каналов REST/BFF, WebSocket, журнала событий, NATS и MQTT. Метки зрелости: [doc-status.md](doc-status.md).

В ISPF каждый канал связи решает свою задачу: синхронные команды и запросы идут через HTTP REST/BFF, живые обновления экранов — через WebSocket, а межсерверный обмен и очереди кластера — через NATS и Redis.

---

## 1. Какой канал когда использовать

| Канал | Характер обмена | Типичное применение |
|-------|-----------------|---------------------|
| **REST API и шлюз BFF** (`/api/v1/**`) | Синхронный запрос — ответ | Чтение и изменение дерева объектов, вызов прикладных функций из UI (`POST /api/v1/bff/invoke`), установка пакетов `bundle.json`, построение отчётов |
| **WebSocket** (`/ws/objects`) | Серверная рассылка изменений в реальном времени | Живое обновление виджетов на экранах оператора, мнемосхем SCADA и дерева объектов в консоли |
| **Журнал событий (БД)** | Долговременный архив с гарантированным сохранением | Лента событий и тревог оператора, расследование инцидентов, вход для корреляторов |
| **Шина NATS** (`ispf.events.*`, `ispf.object.*`) | Асинхронная публикация и подписка между серверами | Синхронизация реплик кластера, интеграция с внешними системами, шаг `publish_nats` в процессах BPMN |

---

## 2. Подписка браузера по WebSocket (`/ws/objects`)

Подключение открывается по адресу `GET /ws/objects` с передачей токена в заголовке `Sec-WebSocket-Protocol: ispf-bearer, <токен>`.

### Подписка на все переменные объекта

```json
{ "type": "subscribe", "paths": ["root.platform.devices.demo-sensor-01"] }
```

### Выборочная подписка только на нужные переменные (рекомендуется для экранов HMI)

Чтобы не пересылать в браузер лишние поля объекта, укажите конкретный список переменных в `variablesByPath`:

```json
{
  "type": "subscribe",
  "paths": ["root.platform.devices.demo-sensor-01"],
  "variablesByPath": {
    "root.platform.devices.demo-sensor-01": ["temperature", "online"]
  }
}
```

- Если для пути не указан список в `variablesByPath`, браузер получает уведомления по **всем** переменным этого узла.
- Если передать пустой массив `paths: []`, подписка снимается (сервер никогда не рассылает изменения всего дерева без явного списка путей).

### Подписка на прикладные события пакета (`subscribe_events`)

```json
{ "type": "subscribe_events", "appId": "mes-reference", "events": ["mesRackOverTemp"] }
```

Сервер проверяет права текущего пользователя по секции `events[]` манифеста приложения и возвращает список подтверждённых и отклонённых событий в сообщении `subscribe_events_result` (`accepted[]`, `rejected[]`).

### Формат входящего уведомления об изменении переменной

```json
{
  "type": "VARIABLE_UPDATED",
  "path": "root.platform.devices.demo-sensor-01",
  "variableName": "temperature",
  "timestamp": "2026-06-22T12:00:00Z"
}
```

---

## 3. Темы (subjects) шины NATS (`ispf.nats.enabled=true`)

| Шаблон темы NATS | Кто публикует | Кто читает | Содержимое сообщения |
|------------------|---------------|------------|----------------------|
| `ispf.object.<путь>.<тип_изменения>` | `NatsEventBridge` | Внешние системы интеграции | `path`, `type`, `variableName`, `timestamp`, `source` |
| `ispf.events.<тип_изменения>` | `NatsEventBridge` | Соседние реплики кластера (`NatsObjectChangeSubscriber`) | Те же поля + при включённой синхронизации [ADR-0029](decisions/0029-cluster-live-variable-replica-sync.md) снимок значения `value` |
| `ispf.workflow.<путь_процесса>.<событие>` | `WorkflowService` | Внешние подписчики и аналитика | `workflowPath`, `event`, `payload` |
| Произвольная тема из шага BPMN `publish_nats` | Движок процессов BPMN | Внешние сервисы | Тема и тело сообщения, заданные в процессе |

Основные параметры в `application.yml`: `ispf.nats.url`, `ispf.nats.replica-id`, `ispf.nats.replica-events-enabled`.

### Гарантированная доставка между репликами через NATS JetStream

Если включён параметр `ispf.nats.jet-stream-enabled=true` (требует `ispf.nats.enabled=true`):

| Параметр | Переменная окружения | По умолчанию |
|----------|----------------------|--------------|
| Имя потока (stream) | `ISPF_NATS_JETSTREAM_STREAM` | `ispf-automation` |
| Срок хранения сообщений в потоке | `ISPF_NATS_JETSTREAM_MAX_AGE_HOURS` | `24` (часа) |
| Префикс долговременного подписчика | `ISPF_NATS_JETSTREAM_CONSUMER_PREFIX` | `ispf-replica-` |

В этом режиме межрепликовые события `ispf.events.>` идут через поток JetStream, а каждая реплика создаёт долговременную подписку (`ispf-replica-<replicaId>`), что позволяет узлу дочитать пропущенные структурные изменения после кратковременного перезапуска. Проверить состояние соединения и очередей JetStream можно в разделе **Система → Метрики** (карточка NATS) или запросом `GET /api/v1/platform/nats/health`.

### Хранение скользящих окон корреляторов в Redis

Если включены `ispf.redis.enabled=true` и `ispf.redis.correlator-windows-enabled=true` (`ISPF_REDIS_CORRELATOR_WINDOWS=true`), буферы скользящих окон корреляторов (`COUNT`, `SEQUENCE`, `EVENT_CHAIN`) хранятся в сортированных множествах Redis вместо таблиц PostgreSQL. Это ускоряет обработку высокочастотных цепочек событий и делает состояние окон общим для реплик кластера. Статус Redis доступен по адресу `GET /api/v1/platform/redis/health`.

---

## 4. Каталог событий прикладного решения (`events[]` в `bundle.json`)

В манифесте приложения можно объявить типизированный каталог прикладных событий и роли, которым разрешена подписка на них:

```json
"events": [
  { "id": "mesRackOverTemp", "roles": ["admin"] },
  { "id": "mesOrderUpdated", "roles": ["operator", "admin"] }
]
```

- При установке пакета список сохраняется в таблицу `application_event_catalog` (доступен через `GET /api/v1/applications/{appId}/events`).
- При публикации события через `POST /api/v1/events/fire?appId={appId}` сервер проверяет соответствие полезной нагрузки схеме `payloadSchema`.

---

## 5. Подключение внешних систем к шине NATS

Внешние сервисы могут подписываться на события дерева объектов напрямую в брокере NATS (`NATS_URL=nats://ispf-nats:4222`):

| Шаблон подписки | Что получает подписчик |
|-----------------|------------------------|
| `ispf.object.>` | Все изменения объектов дерева (`VARIABLE_UPDATED`, создание/удаление узлов и др.) |
| `ispf.object.root.platform.devices.*.VARIABLE_UPDATED` | Только обновления переменных устройств в папке `devices` |
| `ispf.workflow.>` | События запуска и завершения процессов BPMN |

Пример сообщения в теме `ispf.object.*`:

```json
{
  "path": "root.platform.devices.demo-sensor-01",
  "type": "VARIABLE_UPDATED",
  "variableName": "temperature",
  "timestamp": "2026-06-22T12:00:00Z",
  "source": "platform"
}
```

При включённой синхронизации живых значений в кластере (`ispf.cluster.live-variable-sync-enabled=true`) в тему `ispf.events.variable_updated` также передаётся полное значение переменной `value` (`DataRecord` со схемой и строками).

**Рекомендации для внешних подписчиков:**
1. Обрабатывайте сообщения идемпотентно по ключу `(path, variableName, timestamp)`.
2. Для чтения полного состояния объекта после уведомления используйте `GET /api/v1/objects/by-path`.
3. Быстрая проверка из командной строки: `nats sub 'ispf.object.>'`.

Связанные разделы: [Публичный контракт разработчика решений](solution-developer-public-api.md) · [Автоматизация](automation.md) · [Федерация серверов](federation.md) · [Приложения](applications.md).
