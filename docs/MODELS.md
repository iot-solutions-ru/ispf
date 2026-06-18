# Models Plugin

Модуль `ispf-plugin-model` реализует систему **моделей** — шаблонов структуры объектов.

## Типы моделей

| Тип | Поведение |
|-----|-----------|
| `RELATIVE` | Переменные/события вливаются в существующий объект |
| `ABSOLUTE` | Отдельная ветка объекта (singleton) |
| `INSTANCE` | Создание экземпляра по запросу |

## API

| Method | Path | Описание |
|--------|------|----------|
| GET | `/api/v1/models` | Список моделей |
| GET | `/api/v1/models/{id}` | Модель по ID |
| GET | `/api/v1/models/by-name/{name}` | Модель по имени |
| POST | `/api/v1/models` | Создать модель |
| PUT | `/api/v1/models/{id}` | Обновить модель |
| DELETE | `/api/v1/models/{id}` | Удалить модель |
| POST | `/api/v1/models/{id}/apply?objectPath=...` | Применить к объекту |
| POST | `/api/v1/models/{id}/instantiate` | Создать экземпляр |
| POST | `/api/v1/models/from-object` | Создать модель из объекта |
| GET | `/api/v1/models/attachments` | Список привязок |

## Встроенная модель

`mqtt-sensor-v1` — датчик MQTT с переменными `status`, `temperature`, `threshold`, `alarmActive` и событием `thresholdExceeded`.
