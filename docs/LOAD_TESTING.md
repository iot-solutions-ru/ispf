# Load testing ISPF automation

Нагрузочные сценарии для измерения пропускной способности **HTTP events API** и **внутреннего конвейера автоматизации** (driver → alert rule → event journal).

Baseline зафиксирован на prod VPS `ispf.iot-solutions.ru`, версия **0.9.18**, июнь 2026.

**Абсолютная пропускная способность** (events/s, samples/s) зависит от CPU, диска, journal/historian store и настроек coalesce — таблицы ниже **не являются SLA**; используйте их для сравнения режимов на одном стенде, а не как переносимые цифры.

См. также [OBSERVABILITY.md](OBSERVABILITY.md) — Prometheus scrape и OTLP export.

## Три контура

| Контур | Скрипт | Что измеряет |
|--------|--------|--------------|
| **HTTP** | `deploy/events-load-test.py` | `POST /api/v1/events/fire` + `GET /api/v1/events` с клиента |
| **Internal (poll)** | `deploy/events-internal-load-test.py` | Virtual driver → `sineWave` → alert → journal |
| **MQTT ingress (subscribe)** | `deploy/mqtt-ingress-load-test.py` | Реальный брокер → mqtt driver **подписка** → alert → journal |
| **MQTT ingress (push, lab)** | `--mode push` | Синтетический publisher → local Mosquitto |
| **MQTT event journal (internal)** | `deploy/mqtt-event-journal-test-remote.sh` | mqtt driver → `EVENT_JOURNAL_ONLY` → `fireIngress` → journal |
| **MQTT event journal (HTTP tap)** | `deploy/mqtt-event-ingest-test-remote.sh` | External subscriber → `POST /events/fire` (API overhead baseline) |

**Перед каждым прогоном** скрипты останавливают фоновую нагрузку и loadtest-фикстуры:

- **Драйверы:** все DEVICE (рекурсивно: mini-TEC, demo-sensor, mqtt-lab, …), кроме `platform-metrics-probe`
- **Alert rules:** отключаются все не-loadtest; loadtest rules удаляются (mqtt-тест)
- **Correlators / schedules:** отключаются все не-loadtest

```powershell
python deploy/loadtest-cleanup.py
python deploy/loadtest-cleanup.py --purge-mqtt --purge-virtual
python deploy/loadtest-cleanup.py --keep-background   # только loadtest, без отключения demo
```

После теста demo-объекты остаются в дереве; alert rules и schedules нужно включить вручную или перезапустить сервер (bootstrap mini-TEC не пересоздаёт уже существующие правила, но драйверы mini-TEC снова стартуют при рестарте).

## MQTT ingress — event journal (internal fast path)

Сценарий **одно ingress-обновление → одна запись в `event_history`**: mqtt driver, режим `EVENT_JOURNAL_ONLY`, без alert rules и без HTTP.

См. [ADR-0027](decisions/0027-event-journal-ingress-fast-path.md), [ADR-0026](decisions/0026-elastic-telemetry-ingress.md).

```bash
# VPS
bash /opt/ispf/loadtest/mqtt-event-journal-test-remote.sh

# Параметры: RATE, PHASE, WARMUP, DEVICE, TOPIC, EVENT
RATE=10000 PHASE=60 WARMUP=15 bash /opt/ispf/loadtest/mqtt-event-journal-test-remote.sh
```

Setup (один датчик + событие `messageReceived`):

```powershell
python deploy/setup-mqtt-event-journal.py --base-url http://127.0.0.1:8080
```

| Параметр | Default | Описание |
|----------|---------|----------|
| `telemetryPublishMode` | `EVENT_JOURNAL_ONLY` | Пропуск historian и object-change bus |
| `ingressEventName` | `messageReceived` | Descriptor на объекте |
| `telemetryCoalesceMs` | `1` | Lab: почти 1:1 message→event |

**HTTP tap** (`deploy/mqtt-event-ingest-tap.py`, `mqtt-event-ingest-test-remote.sh`) — тот же publisher, fire через REST; baseline overhead API на том же стенде.

**HTTP tap** (`deploy/mqtt-event-ingest-tap.py`, `mqtt-event-ingest-test-remote.sh`) — тот же publisher, fire через REST; baseline overhead API на том же стенде.

### Lab stress (Scylla, multi-device emqtt)

Dedicated lab host (`84.42.21.226`), **EVENT_JOURNAL_ONLY**, 16 mqtt drivers, journal store **Scylla**. Полная документация: **[LAB_EVENT_JOURNAL_STRESS.md](LAB_EVENT_JOURNAL_STRESS.md)**.

| Metric (peak, 8 emqtt shards) | Value |
|---------------------------------|-------|
| Journal (eventsFired / Scylla meta) | **~110k events/s** |
| Per device | **~6.8k events/s** |
| eventsFired → flushed → meta | **100%** (no loss on ISPF path) |
| Limiting factor | Scylla CPU (~108%) |

Benchmark script reports **three efficiency layers**: vs configured MQTT target (reference only), vs emqtt formula, and **ISPF capture (eventsFired / Mosquitto delivered)** — use the latter to verify end-to-end delivery. Requires `sys_interval 1` in `deploy/mosquitto/mosquitto.conf`.

```bash
# Lab host
bash lab-mqtt-event-journal-multi-test.sh
DEVICES=16 RATE_PER_DEVICE=32000 EMQTT_SHARD_MAX=8 bash lab-mqtt-event-journal-multi-test.sh
AUTO_CALIBRATE=true bash lab-mqtt-event-journal-multi-test.sh
```

## MQTT ingress — historian (default)

Сценарий **датчик → дашборд**: mqtt driver подписывается на топик, `TELEMETRY_ONLY` пишет в `variable_samples` (Timescale), **без** alert/correlator/workflow.

```powershell
# Lab: Mosquitto на VPS + synthetic publisher
python deploy/mqtt-ingress-load-test.py --mode push --broker-url tcp://127.0.0.1:1883 `
  --devices 4 --messages-per-second 2000 --telemetry-coalesce-ms 50 `
  --publish-via-ssh root@ispf.iot-solutions.ru --skip-monitor-setup
```

| Флаг | Default | Описание |
|------|---------|----------|
| `--mode` | `subscribe` | `subscribe` = реальный брокер; `push` = lab publisher |
| `--telemetry-coalesce-ms` | `50` | Частота сэмплов в historian (per-device) |
| `--automation` | false | Старый режим: FULL + alert rules → event journal |
| `--publish-via-ssh` | — | push mode: publisher на VPS |

Модель `mqtt-sensor-v1`: `temperature` с `historyEnabled=true`. Binding `double(raw)` → `temperature.value` для chart widget.

**Ограничение prod:** `ispf.variable-history.min-interval-ms` (default **5000**) — debounce записи в БД. Для high-rate loadtest: `ISPF_VARIABLE_HISTORY_MIN_INTERVAL_MS=100` в `/opt/ispf/ispf-server.env` + restart (см. `application.yml`).

Реальный брокер `m5.wqtt.ru` — см. subscribe mode ниже; нужны MQTT credentials.

### Coalesce sweep (historian)

**MQTT historian** — per-device `telemetryCoalesceMs`, `TELEMETRY_ONLY`:

```powershell
python deploy/mqtt-coalesce-sweep.py --messages-per-second 2000 --phase-seconds 40
```

**Automation benchmark** (legacy): `--automation` или sweep ниже.

**Virtual driver automation** — глобальный `ISPF_RUNTIME_TELEMETRY_COALESCE_MS` + рестарт `ispf-server`:

```powershell
python deploy/events-coalesce-sweep.py --coalesce-ms 250,100,50,25,10,5,1
```

Per-device override в loadtest: `--telemetry-coalesce-ms 1` в `mqtt-ingress-load-test.py`.

#### Baseline sweep MQTT historian (0.9.24, TELEMETRY_ONLY, min-interval=100ms, 4 dev, ~2k msg/s)

| telemetryCoalesceMs | Samples/s | Alert/s | TelQ |
|---------------------|-----------|---------|------|
| 250 | ~139 | 0 | 0 |
| 100 | ~160 | 0 | 0 |
| 50 | ~165 | 0 | 0 |
| 5 | **~166** | 0 | 0 |
| 1 | ~157 | 0 | 0 |

Report: `deploy/mqtt-coalesce-sweep-report-1782385852.json`. Prod env: `ISPF_VARIABLE_HISTORY_MIN_INTERVAL_MS=100`.

*(С `min-interval-ms=5000` (до 0.9.24) samples/s был ~1.2 при том же publisher — debounce historian, не MQTT.)*

#### Baseline sweep MQTT automation (0.9.23, FULL+alerts, 4 devices, publisher ~2k msg/s, ClickHouse)

| telemetryCoalesceMs | Events/s | Alert/s | Очереди (auto/journal) |
|---------------------|----------|---------|-------------------------|
| 250 | ~30 | ~30 | 0 / 0 |
| 100 | ~54 | ~54 | 0 / 0 |
| 50 | ~87 | ~85 | 0 / 0 |
| 25 | ~76 | ~73 | 0 / 0 |
| 10 | ~78 | ~78 | 0 / 0 |
| 5 | ~102 | ~101 | 0 / 0 |
| 1 | **~108** | **~107** | 0 / 0 |

Report: `deploy/mqtt-coalesce-sweep-report-1782383597.json`.

**Вывод:** при `coalesce-ms=250` потолок ~30 events/s (4 dev) — упирается в coalescer. При `coalesce-ms≤5` потолок ~**100–108 events/s** (~27/dev) — coalesce уже не лимит; дальше упирается конвейер automation (binding + alert + journal), очереди при этом не растут. Publisher **10k msg/s** при `coalesce=1` даёт **хуже** (~28 events/s) — перегруз ingress (MQTT callback + coalescer) деградирует throughput.

### Baseline (0.9.23, MQTT subscribe + lab push, 4 devices, ClickHouse journal)

| Publisher | Broker | Events/s | Alert fires/s |
|-----------|--------|----------|---------------|
| ~20 msg/s (synthetic) | `tcp://127.0.0.1:1883` (VPS Mosquitto) | ~20.0 | ~20.1 |

*(4× `loadtest-mqtt-dev-*`, mqtt driver RUNNING, condition `self.temperature["value"] > -1000.0`. Report `deploy/mqtt-ingress-load-test-report-1782382991.json`. Реальный `m5.wqtt.ru` на prod: TCP OK, MQTT connect `not authorised` — драйверы 0/4, events/s 0.)*

Lab push (локальный Mosquitto, `--mode push`) — синтетический publisher, см. `mqtt-loadtest-publisher.py`.

### Ingress historian (`--ingress-history-only`)

Benchmark **gateway `lastIngress.raw` only** — no child sensors, no `dispatchTelemetry` binding:

```powershell
python deploy/mqtt-ingress-load-test.py --mode push --broker-url tcp://127.0.0.1:1883 `
  --ingress-history-only --devices 4 --messages-per-second 2000 --telemetry-coalesce-ms 1 `
  --publish-via-ssh root@ispf.iot-solutions.ru --skip-monitor-setup
```

Samples/s measured via `GET .../variables/history` for `lastIngress.raw` on the gateway (not global `sampleCount`). Driver uses `ingressTopicLanes=false` so parallel dispatch does not suppress historian publication.

### High-rate publisher (emqtt-bench)

Для **>2k msg/s** используйте [emqtt-bench](https://github.com/emqx/emqtt-bench) через Docker на VPS (`deploy/mqtt-emqtt-bench.sh`):

```powershell
# Deploy Mosquitto + emqtt-bench image + scripts
.\deploy\vps-mqtt-broker-deploy.ps1

# Ingress load test с emqtt-bench (рекомендуется --gateway)
python deploy/mqtt-ingress-load-test.py --mode push --broker-url tcp://127.0.0.1:1883 `
  --devices 4 --messages-per-second 10000 --telemetry-coalesce-ms 1 `
  --gateway --publisher emqtt-bench --emqtt-interval-ms 10 `
  --publish-via-ssh root@ispf.iot-solutions.ru --skip-monitor-setup

# Standalone на VPS (50k msg/s, 4 топика):
# ssh root@ispf.iot-solutions.ru 'bash /opt/ispf/loadtest/mqtt-emqtt-bench.sh --devices 4 --messages-per-second 50000 --duration-seconds 30'
```

| Флаг | Default | Описание |
|------|---------|----------|
| `--publisher` | `python` | `emqtt-bench` — Docker на VPS; `python` — paho (~1.5k msg/s) |
| `--emqtt-interval-ms` | `10` | `-I` emqtt-bench (100 msg/s на клиента при 10 ms) |

Формула: `msg/s ≈ devices × clients_per_topic × (1000 / interval_ms)`.

**Важно:** строка `Done (formula estimate)` и `ISPF_EMQTT_FORMULA_RATE=` в stdout — **расчёт**, не замер broker. Для lab multi-device benchmark с Mosquitto `$SYS` и ISPF metrics см. [LAB_EVENT_JOURNAL_STRESS.md](LAB_EVENT_JOURNAL_STRESS.md).

## Internal load test (virtual driver)

## Подготовка окружения

### 1. Loadtest-устройства

```powershell
python deploy/vps-load-test.py --seed-only --devices 60
```

Создаёт `root.platform.devices.loadtest-dev-*` (шаблон `virtual-lab-v1`).

### 2. Мониторинг (probe + dashboard)

```powershell
python deploy/setup-platform-metrics-monitor.py --base-url https://ispf.iot-solutions.ru
```

- Probe: `root.platform.devices.platform-metrics-probe`
- Dashboard: `root.platform.dashboards.platform-metrics`
- Синхронизирует `GET /api/v1/platform/metrics` → переменные probe (events/s, alert fires/s, heap, DB pool, queue depth)

Для prod включите probe syncer на VPS или запускайте syncer из load-test скриптов (они делают это автоматически).

### 3. Platform metrics API

Admin-only: `GET /api/v1/platform/metrics` — секция `automation`:

| Поле | Смысл |
|------|--------|
| `eventHistoryRecords` | Размер журнала событий (PostgreSQL) |
| `alertFiresTotal` | Счётчик срабатываний alert rules (in-memory, platform-wide) |
| `objectChangeQueueSize` | Глубина async-очереди object-change bus |
| `eventJournalQueueSize` | Очередь async writer журнала |

Prometheus: `/actuator/prometheus` (admin role) — counters `ispf.events.fired.total`, `ispf.alert.fires.total`, gauges `ispf.object_change.queue.size{lane=telemetry|automation|total}`, `ispf.event_history.records`, `ispf.workflow_instances.running`, `ispf.variable_history.samples`, `ispf.drivers.active`, `ispf.database.connections.*`.

## HTTP load test

```powershell
python deploy/events-load-test.py `
  --base-url https://ispf.iot-solutions.ru `
  --concurrency 40 `
  --duration-seconds 60
```

**Baseline (0.9.5, 60 devices, concurrency 40):** ~147–164 RPS на `POST /events/fire`.

JUnit-аналог: `EventFireLoadTest` (150 concurrent HTTP).

**CI gate (BL-113):** workflow `.github/workflows/load-test.yml` — nightly + `workflow_dispatch`, прогон `EventFireLoadTest` + `ListDevicesLoadTest`, порог `ISPF_LOAD_P99_CEILING_MS` (default 3000 ms), артефакты логов Gradle. Шаги Gradle с `set -o pipefail`, чтобы падение тестов не маскировалось `tee`.

## Internal load test

```powershell
# Авто-cleanup: останавливает mqtt loadtest, пересоздаёт virtual alert rules
python deploy/events-internal-load-test.py --skip-monitor-setup --poll-ms 1000 --phase-seconds 60
```

Файл `deploy/loadtest-sinewave-condition.txt`:

```cel
self.sineWave["value"] > -1000.0
```

### Параметры

| Флаг | Default | Описание |
|------|---------|----------|
| `--warmup-seconds` | 15 | Ожидание после configure driver (coalesce + async journal) |
| `--skip-cleanup` | false | Не останавливать mqtt loadtest перед прогоном |
| `--condition-expr` | `true` | CEL для alert rules |
| `--condition-expr-file` | — | Условие из файла |
| `--poll-ms` | `3000,1000,500` | Интервалы опроса virtual driver |
| `--telemetry-mix-ratio` | `0` | Доля устройств в `TELEMETRY_ONLY` (0.5 = половина без automation lane) |
| `--max-devices` | 0 (all) | Лимит loadtest-устройств |

### Baseline (0.9.23, 60 devices, poll=1000ms, warmup=45s, ClickHouse journal)

| conditionExpr | Events/s | Alert fires/s |
|---------------|----------|---------------|
| `self.sineWave["value"] > -1000.0` | ~29.4 | ~17.5 |

*(0.9.23 P3b: `ISPF_EVENT_JOURNAL_STORE=clickhouse`, HTTP JSONEachRow batch insert. Report `deploy/events-internal-load-test-report-1782382458.json`. На 60 dev throughput ниже, чем у Timescale JDBC (0.9.18) — ожидаемо: выигрыш ClickHouse на больших объёмах journal, retention и аналитике, не на micro-benchmark с локальным PG.)*

### Baseline (0.9.18, 60 devices, poll=1000ms, warmup=45s, coalesce=250ms)

| conditionExpr | Events/s | Alert fires/s |
|---------------|----------|---------------|
| `self.sineWave["value"] > -1000.0` | ~37.9 | ~25.6 |

*(0.9.18 P3a: `event_history` Timescale hypertable + compression segmentby `object_path`, retention 90d. Report `deploy/events-internal-load-test-report-1782378017.json`. Прирост vs 0.9.17 скромный на 60 dev — основной выигрыш Timescale на больших объёмах journal и retention, не на micro-benchmark.)*

### Baseline (0.9.17, 60 devices, poll=1000ms, warmup=45s, coalesce=250ms)

Per-device **`telemetryPublishMode`**: `FULL` (default) или `TELEMETRY_ONLY` (RAM + historian, без alert/workflow на coalesced tick).

| Режим | conditionExpr | Events/s | Alert fires/s |
|-------|---------------|----------|---------------|
| all FULL | `self.sineWave["value"] > -1000.0` | ~36.8 | ~24.5 |
| 50% TELEMETRY_ONLY | same | ~30.1 | ~24.7 |

*(0.9.17: выбор режима на driver binding; `PUT /api/v1/drivers/runtime/configure` поля `telemetryPublishMode`, `telemetryCoalesceMs`. Alert fires/s — глобальный счётчик (фон mini-TEC); journal падает при TELEMETRY_ONLY, automation lane разгружается.)*

Configure example:

```json
{
  "driverId": "virtual",
  "pollIntervalMs": 1000,
  "telemetryPublishMode": "TELEMETRY_ONLY",
  "autoStart": true
}
```

### Baseline (0.9.16, 60 devices, poll=1000ms, warmup=45s, coalesce=250ms)

| conditionExpr | Events/s | Alert fires/s |
|---------------|----------|---------------|
| `self.sineWave["value"] > -1000.0` | ~41.8 | ~29.0 |

*(0.9.16: JDBC batch journal, O(1) event counter, fireAutomation без TX, elastic workers, 6 journal writers.)*

### Baseline (0.9.15, 60 devices, poll=1000ms, warmup=30s, coalesce=250ms)

| conditionExpr | Events/s | Alert fires/s |
|---------------|----------|---------------|
| `self.sineWave["value"] > -1000.0` | ~39.9 | ~27.6 |

*(0.9.14 reference: ~27.4 events/s — in-memory alert runtime. 0.9.15 adds CEL cache, multi-writer journal, coalesce 250ms.)*

### Baseline (0.9.14, 60 devices, poll=1000ms, warmup=30s)

| conditionExpr | Events/s | Alert fires/s |
|---------------|----------|---------------|
| `self.sineWave["value"] > -1000.0` | ~27.4 | ~15.7 |

*(0.9.13 reference: ~26.0 events/s — in-memory alert runtime state.)*

### Baseline (0.9.9, 60 devices, poll=1000ms, warmup=15s)

| conditionExpr | Events/s | Alert fires/s |
|---------------|----------|---------------|
| `true` | ~20.7 | ~20.7 |
| `self.sineWave["value"] > -1000.0` | ~21.9 | ~22.1 |

*(0.9.5 reference: `true` ~20.7, realistic CEL ~17.4 events/s — см. историю в git.)*

### Важно для интерпретации

1. **Drivers** должны быть в RUNNING с `autoStart: true` (`PUT /api/v1/drivers/runtime/configure`).
2. **`alertFiresTotal`** — глобальный счётчик; перед прогоном cleanup отключает mini-TEC/demo alerts и останавливает их драйверы.
3. **`eventHistoryRecords`** — async write; используйте warmup перед измерением.
4. **Не смешивайте** virtual poll (`loadtest-dev-*`) и mqtt subscribe (`loadtest-mqtt-dev-*`) без cleanup — скрипты делают это по умолчанию.
5. Dot-notation `self.sineWave.value` в CEL для alert rules ненадёжна; предпочитайте `self.sineWave["value"]` или binding → derived var → alert (как `demo-sensor-01` / `alarmActive`).

## Архитектура конвейера (кратко)

См. [0014 automation pipeline evolution](decisions/0014-automation-pipeline-evolution.md):

- **Sync:** bindings, WebSocket
- **Async bus (dual lane):** telemetry (historian) vs automation (alerts, workflows, correlators)
- **Coalesce:** `RuntimeTelemetryCoalescer` (global default + per-device override) перед publish `ObjectChangeEvent`
- **Multi-level ingress:** L0 driver buffer → L1 server buffer → L3 ingress queue → L4 coalesce → L5 historian **или** L5′ event journal fast path ([0026](decisions/0026-elastic-telemetry-ingress.md), [0027](decisions/0027-event-journal-ingress-fast-path.md))
- **Telemetry publish mode:** `FULL` | `TELEMETRY_ONLY` | `EVENT_JOURNAL_ONLY` на driver binding
- **Alert path:** `AlertRuleListener` → CEL → `EventService.fire` → `EventJournalAsyncWriter`
- **Ingress journal path:** `TelemetryEventJournalFastPath` → `EventService.fireIngress` → `EventJournalAsyncWriter` (без HTTP и без alert CEL)
- **Alert runtime state:** in-memory (`AlertRuleRuntimeStore`); periodic flush to object tree (default 30 s), not on every evaluation
- **Event journal storage:** prod VPS — `ISPF_EVENT_JOURNAL_STORE=clickhouse` ([0016](decisions/0016-clickhouse-event-journal.md)); relational data остаётся в PostgreSQL. Fallback: `jdbc` + Timescale ([0015](decisions/0015-event-history-timescale.md)). Retention `ISPF_EVENT_JOURNAL_RETENTION_DAYS` (default 90). Скрипты: `deploy/vps-clickhouse-setup.sh`, `deploy/vps-clickhouse-verify.sh`, откат — `deploy/vps-event-journal-jdbc.sh`.

## Связанные документы

- [AUTOMATION.md](AUTOMATION.md) — события, alert rules, correlators
- [DEPLOYMENT.md](DEPLOYMENT.md) — VPS deploy, env vars
- [API.md](API.md) — `/api/v1/platform/metrics`
- [TESTING.md](TESTING.md) — JUnit / CI
