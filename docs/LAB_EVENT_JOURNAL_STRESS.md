# Lab: event journal stress (Scylla + MQTT)

Нагрузочный стенд для **EVENT_JOURNAL_ONLY** fast path ([ADR-0027](decisions/0027-event-journal-ingress-fast-path.md)): mqtt driver → `fireIngress` → `EventJournalAsyncWriter` → Scylla `event_history`.

**Хост:** `84.42.21.226`, SSH port `5031`, user `iot-solutions`  
**HTTP:** `http://84.42.21.226:8000` (lab nginx)  
**Стек:** `~/ispf` — `deploy/lab-test-host-compose.yml` + `deploy/lab-stress.env`

## Быстрый старт

```bash
# На lab-хосте (после deploy артефактов в ~/ispf)
cd ~/ispf
set -a && . lab-stress.env && set +a

# Полный clean run (wipe volumes + benchmark)
bash lab-test-host-clean-run.sh

# Peak / max load (16×32k target, 8 emqtt shards)
DEVICES=16 RATE_PER_DEVICE=32000 EMQTT_SHARD_MAX=8 SKIP_DEVICE_SETUP=false \
  bash lab-mqtt-event-journal-multi-test.sh

# Sustained (~калиброванный rate)
python3 loadtest/../run_lab_sustained.py   # или с Windows: deploy/run_lab_sustained.py

# Auto-calibrate sustained target по фактическому journal
AUTO_CALIBRATE=true CALIBRATE_RUN_MAIN=true bash lab-mqtt-event-journal-multi-test.sh
```

С Windows (upload + run):

```powershell
python deploy/run_lab_max_load.py      # peak + summary
python deploy/run_lab_clean_run.py     # factory reset + sustained
python deploy/run_lab_peak_v088.py     # deploy JAR + peak
```

## Архитектура прогона

```text
emqtt-bench (Docker, sharded --topics-payload)
    → Mosquitto (sys_interval 1, $SYS counters)
    → 16× mqtt driver (ingressCoalesceEnabled=false, EVENT_JOURNAL_ONLY)
    → TelemetryEventJournalFastPath → EventService.fireIngress
    → EventJournalAsyncWriter (24 threads) → Scylla event_history
```

**Не участвуют:** object-change bus (`ISPF_OBJECT_CHANGE_ASYNC_ENABLED=false`), historian, alert CEL, HTTP `/events/fire`.

### Уровни ingress

| Уровень | Настройка | Bench |
|---------|-----------|-------|
| L0 MQTT driver | `ingressCoalesceEnabled` | **false** (`--bench-no-l0-coalesce`) |
| L1 server buffer | `usesDirectIngress` | **пропуск** для `EVENT_JOURNAL_ONLY` |
| Journal queue | 5M capacity, 24 writers | не лимит при peak |

## Скрипты

| Файл | Назначение |
|------|------------|
| `deploy/lab-test-host-compose.yml` | postgres + scylla + mqtt + ispf-server + nginx |
| `deploy/lab-stress.env` | JVM, Scylla SMP/RAM, journal writer, MQTT callback defaults |
| `deploy/lab-mqtt-event-journal-multi-test.sh` | Multi-device benchmark + метрики |
| `deploy/lab-test-host-clean-run.sh` | `docker compose down -v` + benchmark |
| `deploy/lab-emqtt-cleanup.sh` | Stop orphaned emqtt-bench containers |
| `deploy/mqtt-emqtt-bench.sh` | emqtt-bench wrapper (sharded, CPU cap) |
| `deploy/setup-mqtt-event-journal-devices.py` | Seed N devices, EVENT_JOURNAL_ONLY |
| `deploy/run_lab_*.py` | Upload + SSH orchestration с Windows |

### Параметры benchmark

| Env | Default | Описание |
|-----|---------|----------|
| `DEVICES` | 8 | Число mqtt loadtest devices |
| `RATE_PER_DEVICE` | 2000 | **Configured** MQTT target (msg/s на device) |
| `WARMUP` / `PHASE` | 15 / 60 | Секунды прогрева и измерения |
| `INTERVAL_MS` | 1 | emqtt interval (1 ms → до 1000 msg/s на client) |
| `EMQTT_SHARD_MAX` | 4 | Max Docker-контейнеров emqtt (lab-stress: 4; peak: 8) |
| `EMQTT_CPU_LIMIT` | 1.5 | `--cpus` на shard (защита от starvation ISPF/Scylla) |
| `BENCH_NO_L0_COALESCE` | true | `ingressCoalesceEnabled=false` на drivers |
| `AUTO_CALIBRATE` | false | Probe → `RATE_PER_DEVICE` по journal |
| `SKIP_DEVICE_SETUP` | false | Пропустить re-seed (после recreate **mqtt** нужен false) |

## Метрики и «efficiency»

Скрипт `lab-mqtt-event-journal-multi-test.sh` выводит **три слоя** throughput за фазу измерения:

| Метрика | Источник | Смысл |
|---------|----------|-------|
| Mosquitto PUBLISH in | `$SYS/broker/messages/received` | Все PUBLISH от emqtt-клиентов |
| Mosquitto delivered | `$SYS/broker/messages/sent` | Доставка подписчикам |
| ISPF eventsFired | `/api/v1/platform/metrics` | Принято fast path |
| Journal flushed | metrics | Записано async writer |
| Journal (Scylla meta) | `event_journal_meta.total_count` | Глобальный counter (может lag при asyncCounter) |

### Efficiency (как читать)

| Строка | Интерпретация |
|--------|---------------|
| **Journal vs configured target** | Journal / (`DEVICES × RATE_PER_DEVICE`). **Reference only** — emqtt часто не достигает target (CPU cap, ceil clients). Стабильные «~17–22%» **не означают** потерю в ISPF. |
| **Journal vs emqtt formula** | Journal / (clients × 1000/interval × devices). Formula **не замер**, а расчёт скрипта. |
| **ISPF capture (fired/delivered)** | eventsFired / Mosquitto delivered. На QoS0 при многих emqtt clients на topic может быть <100% (subscriber не успевает). |
| **Scylla meta vs eventsFired** | Должно быть **~100%** после queue drain — потерь на journal path нет. |

**Главная метрика «без потерь»:** `eventsFired ≈ flushed ≈ meta` (100%).

Mosquitto `$SYS` требует `sys_interval 1` в `deploy/mosquitto/mosquitto.conf`; после изменения: `docker compose ... up -d --force-recreate mqtt` и re-seed drivers.

## Baseline (lab, ISPF 0.9.88, Scylla 20 SMP / 48G)

Стенд: 16 devices, `ingressCoalesceEnabled=false`, `callbackThreads=64`, journal store=scylla, async counter on.

| Сценарий | Config target | Journal (eventsFired) | Per device | Узкое место |
|----------|---------------|----------------------|------------|-------------|
| Clean sustained | 16×5200 = 83k | ~14k/s | ~900/s | emqtt 4 shards, low clients |
| Peak (8 shards) | 16×32k = 512k | **~83–110k/s** | **~6.8–7.2k/s** | **Scylla ~108% CPU** |
| Max load confirm | 16×32k, 60s phase | **109 578/s** | 6 849/s | Scylla saturated; ISPF ~8% CPU |

### Выводы (2026-07-04)

1. **Журнал не теряет сообщения** на принятом ingress: meta vs eventsFired = **100%**, sync fallback = 0, queue drain ≈ 0.
2. **Максимальная устойчивая нагрузка** на этом lab (~110k events/s, ~6.8k/device × 16) ограничена **записью Scylla**, не очередью journal и не object-change bus.
3. **«17% efficiency»** — артефакт сравнения с завышенным configured target / formula emqtt, не потери в pipeline.
4. Разница clean (~14k) vs peak (~110k) — **конфиг emqtt** (shards, clients, CPU), не «грязная» БД.
5. После `docker compose ... recreate mqtt` drivers отваливаются — нужен re-seed (`SKIP_DEVICE_SETUP=false`).

### Рекомендуемый sustained target

```bash
# ~100k events/s aggregate (запас под peak ~110k)
STRESS_SUSTAINED_RATE_PER_DEVICE=6800   # 16 × 6800 ≈ 109k
# или AUTO_CALIBRATE=true после probe
```

Peak emqtt: `EMQTT_SHARD_MAX=8`, `RATE_PER_DEVICE=32000` (formula 512k; фактический journal ~110k).

## Runtime tuning (без rebuild)

`deploy/lab-stress.env` и hot-reload через runtime-settings (см. `PlatformRuntimeSettingsCatalog`):

| Env | Lab value | Эффект |
|-----|-----------|--------|
| `ISPF_EVENT_JOURNAL_WRITER_THREADS` | 24 | Parallel Scylla batch flush |
| `ISPF_EVENT_JOURNAL_BATCH_SIZE` | 5000 | Batch size |
| `ISPF_EVENT_JOURNAL_CASSANDRA_PARTITION_BATCH` | 200 | Partition-local UNLOGGED batches |
| `ISPF_EVENT_JOURNAL_CASSANDRA_PARALLEL_BATCHES` | 16 | Parallel partition writers |
| `ISPF_EVENT_JOURNAL_CASSANDRA_ASYNC_COUNTER_UPDATE` | true | Non-blocking meta counter (lag в measure — норма) |
| `ISPF_DRIVER_MQTT_CALLBACK_THREADS` | 64 | L0 FIFO workers per driver |
| `ISPF_OBJECT_CHANGE_ASYNC_ENABLED` | false | Bus off for stress |

## emqtt-bench

Формула clients (скрипт):

```text
clients_per_topic = ceil(rate_per_device × interval_ms / 1000)
formula_rate      = devices × clients_per_topic × (1000 / interval_ms)
```

Строки `ISPF_EMQTT_FORMULA_RATE=` в stdout — для парсинга benchmark. Строка `Done (formula estimate)` — **не замер** broker.

Ограничения lab:

- `--cpus EMQTT_CPU_LIMIT` (default 1.5) — Erlang не держит 1 ms interval на полной формуле (~15–21% от formula).
- Много clients на topic + QoS0 → broker `received` >> ISPF `eventsFired`.

Cleanup orphans: `bash lab-emqtt-cleanup.sh` (label `ispf.emqtt-bench=1`).

## Логи на lab

| Путь | Содержание |
|------|------------|
| `~/ispf/loadtest/clean-run.log` | Factory reset benchmark |
| `~/ispf/loadtest/max-load-peak.log` | Peak / max load |
| `~/ispf/loadtest/peak-confirm.log` | Confirm без wipe |
| `~/ispf/loadtest/emqtt-pub-*.log` | emqtt stdout |

## Связанные документы

- [LOAD_TESTING.md](LOAD_TESTING.md) — общие MQTT / emqtt сценарии
- [ADR-0027](decisions/0027-event-journal-ingress-fast-path.md) — EVENT_JOURNAL_ONLY
- [ADR-0026](decisions/0026-elastic-telemetry-ingress.md) — ingress pipeline
- [AUTOMATION.md](AUTOMATION.md) — platform metrics API

## VPS prod comparison (ispf.iot-solutions.ru)

Same benchmark params as lab peak: **16×32k target**, **8 emqtt shards**, 60s measure, `EVENT_JOURNAL_ONLY`, Scylla journal.

| | Lab (84.42.21.226) | VPS prod |
|--|-------------------|----------|
| ISPF | 0.9.88 | 0.9.86 |
| Scylla | 20 SMP / 48G | **1 SMP / 750M** |
| Journal writers | 24 | **6** |
| **eventsFired** | **~110k/s** | **~349/s** |
| Per device | ~6.8k/s | ~19/s |
| meta vs eventsFired | 100% | ~88% (queue backlog) |
| Script | `lab-mqtt-event-journal-multi-test.sh` | `vps-mqtt-event-journal-multi-test.sh` |

```bash
# On VPS (after scripts in /opt/ispf/loadtest)
DEVICES=16 RATE_PER_DEVICE=32000 EMQTT_SHARD_MAX=8 \
  bash /opt/ispf/loadtest/vps-mqtt-event-journal-multi-test.sh

# From dev machine
python deploy/run_vps_max_load.py
```

VPS Scylla is sized for prod footprint, not flood load — **do not compare absolute events/s with lab**. Journal path still works; bottleneck is Scylla capacity and MQTT ingress at QoS0 under overload.
