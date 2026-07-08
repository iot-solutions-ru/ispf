> **Язык:** русская версия (вычитка). Канонический английский: [en/lab-event-journal-stress.md](../en/lab-event-journal-stress.md).

# Лаборатория: стресс журнала событий (Scylla + MQTT)

Нагрузочный стенд для **EVENT_JOURNAL_ONLY** быстрого пути ([ADR-0027](decisions/0027-event-journal-ingress-fast-path.md)): драйвер mqtt → `fireIngress` → `EventJournalAsyncWriter` → Scylla `event_history`.

**Хост:** `84.42.21.226`, порт SSH `5031`, пользователь `iot-solutions`  
**HTTP:** `http://84.42.21.226:8000` (лаборатория nginx)  
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

С Windows (загрузить + запустить):

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

### Уровни вход

| Уровень | Настройка | Скамейка |
|---------|-----------|-------|
| L0 MQTT driver | `ingressCoalesceEnabled` | **false** (`--bench-no-l0-coalesce`) |
| L1 server buffer | `usesDirectIngress` | **пропуск** для `EVENT_JOURNAL_ONLY` |
| Очередь журнала | Вместимость 5 миллионов человек, 24 автора | без лимита в пик |

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

### Параметры теста

| Окружение | По умолчанию | Описание |
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

## Метрики и «эффективность»

Скрипт `lab-mqtt-event-journal-multi-test.sh` выводит **три слоя** throughput за фазу измерения:

| Метрика | Источник | Смысл |
|---------|----------|-------|
| Mosquitto PUBLISH in | `$SYS/broker/messages/received` | Все PUBLISH от emqtt-клиентов |
| Mosquitto delivered | `$SYS/broker/messages/sent` | Доставка подписчикам |
| ISPF eventsFired | `/api/v1/platform/metrics` | Принято fast path |
| Журнал смывается | метрики | Записано асинхронный писатель |
| Журнал (мета Сциллы) | `event_journal_meta.total_count` | Глобальный счетчик (может отставать при asyncCounter) |

### Эффективность (как читать)

| Строка | Интерпретация |
|--------|---------------|
| **Журнал и настроенная цель** | Журнал / (`DEVICES × RATE_PER_DEVICE`). **Только для справки** — emqtt часто не достигает цели (ограничение ЦП, количество клиентов). Стабильные «~17–22%» **не означают** силы в ISPF. |
| **Формула журнала и emqtt** | Журнал / (клиенты × 1000/интервал × устройства). Формула **не замер**, а расчёт скрипта. |
| **Захват ISPF (запущен/доставлен)** | eventFired / Mosquitto доставлен. На QoS0 при многих клиентах emqtt по теме может быть <100% (абонент не настроен). |
| **Мета-Сцилла против eventFired** | Должно быть **~100%** после опорожнения очереди — потерь на пути к журналу нет. |

**Главная метрика «без потерь»:** `eventsFired ≈ flushed ≈ meta` (100%).

Mosquitto `$SYS` требует `sys_interval 1` в `deploy/mosquitto/mosquitto.conf`; после изменения: `docker compose ... up -d --force-recreate mqtt` и re-seed drivers.

## Базовый уровень (лаборатория, ISPF 0.9.88, Scylla 20 SMP/48G)

Стенд: 16 devices, `ingressCoalesceEnabled=false`, `callbackThreads=64`, journal store=scylla, async counter on.

| Сценарий | Цель конфигурации | Журнал (eventsFired) | За устройство | Узкое место |
|----------|---------------|----------------------|------------|-------------|
| Чистый устойчивый | 16×5200 = 83к | ~14 тыс./с | ~900/с | emqtt 4 шарда, низкие клиенты |
| Пик (8 осколков) | 16×32к = 512к | **~83–110 тыс./с** | **~6,8–7,2 тыс./с** | **Сцилла ~108% ЦП** |
| Максимальная нагрузка подтверждается | 16×32к, фаза 60-х | **109 578/с** | 6 849/с | Сцилла насыщенная; ISPF ~8% ЦП |

### Выводы (04.07.2026)

1. **Журнал не опирается на сообщения** по общепринятым входам: мета vs eventFired = **100%**, резервная синхронизация = 0, слив очереди ≈ 0.
2. **Максимальная устойчивая нагрузка** в этой лаборатории (~110 тыс. событий/с, ~6,8 тыс./устройство × 16) ограничена **записью Scylla**, не включаю журнал и не шину изменения объектов.
3. **«КПД 17%»** — эталонный образец с завышенной настроенной целью/формулой emqtt, без потерь в конвейере.
4. Разница чистая (~14к) и пиковая (~110к) — **конфиг emqtt** (шарды, клиенты, ЦП), не «грязная» БД.
5. После `docker compose ... recreate mqtt` drivers отваливаются — нужен re-seed (`SKIP_DEVICE_SETUP=false`).

### Рекомендуемый устойчивый целевой показатель

```bash
# ~100k events/s aggregate (запас под peak ~110k)
STRESS_SUSTAINED_RATE_PER_DEVICE=6800   # 16 × 6800 ≈ 109k
# или AUTO_CALIBRATE=true после probe
```

Peak emqtt: `EMQTT_SHARD_MAX=8`, `RATE_PER_DEVICE=32000` (formula 512k; фактический journal ~110k).

## Настройка времени выполнения (без пересборки)

`deploy/lab-stress.env` и hot-reload через runtime-settings (см. `PlatformRuntimeSettingsCatalog`):

| Окружение | Лабораторная ценность | Эффект |
|-----|-----------|--------|
| `ISPF_EVENT_JOURNAL_WRITER_THREADS` | 24 | Parallel Scylla batch flush |
| `ISPF_EVENT_JOURNAL_BATCH_SIZE` | 5000 | Batch size |
| `ISPF_EVENT_JOURNAL_CASSANDRA_PARTITION_BATCH` | 200 | Partition-local UNLOGGED batches |
| `ISPF_EVENT_JOURNAL_CASSANDRA_PARALLEL_BATCHES` | 16 | Parallel partition writers |
| `ISPF_EVENT_JOURNAL_CASSANDRA_ASYNC_COUNTER_UPDATE` | true | Non-blocking meta counter (lag в measure — норма) |
| `ISPF_DRIVER_MQTT_CALLBACK_THREADS` | 64 | L0 FIFO workers per driver |
| `ISPF_OBJECT_CHANGE_ASYNC_ENABLED` | false | Bus off for stress |

## emqtt-скамейка

Формула клиентов (скрипт):

```text
clients_per_topic = ceil(rate_per_device × interval_ms / 1000)
formula_rate      = devices × clients_per_topic × (1000 / interval_ms)
```

Строки `ISPF_EMQTT_FORMULA_RATE=` в стандартном выводе — для анализа бенчмарка. Строка `Done (formula estimate)` — **не замер** брокер.

Ограничения лаборатории:

- `--cpus EMQTT_CPU_LIMIT` (по умолчанию 1,5) — Эрланг не держит интервал 1 мс по полной формуле (~15–21% от формулы).
- Много clients на topic + QoS0 → broker `received` >> ISPF `eventsFired`.

Cleanup orphans: `bash lab-emqtt-cleanup.sh` (label `ispf.emqtt-bench=1`).

## Логи в лаборатории

| Путь | Содержание |
|------|------------|
| `~/ispf/loadtest/clean-run.log` | Factory reset benchmark |
| `~/ispf/loadtest/max-load-peak.log` | Peak / max load |
| `~/ispf/loadtest/peak-confirm.log` | Confirm без wipe |
| `~/ispf/loadtest/emqtt-pub-*.log` | emqtt stdout |

## Связанные документы

- [LOAD_TESTING.md](load-testing.md) — общие сценарии MQTT/emqtt
- [ADR-0027](decisions/0027-event-journal-ingress-fast-path.md) — EVENT_JOURNAL_ONLY
- [ADR-0026](decisions/0026-elastic-telemetry-ingress.md) — входной конвейер
- [AUTOMATION.md](automation.md) — API метрик платформы

##Сравнение продуктов VPS (ispf.iot-solutions.ru)

Те же параметры теста, что и лабораторный пик: **цель 16×32 тыс.**, **8 осколков emqtt**, мера 60 с, `EVENT_JOURNAL_ONLY`, журнал Scylla.

| | Лаборатория (84.42.21.226) | VPS прод |
|--|-------------------|----------|
| ИСФФ | 0.9.88 | 0.9.86 |
| Сцилла | 20 СМП/48Г | **1 СМП / 750М** |
| Авторы журналов | 24 | **6** |
| **события уволены** | **~110 тыс./с** | **~349/с** |
| За устройство | ~6,8 тыс./с | ~19/с |
| мета против событийFired | 100% | ~88% (очередь) |
| Script | `lab-mqtt-event-journal-multi-test.sh` | `vps-mqtt-event-journal-multi-test.sh` |

```bash
# On VPS (after scripts in /opt/ispf/loadtest)
DEVICES=16 RATE_PER_DEVICE=32000 EMQTT_SHARD_MAX=8 \
  bash /opt/ispf/loadtest/vps-mqtt-event-journal-multi-test.sh

# From dev machine
python deploy/run_vps_max_load.py
```

Размер VPS Scylla рассчитан на площадь продукта, а не на заливную нагрузку — **не сравнивайте абсолютные числа событий с лабораторными**. Путь к журналу все еще работает; Узким местом является пропускная способность Scylla и вход MQTT при QoS0 при перегрузке.
