> **Язык:** русская версия. Английский оригинал: [en/lab-mqtt-historian-stress.md](../en/lab-mqtt-historian-stress.md).

# Нагрузочный сценарий `I-01`: стресс-тест записи истории переменных из MQTT (ClickHouse и ScyllaDB)

> **Статус:** Lab — методика замера предельной скорости записи временных рядов (`TELEMETRY_ONLY` → `VariableHistoryAsyncWriter` → `variable_samples`) на выделенном стенде. Метки зрелости: [doc-status.md](doc-status.md).

Сценарий **`I-01`** измеряет пропускную способность прямого пути записи телеметрии из драйверов `mqtt` в таблицу `variable_samples` (в СУБД ClickHouse или ScyllaDB) при полностью отключённом слиянии пакетов (`ingressCoalesceEnabled = false`, `minIntervalMs = 0`).

Готовые обезличенные шаблоны конфигурации стенда и скрипты находятся в каталоге [`examples/lab-mqtt-historian-stress/`](../../examples/lab-mqtt-historian-stress/).

---

## 1. Разнесённая схема стенда (`Split Topology`)

Чтобы генератор трафика (`emqtt-bench`) и СУБД не отнимали ядра процессора у самого сервера `ispf-server`, стенд разворачивается на двух машинах:

| Узел стенда | Роль | Конфигурация Compose |
|-------------|------|----------------------|
| **Сервер приложения** (`ISPF_LAB_CLUSTER_HOST`) | `ispf-server` + `nginx` | `lab-test-host-compose.yml` с профилем `lab-stress-ch.env` (ClickHouse) или `lab-stress.env` (ScyllaDB) |
| **Сервер генератора и БД** (`ISPF_LAB_LOADGEN_HOST`) | PostgreSQL, ClickHouse, ScyllaDB, брокер Mosquitto и контейнеры `emqtt-bench` | `lab-db-compose.yml`, `lab-loadgen-compose.yml` |

```text
emqtt-bench (генератор на втором хосте)
    ──► Брокер Mosquitto
    ──► 16 драйверов mqtt на сервере ISPF (режим TELEMETRY_ONLY, без слияния)
    ──► TelemetryHistorianFastPath ──► VariableHistoryAsyncWriter
    ──► Таблица variable_samples в ClickHouse или ScyllaDB
```

---

## 2. Порядок запуска замера

### 1. Подготовка БД и брокера на вспомогательном хосте

```bash
ssh "${ISPF_LAB_DB_SSH}" 'cd ~/ispf && bash lab-db-bootstrap.sh'
bash ~/ispf/lab-loadgen-bootstrap.sh
```

### 2. Прогон с хранилищем ClickHouse (целевой поток 250 000 и 500 000 точек/с)

```bash
cd ~/ispf
docker compose --env-file lab-stress-ch.env -f lab-test-host-compose.yml \
  up -d --force-recreate ispf-server nginx

bash lab-emqtt-cleanup-remote.sh

# 16 устройств × 15 625 сообщений/с = 250 000 точек/с (90 с замер после 20 с прогрева)
DEVICES=16 RATE_PER_DEVICE=15625 PHASE=90 WARMUP=20 INTERVAL_MS=1 EMQTT_SHARD_MAX=8 \
  bash lab-single-mqtt-historian-ch-test.sh

# 16 устройств × 31 250 сообщений/с = 500 000 точек/с
DEVICES=16 RATE_PER_DEVICE=31250 PHASE=90 WARMUP=20 INTERVAL_MS=1 EMQTT_SHARD_MAX=8 \
  bash lab-single-mqtt-historian-ch-test.sh
```

Для аналогичного прогона на ScyllaDB используйте файл окружения `lab-stress.env` и скрипт `lab-single-mqtt-historian-test.sh`.

---

## 3. Контролируемые метрики

Скрипт сопоставляет три показателя за окно измерения:
1. Число сообщений, принятых брокером Mosquitto (`$SYS/broker/messages/received`).
2. Прирост счётчика записанных сервером ISPF точек (`variableHistoryFlushedTotal`).
3. Слив очереди записи `variableHistoryQueueSize` до `0` после завершения подачи трафика.

**Связанные разделы:** [Нагрузочное тестирование (`load-testing.md`)](load-testing.md) · [Уровни хранения истории (`historian-tiers.md`)](historian-tiers.md) · [Настройка ClickHouse (`clickhouse-prod-playbook.md`)](clickhouse-prod-playbook.md).
