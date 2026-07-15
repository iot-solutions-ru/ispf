> **Язык:** русская версия (вычитка). Канонический английский: [en/lab-mqtt-gateway-ingress.md](../en/lab-mqtt-gateway-ingress.md).

# Лаборатория: MQTT gateway ingress (сценарий I-02)

Нагрузочный сценарий **I-02-mqtt-gateway** проверяет путь **один MQTT-драйвер → dispatch на дочерние сенсоры → historian**, в отличие от **I-01**, где каждое устройство имеет свой mqtt-драйвер.

**Цель:** убедиться, что `mqtt-gateway-v1` принимает ingress, вызывает `dispatchTelemetry`, создаёт child sensor'ы (lazy `instantiateModelIfMissing`) и пишет сэмплы в historian при `TELEMETRY_ONLY` (без event journal).

**Где запускать:** lab с **разделённой топологией** (ISPF на хосте приложения, Mosquitto + emqtt на хосте loadgen). Реальные адреса — только в локальных `deploy/lab-*.env` (не коммитить).

## Конвейер

```text
emqtt-bench (хост loadgen, NUMERIC_PAYLOAD)
    → Mosquitto (tcp://<loadgen-host>:1883)
    → 1× mqtt-драйвер на loadtest-mqtt-gateway (TELEMETRY_ONLY)
    → lastIngress → dispatchTelemetry
    → до N child sensor'ов (loadtest-mqtt-sensor-00001…)
    → temperature → TelemetryHistorianFastPath → variable_samples (Scylla)
```

Параметры по умолчанию в ordered suite: **4** топика, **~2000 msg/s** суммарно, фаза **60 с**, прогрев **20 с**, `telemetryCoalesceMs=50` на gateway.

## Запуск (single-node, split topology)

На хосте приложения брокер — **не** `tcp://mqtt:1883` (в compose приложения нет Mosquitto). Используйте адрес из `lab-loadgen.env`:

```bash
cd ~/ispf/loadtest
# shellcheck source=../lab-loadgen.env
source ../lab-loadgen.env

ISPF_EMQTT_DOCKER_NETWORK=ispf-lab_default ISPF_EMQTT_SHARD_MAX=2 \
python3 mqtt-ingress-load-test.py \
  --base-url http://127.0.0.1:${ISPF_LAB_HTTP_PORT:-8000} \
  --mode push \
  --broker-url "tcp://${ISPF_MQTT_BROKER_HOST}:${ISPF_MQTT_BROKER_PORT}" \
  --publisher emqtt-bench \
  --publish-via-ssh "${ISPF_LAB_LOADGEN_SSH}" \
  --remote-deploy-dir "${ISPF_LAB_LOADGEN_ROOT}/loadtest" \
  --emqtt-interval-ms 10 \
  --devices 4 --messages-per-second 2000 \
  --phase-seconds 60 --warmup-seconds 20 \
  --skip-monitor-setup \
  --gateway --telemetry-coalesce-ms 50
```

Через обёртку сценария (soft reset между прогонами):

```bash
cd ~/ispf
bash lab-single-scenario-run.sh --soft-reset I-02-mqtt-gateway -- \
  'cd ~/ispf/loadtest && source ../lab-loadgen.env && \
   ISPF_EMQTT_DOCKER_NETWORK=ispf-lab_default ISPF_EMQTT_SHARD_MAX=2 \
   python3 mqtt-ingress-load-test.py ...'   # как выше
```

С рабочей станции (upload скриптов + SSH):

```bash
python deploy/run_lab_ordered_suite.py --topology single --only I-02 --reset soft
```

## Критерии PASS (скрипт)

| Проверка | Условие |
|----------|---------|
| Gateway driver | `RUNNING` + `connected=true` |
| Ingress | live MQTT до фазы измерения (gateway или child `temperature`) |
| Historian | дельта flushed ≥ 50 за окно 60 с, rate ≥ 0,5 samples/s |
| Event journal | `event_history` не растёт (`TELEMETRY_ONLY`) |

Успешный прогон печатает `=== I-02 PASS ===`.

## Что делает `mqtt-ingress-load-test.py --gateway`

| Шаг | Действие |
|-----|----------|
| 1 | Cleanup loadtest mqtt-устройств |
| 2 | Seed: 1× `loadtest-mqtt-gateway`, child'ы **не** создаются заранее — только lazy dispatch |
| 3 | Стабилизация gateway driver (~30 с) |
| 4 | emqtt на loadgen по SSH; `NUMERIC_PAYLOAD=true` (тело `25.0`) |
| 5 | Прогрев, ожидание ingress на `loadtest-mqtt-sensor-00001` |
| 6 | PATCH `historySampleMode=ALL_VALUES` на всех появившихся child sensor'ах |
| 7 | Измерение 60 с: дельта **`variableHistoryFlushedTotal`** (не `sampleCount`) |

## Подводные камни (I-02)

| Проблема | Симптом | Решение |
|----------|---------|---------|
| Брокер `tcp://mqtt:1883` на split lab | ingress ~0, gateway без трафика | `ISPF_MQTT_BROKER_HOST` + `--publish-via-ssh` из `lab-loadgen.env` |
| Метрика `sampleCount` в historian | FAIL при живом historian после I-01 stress | Дельта по **`variableHistoryFlushedTotal`** / `flushedTotal`; `COUNT(*)` на Scylla даёт 0/timeout |
| `CHANGES_ONLY` (по умолчанию с 0.9.139) + `NUMERIC_PAYLOAD` | мало или 0 сэмплов на child | `ALL_VALUES` на `temperature` child'ов; в instance type `mqtt-gateway-sensor-v1` — `historySampleMode: ALL_VALUES` |
| `NUMERIC_PAYLOAD` не на remote emqtt | timestamp в payload, нестабильный parse | Префикс `NUMERIC_PAYLOAD=true` в SSH-команде `mqtt-emqtt-bench.sh` |
| Короткая длительность publisher | measure без трафика | duration ≥ warmup + wait + phase (~200 с) |
| Stale `lastIngress` + constant payload | WARN ingress при работающем dispatch | Gate: live child `temperature` или waiver при historian delta ≥ 50 |
| Soft reset, Scylla на remote DB | preflight exit 1, `scylla_count=-1` | `lab-single-soft-reset.py` truncate через `ISPF_LAB_DB_SSH`; `--skip-scylla-verify` если COUNT медленный |
| Долгая нагрузка / 403 на API | login/metrics 403 | Перезапуск `ispf-server` + nginx на хосте приложения |

## Базовый уровень (lab, split topology)

Стенд: single-node ISPF на хосте приложения, Scylla + Mosquitto на хосте loadgen, historian store **Scylla**, stress env (очередь historian 8M). После прогонов I-01 на том же стенде таблица `variable_samples` велика — ориентируйтесь на **flushed**, не на `sampleCount`.

| Параметр | Значение |
|----------|----------|
| Устройства (топики) | 4 |
| Publish rate | ~2000 msg/s |
| Historian rate (measure) | **~4,4k samples/s** (flushed delta; remasure 0.9.147) |
| Child sensors | 4 (lazy) |
| Gateway coalesce | 50 ms |
| Результат | **I-02 PASS** |

### I-08 — только historian на gateway `lastIngress.raw`

Тот же стенд; без dispatch на child. Historian на `lastIngress.raw`, coalesce **1 ms**, `ISPF_VARIABLE_HISTORY_MIN_INTERVAL_MS=0`, ISPF **0.9.147**.

| Параметр | Значение |
|----------|----------|
| Publish rate | ~2000 msg/s |
| Live ingress | OK (`lastIngress.raw` в RAM) |
| Historian rate (flushed delta) | **~3,8k samples/s** |
| Результат | **I-08 PASS** |

Нужны запись в **RAM** на historian-only MQTT fast path и замер через **`variableHistoryFlushedTotal`** (не страницы field-history с лимитом 10k). Сводка suite: [load-testing § Ordered suite](load-testing.md#базовый-уровень-ordered-suite-i-01i-08), [`reports/ordered-suite-i01-i08.md`](../../examples/lab-mqtt-historian-stress/reports/ordered-suite-i01-i08.md).

Абсолютные samples/s зависят от железа и профиля stress; таблица — ориентир для регрессии на **одном** lab, не SLA прода.

## Gateway scale (50k eager, split lab)

Сценарий **1× mqtt-gateway → 50k pre-instantiated child sensors → `dispatchTelemetry` → historian** (2 метрики: `temperature`, `humidity`). Оркестратор на хосте приложения; emqtt на loadgen.

**Анонимизация (git):** в коммите — только RFC 5737 адреса (`198.51.100.x`, см. [`examples/lab-mqtt-historian-stress/env/lab-loadgen.env`](../../examples/lab-mqtt-historian-stress/env/lab-loadgen.env)) и плейсхолдеры. Скрипты gateway scale (`deploy/lab-*`, `deploy/setup-mqtt-gateway-scale-devices.py`, `deploy/mqtt_loadtest_lib.py`) — **gitignored**; реальные SSH/хосты/пароли остаются локально на машине оператора. Перед push: `python deploy/tools/anonymize-repo.py` ([documentation-audit](documentation-audit.md#anonymization-policy-public-docs)).

**Обязательно на ISPF:** `ISPF_VARIABLE_HISTORY_MIN_INTERVAL_MS=0` (иначе debounce 5000 ms режет throughput).

**`MESSAGES_PER_SECOND`** — **суммарный** rate на все топики, не на устройство. При 50k×2 метрик = 100k топиков: `MESSAGES_PER_SECOND=100000` ≈ 2 msg/s на устройство (1 Hz на метрику).

### Первичный seed (hard reset)

```bash
cd ~/ispf
# скрипты — копия из gitignored deploy/lab-* на lab-хосте
RESET_MODE=hard INSTANCES=50000 SEED_PARALLEL_WORKERS=8 \
  MESSAGES_PER_SECOND=10000 WARMUP=60 PHASE=120 EMQTT_SHARD_MAX=32 \
  bash lab-gateway-scale-run-from-loadgen.sh
```

### Measure-only (дерево уже seeded)

Не пересоздавайте gateway и 50k child'ов — только restart driver + benchmark:

```bash
cd ~/ispf
# API wait + --ensure-driver-only + stabilize 90s + emqtt measure
bash lab-run-gateway-100k-measure.sh
```

Или вручную на loadgen (без ensure driver на app host):

```bash
EMQTT_LOCAL=1 SKIP_SEED=1 INSTANCES=50000 MESSAGES_PER_SECOND=100000 \
  WARMUP=60 PHASE=120 EMQTT_SHARD_MAX=64 \
  ISPF_LAB_API_BASE=http://198.51.100.11:8000 bash lab-single-mqtt-50k-gateway-test.sh
```

### Тюнинг 100k msg/s + recreate

Патч `lab-stress.env`, `docker compose recreate ispf-server`. Compose **обязан** видеть пароль PG — подключайте **`lab-db.env`** (там `ISPF_DB_PASSWORD`, gitignored):

```bash
cd ~/ispf
bash lab-tune-gateway-100k.sh          # tune + recreate + API wait
bash lab-run-gateway-100k-measure.sh   # measure-only (не re-seed)
# или одной командой:
bash lab-run-gateway-100k-v3.sh
```

Ключевые env (v2/v3, lab 0.9.144):

| Ключ | v1 | v2 | v3 |
|------|----|----|-----|
| `INGRESS_DISPATCH_THREADS_MAX` | 64 | 96 | **128** |
| `WRITER_THREADS_MAX` | 64 | 96 | **128** |
| `CASSANDRA_PARALLEL_BATCHES` | — | 128 | **128** |
| `MQTT_CALLBACK_THREADS` | 256 | 256 | **256** |
| Общее | `INGRESS_DISPATCH_COALESCE=false`, `INGRESS_BYPASS_L3=true`, `MIN_INTERVAL_MS=0`, `OVERFLOW_COALESCE=false` | | |

### Результаты (lab 0.9.144, split topology, Jul 2026)

| Этап | Target | Mosquitto RX | Historian flushed | Queue | Итог |
|------|--------|--------------|-------------------|-------|------|
| 10k baseline | 10k msg/s | ~11,2k | ~22,4k samples/s | ~0 | PASS |
| 100k defaults | 100k msg/s | ~109,5k | ~37,5k | ~0 | FAIL |
| v1 tune (64 workers) | 100k | ~109,4k | ~91,7k | 8M | PASS |
| v2 tune (96 workers) | 100k | ~109,4k | ~94,6k | 8M→5,5M (60s drain) | PASS |
| **v3 tune (128 workers)** | 100k | **~109,5k** | **~84,0k** | **8M→0** (120s drain) | **PASS** |

| Параметр | Значение |
|----------|----------|
| Child instances | 50 000 (eager) |
| MQTT topics | 100 000 (2 metrics) |
| Seed workers | **8** (16+ перегружает nginx/API) |
| emqtt shards | 32 (10k) / **64** (100k) |
| PASS | flushed ≥ 25% цели × metrics; mosquitto rx ≥ 25% цели; `failed_workers=0` в emqtt log |

Оставшийся gap (~84–95k flushed vs ~109k MQTT): historian writer + Scylla ingest; очередь 8M — рычаги `WRITER_THREADS_MAX`, `CASSANDRA_PARALLEL_BATCHES`, partition batches.

### После `recreate ispf-server`

Драйвер gateway **не** переподключается сам → historian **0** при живом Mosquitto RX.

**Правильно:** `--ensure-driver-only` (restart driver, без delete/create дерева):

```bash
python3 loadtest/setup-mqtt-gateway-scale-devices.py \
  --instances 50000 --telemetry-coalesce-ms 0 \
  --base-url http://127.0.0.1:8000 \
  --broker-url "tcp://${ISPF_MQTT_BROKER_HOST}:${ISPF_MQTT_BROKER_PORT}" \
  --ensure-driver-only
sleep 90   # stabilize перед measure
```

**Неправильно:** `--skip-cleanup` без `--eager` на «холодном» API сразу после recreate — nginx может отдать **502**, а старый код пытался `_create_object` заново.

Скрипт `lab-run-gateway-100k-measure.sh` делает API wait (до 60×5s) + `--ensure-driver-only` + stabilize 90s.

### Подводные камни (gateway scale)

| Проблема | Симптом | Решение |
|----------|---------|---------|
| Устаревший `mqtt-emqtt-bench.sh` на loadgen | ~400 msg/s, `failed_workers=8`, emqtt ~1 с | `lab-loadgen-sync.sh` или `REMOTE_FILES` в `lab-gateway-scale-run-from-loadgen.sh`; `chmod +x` |
| API purge 30k+ instances | часы | `RESET_MODE=hard`, не `purge` |
| 16+ parallel seed workers | Connection reset mid-seed | `SEED_PARALLEL_WORKERS=8` |
| `recreate` без ensure driver | historian=0, mosquitto rx > 0 | `--ensure-driver-only` + 90s stabilize |
| 502 сразу после recreate | setup падает на `POST /objects` | API wait; не re-create gateway если уже есть |
| PG password после compose | `password authentication failed` | `ISPF_DB_PASSWORD` из `lab-db.env` в compose (`lab-tune-gateway-100k.sh` копирует) |
| CRLF в скриптах с Windows | `set: pipefail: invalid option` | `tr -d '\r'` на lab или pipe через `bash -s` |

Ключевые файлы scale (**gitignored**, `deploy/`): `lab-gateway-scale-run-from-loadgen.sh`, `lab-single-mqtt-50k-gateway-test.sh`, `lab-tune-gateway-100k.sh`, `lab-run-gateway-100k-measure.sh`, `lab-run-gateway-100k-v3.sh`, `setup-mqtt-gateway-scale-devices.py`, `mqtt_loadtest_lib.py` (`--eager`, `--ensure-driver-only`).

## Связь с I-01 (historian stress)

| | I-01 | I-02 |
|---|------|------|
| MQTT подключений | N драйверов (по устройству) | 1 gateway |
| Historian target | `temperature` на каждом device | `temperature` на child после dispatch |
| Типичный rate | 250k–500k msg/s (stress) | ~2k msg/s (ingress suite) |
| Главная метрика | `variableHistoryFlushedTotal` | то же (**не** `sampleCount`) |

См. [lab-mqtt-historian-stress](lab-mqtt-historian-stress.md) — стресс historian и `ALL_VALUES` для loadtest seed.

## Ключевые файлы

| Файл | Назначение |
|------|------------|
| `deploy/mqtt-ingress-load-test.py` | Тело I-02/I-04/I-08; флаги `--gateway`, PASS/FAIL |
| `deploy/mqtt_loadtest_lib.py` | Seed gateway, `enable_gateway_children_history`, flushed total |
| `deploy/mqtt-emqtt-bench.sh` | emqtt; `NUMERIC_PAYLOAD`, shard |
| `deploy/lab-single-scenario-run.sh` | Обёртка single-node + reset |
| `deploy/lab-single-soft-reset.py` | Soft reset + remote Scylla truncate |
| `deploy/run_lab_ordered_suite.py` | Suite I-01…I-08; `SINGLE_LOADTEST_COMMON` с loadgen broker |
| `deploy/lab-loadgen.env` | `ISPF_MQTT_BROKER_HOST`, `ISPF_LAB_LOADGEN_SSH` (локально, gitignored) |
| `deploy/lab-tune-gateway-100k.sh` | Тюнинг env + recreate ispf-server (100k gateway) **(gitignored)** |
| `deploy/lab-run-gateway-100k-measure.sh` | Measure-only: API wait + `--ensure-driver-only` + benchmark **(gitignored)** |
| `deploy/lab-run-gateway-100k-v3.sh` | tune + measure (v3, 128 threads) **(gitignored)** |
| `examples/lab-mqtt-historian-stress/env/lab-loadgen.env` | Анонимизированная топология split lab (в git) |

Метрики: `GET /api/v1/platform/metrics` → `automation.variableHistoryFlushedTotal`, `variableHistory.flushedTotal`.

## Связанные документы

- [lab-mqtt-historian-stress](lab-mqtt-historian-stress.md) — I-01 historian flood
- [load-testing](load-testing.md) — ordered suite, clean slate
- [decisions/0017-telemetry-ingest-pipeline](decisions/0017-telemetry-ingest-pipeline.md) — gateway dispatch
- [variable-history](variable-history.md) — `historySampleMode`, CHANGES_ONLY vs ALL_VALUES
