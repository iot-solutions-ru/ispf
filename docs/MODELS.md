# Models Plugin

Модуль `ispf-plugin-model` — система **моделей** (шаблонов структуры объектов).

## Типы моделей (`ModelType`)

| Тип | Поведение |
|-----|-----------|
| `RELATIVE` | Переменные/события/функции **вливаются** в существующий объект |
| `ABSOLUTE` | Отдельная ветка (singleton branch) |
| `INSTANCE` | Создание **дочернего экземпляра** по запросу |

При создании объекта с `templateId` автоматически применяются RELATIVE-модели, привязанные к типу.

## Состав модели (`ModelDefinition`)

- Переменные (`ModelVariableDefinition`) — schema, default, group, readable/writable
- События (`EventDescriptor`)
- Функции (`FunctionDescriptor`)
- Bindings (`ModelBindingDefinition`) — CEL или platform bindings; см. [BINDINGS.md](BINDINGS.md)
- Метаданные: name, description, `ObjectType`, `ModelType`

## Engine

| Класс | Назначение |
|-------|------------|
| `ModelRegistry` | In-memory каталог моделей |
| `ModelEngine` | CRUD, apply, instantiate, fromObject |
| `ModelBootstrap` | Built-in models при старте |
| `ModelPersistenceService` | Сохранение пользовательских моделей в `model_definitions` (REQ-PF-07) |

Пользовательские модели (не `builtin`) восстанавливаются из БД при старте сервера после `ensureBuiltInModels()`.

## API

| Method | Path | Описание |
|--------|------|----------|
| GET | `/api/v1/models` | Список |
| GET | `/api/v1/models/{id}` | По ID |
| GET | `/api/v1/models/by-name/{name}` | По имени |
| POST | `/api/v1/models` | Создать |
| PUT | `/api/v1/models/{id}` | Обновить |
| DELETE | `/api/v1/models/{id}` | Удалить |
| POST | `/api/v1/models/{id}/apply?objectPath=` | Применить к объекту |
| POST | `/api/v1/models/{id}/instantiate` | Создать экземпляр |
| POST | `/api/v1/models/from-object` | Экспорт модели из объекта |
| GET | `/api/v1/models/attachments` | Привязки model↔type |

Доступ: **admin**. См. [API.md](API.md).

## Встроенные модели

### mqtt-sensor-v1

Датчик с virtual/MQTT driver.

| Переменная | Группа | Описание |
|------------|--------|----------|
| `status` | status | online, lastSeen |
| `temperature` | telemetry | value, unit |
| `threshold` | config | Порог |
| `alarmActive` | status | Binding: temp > threshold |
| `driverId`, `driverConfigJson`, … | driver | Runtime |

Событие: `thresholdExceeded`.  
Функция: `acknowledgeAlarm`.

### dashboard-v1

| Переменная | Описание |
|------------|----------|
| `title` | Заголовок |
| `layout` | JSON layout |
| `refreshIntervalMs` | Poll interval |

### workflow-v1

| Переменная | Описание |
|------------|----------|
| `title`, `status`, `bpmnXml`, `triggerJson` | Метаданные |
| `instanceState`, `lastRunAt`, `lastAction` | Runtime |

### snmp-agent-v1

SNMP-устройство с MIB-II и HOST-RESOURCES-MIB точками. Демо: `root.platform.devices.snmp-localhost`.

| Переменная | OID | Описание |
|------------|-----|----------|
| `sysName` | 1.3.6.1.2.1.1.5.0 | Имя хоста |
| `sysDescr` | 1.3.6.1.2.1.1.1.0 | Описание системы / ОС |
| `sysUpTime` | 1.3.6.1.2.1.1.3.0 | Uptime (TimeTicks) |
| `sysLocation` | 1.3.6.1.2.1.1.6.0 | Расположение |
| `sysContact` | 1.3.6.1.2.1.1.4.0 | Контакт |
| `hrMemorySize` | 1.3.6.1.2.1.25.2.2.0 | Объём RAM (KB) |
| `hrSystemProcesses` | 1.3.6.1.2.1.25.1.6.0 | Число процессов |
| `hrSystemNumUsers` | 1.3.6.1.2.1.25.1.5.0 | Число пользователей |
| `ifNumber` | 1.3.6.1.2.1.2.1.0 | Число сетевых интерфейсов |
| `ifInOctets` | 1.3.6.1.2.1.2.2.1.10.1 | Входящие octets (интерфейс #1) |
| `ifOutOctets` | 1.3.6.1.2.1.2.2.1.16.1 | Исходящие octets (интерфейс #1) |
| `hrProcessorLoad` | 1.3.6.1.2.1.25.3.3.1.2.1 | Загрузка CPU % (ядро #1) |

Используются дашбордом `root.platform.dashboards.snmp-host-monitoring`.

## Пример: instantiate

```http
POST /api/v1/models/{id}/instantiate
Content-Type: application/json

{
  "parentPath": "root.platform.devices",
  "name": "sensor-02",
  "parameters": {}
}
```

## Связанные документы

- [OBJECT_MODEL.md](OBJECT_MODEL.md) — переменные, DataRecord
- [DASHBOARDS.md](DASHBOARDS.md) — dashboard-v1 layout
- [WORKFLOWS.md](WORKFLOWS.md) — workflow-v1
- [DRIVERS.md](DRIVERS.md) — driver variables в моделях
