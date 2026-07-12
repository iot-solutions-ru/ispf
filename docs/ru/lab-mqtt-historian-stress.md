> **Язык:** русская версия (вычитка). Канонический английский: [en/lab-mqtt-historian-stress.md](../en/lab-mqtt-historian-stress.md).

# Лаборатория: стресс historian MQTT (Scylla vs ClickHouse)

Нагрузочный стенд для пути **TELEMETRY_ONLY** → архиватор: драйвер mqtt → fast historian path → `VariableHistoryAsyncWriter` → `variable_samples` в **Scylla** или **ClickHouse**.

**Цель:** сравнить пропускную способность записи historian на лабораторном железе при **отключённых** coalesce и debounce — это **не** SLA для прода.

**Где запускать:** выделенный lab-хост (доступ по SSH с рабочей станции; HTTP API через nginx на edge).  
**Шаблоны в git:** [`examples/lab-mqtt-historian-stress/`](../../examples/lab-mqtt-historian-stress/) — обезличенные compose, env и скрипты. Скопируйте в `~/ispf` на lab-хостах и подставьте свои адреса.

Реальные хосты, SSH и пароли — только в **локальных** `deploy/lab-*.env` и `deploy/lab_ssh.py` (в `.gitignore`, не коммитить).

## Разделённая топология

Генератор нагрузки и БД вынесены **с** хоста ISPF, чтобы emqtt-bench не конкурировал с writer'ами historian:

| Узел | Роль | Compose / скрипты |
|------|------|-------------------|
| **Хост приложения** (`ISPF_LAB_CLUSTER_HOST`) | ISPF + nginx (публичный edge) | `lab-test-host-compose.yml` + `lab-stress.env` или `lab-stress-ch.env` |
| **Хост loadgen/DB** (`ISPF_LAB_LOADGEN_HOST` / `ISPF_LAB_DB_HOST`) | PostgreSQL, Scylla, ClickHouse, Mosquitto, emqtt | `lab-db-compose.yml`, `lab-loadgen-compose.yml` |

```text
emqtt-bench (--network host, брокер на хосте loadgen)
    → Mosquitto (контейнер loadgen)
    → 16× драйвер mqtt (TELEMETRY_ONLY, ingressCoalesceEnabled=false)
    → TelemetryHistorianFastPath → VariableHistoryAsyncWriter
    → Scylla variable_samples   ИЛИ   ClickHouse variable_samples
```

Драйверы на хосте приложения подписаны на **`ISPF_MQTT_BROKER_URL`** (обычно `tcp://<loadgen-host>:1883`). Издатель на хосте loadgen обязан использовать **`MQTT_PUBLISH_HOST`** из `lab-loadgen.env` — адрес, на котором Mosquitto реально слушает в LAN, а не `127.0.0.1`, если брокер привязан только к внешнему интерфейсу.

## Пример проведения теста (Scylla, затем ClickHouse)

### 0. Поднять DB + MQTT на хосте loadgen

```bash
# На хосте приложения (ISPF)
ssh "${ISPF_LAB_DB_SSH}" 'cd ~/ispf && bash lab-db-bootstrap.sh'
bash ~/ispf/lab-loadgen-bootstrap.sh
```

Переменные `ISPF_LAB_DB_SSH`, `ISPF_LAB_LOADGEN_SSH` — в `examples/lab-mqtt-historian-stress/env/lab-loadgen.env` (замените на свои значения на стенде).

### 1. Historian Scylla (250k и 500k msg/s)

```bash
cd ~/ispf

docker compose --env-file lab-stress.env -f lab-test-host-compose.yml \
  up -d --force-recreate ispf-server nginx

curl -sf http://127.0.0.1:${ISPF_LAB_HTTP_PORT:-8000}/api/v1/info

bash lab-emqtt-cleanup-remote.sh

# 16 устройств × 15625 msg/s = 250k/s; фаза 90 с после прогрева 20 с
DEVICES=16 RATE_PER_DEVICE=15625 PHASE=90 WARMUP=20 INTERVAL_MS=1 EMQTT_SHARD_MAX=8 \
  bash lab-single-mqtt-historian-test.sh

# 500k/s: RATE_PER_DEVICE=31250
DEVICES=16 RATE_PER_DEVICE=31250 PHASE=90 WARMUP=20 INTERVAL_MS=1 EMQTT_SHARD_MAX=8 \
  bash lab-single-mqtt-historian-test.sh
```

### 2. Historian ClickHouse (те же скорости)

`ISPF_VARIABLE_HISTORY_STORE` берётся из env — в `lab-test-host-compose.yml`: `${ISPF_VARIABLE_HISTORY_STORE:-scylla}`.

```bash
docker compose --env-file lab-stress-ch.env -f lab-test-host-compose.yml \
  up -d --force-recreate ispf-server nginx

curl -sf http://127.0.0.1:${ISPF_LAB_HTTP_PORT:-8000}/api/v1/info
docker compose -f lab-test-host-compose.yml exec ispf-server \
  printenv ISPF_VARIABLE_HISTORY_STORE   # ожидается clickhouse

bash lab-emqtt-cleanup-remote.sh

DEVICES=16 RATE_PER_DEVICE=15625 PHASE=90 WARMUP=20 INTERVAL_MS=1 EMQTT_SHARD_MAX=8 \
  bash lab-single-mqtt-historian-ch-test.sh

DEVICES=16 RATE_PER_DEVICE=31250 PHASE=90 WARMUP=20 INTERVAL_MS=1 EMQTT_SHARD_MAX=8 \
  bash lab-single-mqtt-historian-ch-test.sh

# Вернуть Scylla-профиль
docker compose --env-file lab-stress.env -f lab-test-host-compose.yml \
  up -d --force-recreate ispf-server nginx
```

С рабочей станции: скопировать `examples/lab-mqtt-historian-stress/` на lab; SSH-хелпер — `deploy/lab_ssh.py` (gitignored, см. `deploy/local/README.example.md`).

## Что делает скрипт бенчмарка

`lab-single-mqtt-historian-test.sh` / `lab-single-mqtt-historian-ch-test.sh` (сценарий **I-01 historian**):

| Шаг | Действие |
|-----|----------|
| 1 | `setup-mqtt-historian-devices.py` — 16× `loadtest-mqtt-dev-*`, `TELEMETRY_ONLY`, historian на `temperature`, `ingressCoalesceEnabled=false`, брокер из `ISPF_MQTT_BROKER_URL` |
| 2 | Стабилизация 15 с; cleanup emqtt на хосте loadgen |
| 3 | Запуск `lab-emqtt-remote.sh` с `NUMERIC_PAYLOAD=true` (timestamp в payload подавляется `minIntervalMs`) |
| 4 | Прогрев `WARMUP` с, затем измерение `PHASE` с |
| 5 | Дельты: Mosquitto `$SYS/broker/messages/received`, `variableHistoryFlushedTotal`, строки в store |
| 6 | Ожидание `variableHistoryQueueSize` → 0 |

### Критерии PASS (скрипт)

- Дельта historian flushed ≥ 30 за окно измерения
- Дельта store ≥ 40 строк (CH `count()`; Scylla `COUNT(*)` — см. подводные камни)

**Главная метрика сквозной доставки:** `Mosquitto received ≈ historian flushed ≈ store delta` (после слива очереди).

## Базовый уровень (lab, ISPF 0.9.137, 2026-07)

Стенд: split topology, 16 драйверов mqtt, `NUMERIC_PAYLOAD`, `minIntervalMs=0`, ingress coalesce выкл., очередь 8M, JVM 28G, Scylla 20 SMP / 40G на хосте DB, ClickHouse 24.8 на том же хосте.

| Store | Цель | Mosquitto RX | Historian flushed | Store Δ/с | Очередь в конце |
|-------|------|--------------|-------------------|-----------|-----------------|
| **Scylla** | 250k | **257k** msg/s | **258k** samples/s | COUNT timeout* | 0 |
| **Scylla** | 500k | **520k** msg/s | **559k** samples/s | COUNT timeout* | 0 (пик ~7.9M, слита) |
| **ClickHouse** | 250k | **288k** msg/s | **288k** samples/s | **289k** rows/s | 0 |
| **ClickHouse** | 500k | **579k** msg/s | **579k** samples/s | **579k** rows/s | 0 |

\* `SELECT COUNT(*) FROM variable_samples` на Scylla **таймаутит** после десятков миллионов строк — скрипт может печатать `FAIL`, хотя метрики historian в норме. Для Scylla ориентируйтесь на `variableHistoryFlushedTotal` и Mosquitto `$SYS`.

**Только Scylla:** `ISPF_VARIABLE_HISTORY_BENCHMARK_SPREAD_SAMPLED_AT=true` — уникальный `sampled_at` на сообщение. **Профиль CH в lab:** spread **выкл.** (CH держит высокий insert rate без разнесения timestamp по сериям).

## Lab vs prod

Абсолютные msg/s **нельзя сравнивать** между хостами. Таблица — **что намеренно отличается** в lab stress от типичного **продакшен-стенда**:

| Аспект | Lab historian stress | Prod (типично) |
|--------|----------------------|----------------|
| **Задача** | Максимум ingress + запись в store | Стабильная эксплуатация, retention, UI, смешанная нагрузка |
| **Топология** | ISPF на отдельном хосте; DB + MQTT на loadgen-хосте | Часто один VPS: ISPF + PG + CH journal + компактная Scylla |
| **Historian store** | Scylla **или** CH (A/B на одном железе) | `jdbc`/Timescale по умолчанию; journal CH; historian часто PG ([deployment](deployment.md)) |
| **`minIntervalMs`** | **0** (каждое сообщение может стать сэмплом) | **5000–10000**+ (debounce БД, [demostands](demostands.md)) |
| **Ingress coalesce** | L0/L3/L4 **выкл.**; `TELEMETRY_ONLY` | Per-device / global coalesce 1–5 с для реальных тегов |
| **MQTT callback threads** | 256, очередь 500k | Десятки, не сотни |
| **Очередь historian** | 8M, elastic writers 8–48 | Тысячи — десятки тысяч |
| **JVM heap** | 28G, G1 под flood | 4G idle / умеренный prod |
| **Scylla** | Много SMP, десятки GB RAM, выделенный DB-хост | 1 SMP, сотни MB — под footprint, не flood |
| **ClickHouse** | Отдельный контейнер, lab-учётные данные | Prod playbook, retention, аналитика ([clickhouse-prod-playbook](clickhouse-prod-playbook.md)) |
| **Payload** | Числовой константный (`NUMERIC_PAYLOAD`) | Реальная телеметрия; timestamp в payload взаимодействует с debounce |
| **Устройства** | 16 синтетических loadtest-топиков | Реальный парк + demo (на prod idle тяжёлое demo отключено) |
| **Журнал событий** | Вкл. (тест Scylla) / **выкл.** (тест CH) | CH на проде для `event_history` |
| **Фикстуры** | `ISPF_BOOTSTRAP_FIXTURES_ENABLED=false` | Политика prod deploy: без demo fixtures |

**Вывод для прода:** устойчивые **~250–500k samples/s** в historian — **потолок lab** на выделенном железе со stress env, а не целевая конфигурация прода. Fair-бенчи прода (4 dev, coalesce sweep, `min-interval-ms=100`) — в [load-testing](load-testing.md).

## Подводные камни

| Проблема | Симптом | Решение |
|----------|---------|---------|
| emqtt шлёт на `127.0.0.1`, а Mosquitto слушает LAN | Mosquitto RX ~0, `econnrefused` | `MQTT_PUBLISH_HOST` в `env/lab-loadgen.env` (адрес loadgen-хоста) |
| CH env не применился | `printenv` показывает `scylla` | `ISPF_VARIABLE_HISTORY_STORE: "${ISPF_VARIABLE_HISTORY_STORE:-scylla}"` в compose |
| nginx 502 после recreate | API недоступен 3–5 с | Всегда `up -d --force-recreate ispf-server nginx` вместе |
| Scylla `COUNT(*)` FAIL | delta 0, flushed OK | Ожидаемо при большом объёме; доверяйте flushed + Mosquitto |
| CH script `pipefail` | exit 2 | LF: `sed -i 's/\r$//' lab-single-mqtt-historian-ch-test.sh` |
| Timestamp в MQTT payload | ~1 sample/device | `NUMERIC_PAYLOAD=true` в скриптах бенчмарка |
| `historySampleMode` CHANGES_ONLY (0.9.139+) | 0 flushed при constant payload без ALL_VALUES | `enable_variable_history(..., historySampleMode=ALL_VALUES)` в loadtest seed |
| `Done (formula estimate)` | Завышенные msg/s | Не замер; используйте `$SYS` и метрики ISPF |

## Ключевые файлы

Шаблоны в репозитории: [`examples/lab-mqtt-historian-stress/`](../../examples/lab-mqtt-historian-stress/).

| Файл | Назначение |
|------|------------|
| `compose/lab-test-host-compose.yml` | ISPF + nginx на хосте приложения; store из env |
| `env/lab-stress.env` | Stress Scylla (spread, очередь 8M, journal вкл.) |
| `env/lab-stress-ch.env` | Stress ClickHouse (journal выкл.) |
| `compose/lab-db-compose.yml` / `scripts/lab-db-bootstrap.sh` | PG + Scylla + CH на хосте DB |
| `env/lab-loadgen.env` / `compose/lab-loadgen-compose.yml` | Mosquitto + `MQTT_PUBLISH_HOST`, SSH-алиасы |
| `scripts/lab-single-mqtt-historian-test.sh` | Бенчмарк Scylla I-01 |
| `scripts/lab-single-mqtt-historian-ch-test.sh` | Бенчмарк ClickHouse I-01 |
| `scripts/lab-emqtt-remote.sh` | emqtt на loadgen по SSH |

Метрики: `GET /api/v1/platform/metrics` → `automation.variableHistoryFlushedTotal`, `variableHistoryQueueSize`.

## Связанные документы

- [load-testing](load-testing.md) — общие MQTT / historian сценарии
- [lab-mqtt-gateway-ingress](lab-mqtt-gateway-ingress.md) — I-02 gateway dispatch → child historian
- [lab-event-journal-stress](lab-event-journal-stress.md) — EVENT_JOURNAL_ONLY (события/с, не сэмплы)
- [variable-history](variable-history.md) — модель historian и stores
- [demostands](demostands.md) — профили развёртывания prod
- [0026-elastic-telemetry-ingress](decisions/0026-elastic-telemetry-ingress.md) — конвейер ingress
