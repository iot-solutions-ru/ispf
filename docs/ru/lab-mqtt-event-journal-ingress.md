> **Язык:** русская версия. Английский оригинал: [en/lab-mqtt-event-journal-ingress.md](../en/lab-mqtt-event-journal-ingress.md).

# Нагрузочный сценарий `I-03`: прямая запись потока MQTT в журнал событий (`EVENT_JOURNAL_ONLY`)

> **Статус:** Lab — проверка быстрого пути записи высокочастотного потока сообщений MQTT напрямую в таблицу журнала событий `event_history` ([ADR-0027](decisions/0027-event-journal-ingress-fast-path.md)). Метки зрелости: [doc-status.md](doc-status.md).

Сценарий **`I-03-mqtt-event-journal`** проверяет режим публикации драйвера `telemetryPublishMode = "EVENT_JOURNAL_ONLY"`, в котором каждое входящее сообщение MQTT сразу формирует запись в журнале событий (`EventJournalAsyncWriter`), минуя архив истории переменных и вычисление правил CEL.

---

## 1. Схема прохождения данных

```text
emqtt-bench (генератор нагрузки)
    ──► Брокер Mosquitto
    ──► N драйверов mqtt (режим EVENT_JOURNAL_ONLY, без слияния пакетов)
    ──► TelemetryEventJournalFastPath ──► EventService.fireIngress
    ──► EventJournalAsyncWriter ──► Таблица event_history (ClickHouse / ScyllaDB)
```

---

## 2. Три профиля запуска сценария `I-03`

Шаблоны скриптов находятся в каталоге [`examples/lab-mqtt-historian-stress/scripts/`](../../examples/lab-mqtt-historian-stress/):

| Профиль | Скрипт запуска | Конфигурация потока | Назначение |
|---------|----------------|---------------------|------------|
| **Быстрая проверка (`smoke`)** | `bash lab-single-mqtt-event-journal-test.sh` | 4 устройства × 500 сообщ./с | Быстрая проверка работоспособности конвейера (входит в набор `run_lab_ordered_suite.py --only I-03`) |
| **Пиковый по отдельным топикам (`peak`)** | `bash lab-run-event-journal-peak.sh` | 16 устройств, у каждого свой топик MQTT | Замер предельной скорости при независимых топиках устройств |
| **Массовая рассылка (`400k fan-out`)** | `bash lab-run-event-journal-400k.sh` | 16 драйверов подписаны на общий топик (`--shared-topic`), ~26 000 публ./с | Замер скорости асинхронной записи журнала на уровне ~400 000 событий/с |

---

## 3. Критерии успешного прохождения (`PASS`)

1. Прирост счётчиков `eventsFiredTotal` и `eventJournalFlushedTotal` за фазу измерения не ниже заданного порога профиля.
2. Таблица истории переменных `variable_samples` не растёт (`0` записей, так как включён режим `EVENT_JOURNAL_ONLY`).
3. После завершения генерации очередь `eventJournalQueueSize` полностью сбрасывается на диск, а счётчик синхронных блокировок `eventJournalSyncFallbackTotal` равен `0`.

**Связанные разделы:** [Нагрузочное тестирование (`load-testing.md`)](load-testing.md) · [Стресс-тест журнала событий (`lab-event-journal-stress.md`)](lab-event-journal-stress.md) · [ADR-0027](decisions/0027-event-journal-ingress-fast-path.md).
