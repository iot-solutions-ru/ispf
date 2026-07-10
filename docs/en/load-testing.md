> **Language:** Canonical English. Russian edition: [ru/load-testing.md](../ru/load-testing.md).

# Load testing ISPF automation

Load scenarios to measure throughput of **HTTP events API** and the **internal automation pipeline** (driver → alert rule → event journal).

Baseline recorded on prod stand, version **0.9.18**, June 2026. **Deploy profile** for load-test — throughput ([demostands](demostands.md)); do not use idle/edge env.

**Absolute throughput** (events/s, samples/s) depends on CPU, disk, journal/historian store and coalesce settings — tables below are **not SLA**; use them to compare modes on one stand, not as portable numbers.

See also [observability](observability.md) — Prometheus scrape and OTLP export.

## Three paths

| Path | Script | What it measures |
|--------|--------|--------------|
| **HTTP** | `deploy/events-load-test.py` | `POST /api/v1/events/fire` + `GET /api/v1/events` from client |
| **Internal (poll)** | `deploy/events-internal-load-test.py` | Virtual driver → `sineWave` → alert → journal |
| **MQTT ingress (subscribe)** | `deploy/mqtt-ingress-load-test.py` | Real broker → mqtt driver **subscribe** → alert → journal |
| **MQTT ingress (push, lab)** | `--mode push` | Synthetic publisher → local Mosquitto |
| **MQTT event journal (internal)** | `deploy/mqtt-event-journal-test-remote.sh` | mqtt driver → `EVENT_JOURNAL_ONLY` → `fireIngress` → journal |
| **MQTT event journal (HTTP tap)** | `deploy/mqtt-event-ingest-test-remote.sh` | External subscriber → `POST /events/fire` (API overhead baseline) |

**Before each run** scripts stop background load and loadtest fixtures:

- **Drivers:** all DEVICE (recursively: mini-TEC, demo-sensor, mqtt-lab, …), except `platform-metrics-probe`
- **Alert rules:** all non-loadtest disabled; loadtest rules removed (mqtt test)
- **Correlators / schedules:** all non-loadtest disabled

```powershell
python deploy/loadtest-cleanup.py
python deploy/loadtest-cleanup.py --purge-mqtt --purge-virtual
python deploy/loadtest-cleanup.py --keep-background   # loadtest only, without disabling demo
```

After test demo objects remain in tree; alert rules and schedules must be re-enabled manually or restart server (bootstrap mini-TEC does not recreate existing rules, but mini-TEC drivers restart on server restart).

## MQTT ingress — event journal (internal fast path)

Scenario **one ingress update → one `event_history` record**: mqtt driver, mode `EVENT_JOURNAL_ONLY`, no alert rules and no HTTP.

See [0027-event-journal-ingress-fast-path](decisions/0027-event-journal-ingress-fast-path.md), [0026-elastic-telemetry-ingress](decisions/0026-elastic-telemetry-ingress.md).

```bash
# VPS
bash /opt/ispf/loadtest/mqtt-event-journal-test-remote.sh

# Params: RATE, PHASE, WARMUP, DEVICE, TOPIC, EVENT
RATE=10000 PHASE=60 WARMUP=15 bash /opt/ispf/loadtest/mqtt-event-journal-test-remote.sh
```

Setup (one sensor + `messageReceived` event):

```powershell
python deploy/setup-mqtt-event-journal.py --base-url http://127.0.0.1:8080
```

| Parameter | Default | Description |
|----------|---------|----------|
| `telemetryPublishMode` | `EVENT_JOURNAL_ONLY` | Skip historian and object-change bus |
| `ingressEventName` | `messageReceived` | Descriptor on object |
| `telemetryCoalesceMs` | `1` | Lab: nearly 1:1 message→event |

**HTTP tap** (`deploy/mqtt-event-ingest-tap.py`, `mqtt-event-ingest-test-remote.sh`) — same publisher, fire via REST; API overhead baseline on same stand.

### VPS fair single-device bench (event journal)

1× mqtt, `EVENT_JOURNAL_ONLY`, `ingressCoalesceEnabled=false`, Scylla journal. Comparable emqtt phases for regression on prod hardware:

```bash
# On VPS (after scp deploy/vps-ispf-fair-*.sh)
bash /opt/ispf/loadtest/vps-ispf-fair-run.sh
```

| Phase | emqtt | Typical eventsFired/s (VPS 0.9.87, Scylla 1 SMP) |
|-------|-------|--------------------------------------------------|
| Sustained | 65s, 20 clients, 10ms | **~1.9k** (elastic L0 + L5′) |
| Peak | 65s, 32 clients, 1ms | high delta; journal queue ≥500k needed |

Optional tuning before peak: `bash deploy/vps-event-journal-peak-tuning.sh` (queue 500k, batch 1k, flush 20ms). Metrics: `eventsFiredTotal`, `eventJournalSyncFallbackTotal`, `eventJournalQueueSize` in `/api/v1/platform/metrics` (section `automation`). Scylla `COUNT(*)` after peak may timeout — see [0027-event-journal-ingress-fast-path](decisions/0027-event-journal-ingress-fast-path.md).

### Lab stress (Scylla, multi-device emqtt)

Dedicated lab host (`84.42.21.226`), **EVENT_JOURNAL_ONLY**, 16 mqtt drivers, journal store **Scylla**. Full documentation: **[lab-event-journal-stress](lab-event-journal-stress.md)**.

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

### VPS prod (Scylla journal, smaller Scylla footprint)

Same methodology on `ispf.iot-solutions.ru` — multi-device: `deploy/vps-mqtt-event-journal-multi-test.sh`, orchestration `deploy/run_vps_max_load.py`. Single-device fair bench: `deploy/vps-ispf-fair-bench.sh` ([0027-event-journal-ingress-fast-path](decisions/0027-event-journal-ingress-fast-path.md)).

| Metric | VPS prod (context) | Lab (reference) |
|--------|-------------------|-----------------|
| 16× peak (pre-elastic, 2026-07-04) | **~349/s** eventsFired | ~110k/s |
| 1× sustained (elastic 0.9.87+, 2026-07-05) | **~1.9k/s** eventsFired | — |
| Scylla | 1 SMP / 750M | 20 SMP / 48G |

Absolute rates are **not comparable** across hosts; use fair bench for single-device regression, multi-device script for fleet-shaped load.

## MQTT ingress — historian (default)

Scenario **sensor → dashboard**: mqtt driver subscribes to topic, `TELEMETRY_ONLY` writes to `variable_samples` (Timescale), **without** alert/correlator/workflow.

```powershell
# Lab: Mosquitto on VPS + synthetic publisher
python deploy/mqtt-ingress-load-test.py --mode push --broker-url tcp://127.0.0.1:1883 `
  --devices 4 --messages-per-second 2000 --telemetry-coalesce-ms 50 `
  --publish-via-ssh root@ispf.iot-solutions.ru --skip-monitor-setup
```

| Flag | Default | Description |
|------|---------|----------|
| `--mode` | `subscribe` | `subscribe` = real broker; `push` = lab publisher |
| `--telemetry-coalesce-ms` | `50` | Sample rate in historian (per-device) |
| `--automation` | false | Legacy mode: FULL + alert rules → event journal |
| `--publish-via-ssh` | — | push mode: publisher on VPS |

Model `mqtt-sensor-v1`: `temperature` with `historyEnabled=true`. Binding `double(raw)` → `temperature.value` for chart widget.

**Prod constraint:** `ispf.variable-history.min-interval-ms` (default **5000**) — DB write debounce. For high-rate loadtest: `ISPF_VARIABLE_HISTORY_MIN_INTERVAL_MS=100` in `/opt/ispf/ispf-server.env` + restart (see `application.yml`).

Real broker `m5.wqtt.ru` — see subscribe mode below; MQTT credentials required.

### Coalesce sweep (historian)

**MQTT historian** — per-device `telemetryCoalesceMs`, `TELEMETRY_ONLY`:

```powershell
python deploy/mqtt-coalesce-sweep.py --messages-per-second 2000 --phase-seconds 40
```

**Automation benchmark** (legacy): `--automation` or sweep below.

**Virtual driver automation** — global `ISPF_RUNTIME_TELEMETRY_COALESCE_MS` + restart `ispf-server`:

```powershell
python deploy/events-coalesce-sweep.py --coalesce-ms 250,100,50,25,10,5,1
```

Per-device override in loadtest: `--telemetry-coalesce-ms 1` in `mqtt-ingress-load-test.py`.

#### Baseline sweep MQTT historian (0.9.24, TELEMETRY_ONLY, min-interval=100ms, 4 dev, ~2k msg/s)

| telemetryCoalesceMs | Samples/s | Alert/s | TelQ |
|---------------------|-----------|---------|------|
| 250 | ~139 | 0 | 0 |
| 100 | ~160 | 0 | 0 |
| 50 | ~165 | 0 | 0 |
| 5 | **~166** | 0 | 0 |
| 1 | ~157 | 0 | 0 |

Report: `deploy/mqtt-coalesce-sweep-report-1782385852.json`. Prod env: `ISPF_VARIABLE_HISTORY_MIN_INTERVAL_MS=100`.

*(With `min-interval-ms=5000` (before 0.9.24) samples/s was ~1.2 with same publisher — historian debounce, not MQTT.)*

#### Baseline sweep MQTT automation (0.9.23, FULL+alerts, 4 devices, publisher ~2k msg/s, ClickHouse)

| telemetryCoalesceMs | Events/s | Alert/s | Queues (auto/journal) |
|---------------------|----------|---------|-------------------------|
| 250 | ~30 | ~30 | 0 / 0 |
| 100 | ~54 | ~54 | 0 / 0 |
| 50 | ~87 | ~85 | 0 / 0 |
| 25 | ~76 | ~73 | 0 / 0 |
| 10 | ~78 | ~78 | 0 / 0 |
| 5 | ~102 | ~101 | 0 / 0 |
| 1 | **~108** | **~107** | 0 / 0 |

Report: `deploy/mqtt-coalesce-sweep-report-1782383597.json`.

**Conclusion:** at `coalesce-ms=250` ceiling ~30 events/s (4 dev) — coalescer bound. At `coalesce-ms≤5` ceiling ~**100–108 events/s** (~27/dev) — coalesce no longer limit; pipeline automation (binding + alert + journal) bound next, queues do not grow. Publisher **10k msg/s** at `coalesce=1` yields **worse** (~28 events/s) — ingress overload (MQTT callback + coalescer) degrades throughput.

### Baseline (0.9.23, MQTT subscribe + lab push, 4 devices, ClickHouse journal)

| Publisher | Broker | Events/s | Alert fires/s |
|-----------|--------|----------|---------------|
| ~20 msg/s (synthetic) | `tcp://127.0.0.1:1883` (VPS Mosquitto) | ~20.0 | ~20.1 |

*(4× `loadtest-mqtt-dev-*`, mqtt driver RUNNING, condition `self.temperature["value"] > -1000.0`. Report `deploy/mqtt-ingress-load-test-report-1782382991.json`. Real `m5.wqtt.ru` on prod: TCP OK, MQTT connect `not authorised` — drivers 0/4, events/s 0.)*

Lab push (local Mosquitto, `--mode push`) — synthetic publisher, see `mqtt-loadtest-publisher.py`.

### Ingress historian (`--ingress-history-only`)

Benchmark **gateway `lastIngress.raw` only** — no child sensors, no `dispatchTelemetry` binding:

```powershell
python deploy/mqtt-ingress-load-test.py --mode push --broker-url tcp://127.0.0.1:1883 `
  --ingress-history-only --devices 4 --messages-per-second 2000 --telemetry-coalesce-ms 1 `
  --publish-via-ssh root@ispf.iot-solutions.ru --skip-monitor-setup
```

Samples/s measured via `GET .../variables/history` for `lastIngress.raw` on the gateway (not global `sampleCount`). Driver uses `ingressTopicLanes=false` so parallel dispatch does not suppress historian publication.

### High-rate publisher (emqtt-bench)

For **>2k msg/s** use [emqtt-bench](https://github.com/emqx/emqtt-bench) via Docker on VPS (`deploy/mqtt-emqtt-bench.sh`):

```powershell
# Deploy Mosquitto + emqtt-bench image + scripts
.\deploy\vps-mqtt-broker-deploy.ps1

# Ingress load test with emqtt-bench (recommended --gateway)
python deploy/mqtt-ingress-load-test.py --mode push --broker-url tcp://127.0.0.1:1883 `
  --devices 4 --messages-per-second 10000 --telemetry-coalesce-ms 1 `
  --gateway --publisher emqtt-bench --emqtt-interval-ms 10 `
  --publish-via-ssh root@ispf.iot-solutions.ru --skip-monitor-setup

# Standalone on VPS (50k msg/s, 4 topics):
# ssh root@ispf.iot-solutions.ru 'bash /opt/ispf/loadtest/mqtt-emqtt-bench.sh --devices 4 --messages-per-second 50000 --duration-seconds 30'
```

| Flag | Default | Description |
|------|---------|----------|
| `--publisher` | `python` | `emqtt-bench` — Docker on VPS; `python` — paho (~1.5k msg/s) |
| `--emqtt-interval-ms` | `10` | `-I` emqtt-bench (100 msg/s per client at 10 ms) |

Formula: `msg/s ≈ devices × clients_per_topic × (1000 / interval_ms)`.

**Important:** line `Done (formula estimate)` and `ISPF_EMQTT_FORMULA_RATE=` in stdout — **calculation**, not broker measurement. For lab multi-device benchmark with Mosquitto `$SYS` and ISPF metrics see [lab-event-journal-stress](lab-event-journal-stress.md).

## Internal load test (virtual driver)

## Environment setup

### 1. Loadtest devices

```powershell
python deploy/vps-load-test.py --seed-only --devices 60
```

Creates `root.platform.devices.loadtest-dev-*` (template `virtual-lab-v1`).

### 2. Monitoring (probe + dashboard)

```powershell
python deploy/setup-platform-metrics-monitor.py --base-url https://ispf.iot-solutions.ru
```

- Probe: `root.platform.devices.platform-metrics-probe`
- Dashboard: `root.platform.dashboards.platform-metrics`
- Syncs `GET /api/v1/platform/metrics` → probe variables (events/s, alert fires/s, heap, DB pool, queue depth)

**Enable sync:** Admin → System → Metrics → Load diagnostics → "Sync metrics to probe device", or `PUT /api/v1/platform/diagnostics/metrics-probe` `{ "enabled": true }`. Load-test scripts enable probe via API automatically; for manual monitoring **disable** probe after test (UI unchecks on page close).

### 3. Platform metrics API

Admin-only: `GET /api/v1/platform/metrics` — `automation` section:

| Field | Meaning |
|------|--------|
| `eventHistoryRecords` | Event journal size (PostgreSQL) |
| `alertFiresTotal` | Alert rule fire counter (in-memory, platform-wide) |
| `objectChangeQueueSize` | Object-change bus async queue depth |
| `eventJournalQueueSize` | Journal async writer queue |

Prometheus: `/actuator/prometheus` (admin role) — counters `ispf.events.fired.total`, `ispf.alert.fires.total`, gauges `ispf.object_change.queue.size{lane=telemetry|automation|total}`, `ispf.event_history.records`, `ispf.workflow_instances.running`, `ispf.variable_history.samples`, `ispf.drivers.active`, `ispf.database.connections.*`.

## HTTP load test

```powershell
python deploy/events-load-test.py `
  --base-url https://ispf.iot-solutions.ru `
  --concurrency 40 `
  --duration-seconds 60
```

**Baseline (0.9.5, 60 devices, concurrency 40):** ~147–164 RPS on `POST /events/fire`.

JUnit equivalent: `EventFireLoadTest` (150 concurrent HTTP).

**CI gate (BL-113):** workflow `.github/workflows/load-test.yml` — nightly + `workflow_dispatch`, runs `EventFireLoadTest`, `ListDevicesLoadTest`, and `AnalyticsMultiTagQueryLoadTest` (BL-210), threshold `ISPF_LOAD_P99_CEILING_MS` / `ISPF_ANALYTICS_LOAD_P95_CEILING_MS` (default 3000 ms), Gradle log artifacts. Gradle steps use `set -o pipefail` so test failure is not masked by `tee`.

## Analytics platform scale gates (BL-210)

| Gate | Script / test | Default ceiling |
|------|---------------|-----------------|
| Multi-tag query (10×7d×1h) | `AnalyticsMultiTagQueryLoadTest` | p95 **3000 ms** |
| Lab multi-tag + catalog + CH | `deploy/local/tools/analytics-scale-gate.sh` | p95 **3000 ms**, catalog **50k**, CH **1B** rows |
| 50k-tag catalog seed | `deploy/local/tools/seed-analytics-scale-catalog.py` | `--tags 50000` |

Enterprise L walkthrough: [examples/analytics-platform/enterprise-l](../examples/analytics-platform/enterprise-l/README.md). SLO table: [variable-history](variable-history.md) § Analytics SLO.

## Internal load test

```powershell
# Auto-cleanup: stops mqtt loadtest, recreates virtual alert rules
python deploy/events-internal-load-test.py --skip-monitor-setup --poll-ms 1000 --phase-seconds 60
```

File `deploy/loadtest-sinewave-condition.txt`:

```cel
self.sineWave["value"] > -1000.0
```

### Parameters

| Flag | Default | Description |
|------|---------|----------|
| `--warmup-seconds` | 15 | Wait after configure driver (coalesce + async journal) |
| `--skip-cleanup` | false | Do not stop mqtt loadtest before run |
| `--condition-expr` | `true` | CEL for alert rules |
| `--condition-expr-file` | — | Condition from file |
| `--poll-ms` | `3000,1000,500` | Virtual driver poll intervals |
| `--telemetry-mix-ratio` | `0` | Share of devices in `TELEMETRY_ONLY` (0.5 = half without automation lane) |
| `--max-devices` | 0 (all) | Loadtest device limit |

### Baseline (0.9.23, 60 devices, poll=1000ms, warmup=45s, ClickHouse journal)

| conditionExpr | Events/s | Alert fires/s |
|---------------|----------|---------------|
| `self.sineWave["value"] > -1000.0` | ~29.4 | ~17.5 |

*(0.9.23 P3b: `ISPF_EVENT_JOURNAL_STORE=clickhouse`, HTTP JSONEachRow batch insert. Report `deploy/events-internal-load-test-report-1782382458.json`. On 60 dev throughput lower than Timescale JDBC (0.9.18) — expected: ClickHouse wins on large journal volumes, retention and analytics, not micro-benchmark with local PG.)*

### Baseline (0.9.18, 60 devices, poll=1000ms, warmup=45s, coalesce=250ms)

| conditionExpr | Events/s | Alert fires/s |
|---------------|----------|---------------|
| `self.sineWave["value"] > -1000.0` | ~37.9 | ~25.6 |

*(0.9.18 P3a: `event_history` Timescale hypertable + compression segmentby `object_path`, retention 90d. Report `deploy/events-internal-load-test-report-1782378017.json`. Gain vs 0.9.17 modest on 60 dev — main Timescale win on large journal volumes and retention, not micro-benchmark.)*

### Baseline (0.9.17, 60 devices, poll=1000ms, warmup=45s, coalesce=250ms)

Per-device **`telemetryPublishMode`**: `FULL` (default) or `TELEMETRY_ONLY` (RAM + historian, no alert/workflow on coalesced tick).

| Mode | conditionExpr | Events/s | Alert fires/s |
|-------|---------------|----------|---------------|
| all FULL | `self.sineWave["value"] > -1000.0` | ~36.8 | ~24.5 |
| 50% TELEMETRY_ONLY | same | ~30.1 | ~24.7 |

*(0.9.17: mode selection on driver binding; `PUT /api/v1/drivers/runtime/configure` fields `telemetryPublishMode`, `telemetryCoalesceMs`. Alert fires/s — global counter (mini-TEC background); journal drops with TELEMETRY_ONLY, automation lane offloaded.)*

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

*(0.9.16: JDBC batch journal, O(1) event counter, fireAutomation without TX, elastic workers, 6 journal writers.)*

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

*(0.9.5 reference: `true` ~20.7, realistic CEL ~17.4 events/s — see git history.)*

### Interpretation notes

1. **Drivers** must be RUNNING with `autoStart: true` (`PUT /api/v1/drivers/runtime/configure`).
2. **`alertFiresTotal`** — global counter; before run cleanup disables mini-TEC/demo alerts and stops their drivers.
3. **`eventHistoryRecords`** — async write; use warmup before measurement.
4. **Do not mix** virtual poll (`loadtest-dev-*`) and mqtt subscribe (`loadtest-mqtt-dev-*`) without cleanup — scripts do this by default.
5. Dot-notation `self.sineWave.value` in CEL for alert rules is unreliable; prefer `self.sineWave["value"]` or binding → derived var → alert (like `demo-sensor-01` / `alarmActive`).

## Pipeline architecture (brief)

See [0014 automation pipeline evolution](decisions/0014-automation-pipeline-evolution.md):

- **Sync:** bindings, WebSocket
- **Async bus (dual lane):** telemetry (historian) vs automation (alerts, workflows, correlators)
- **Coalesce:** `RuntimeTelemetryCoalescer` (global default + per-device override) before publish `ObjectChangeEvent`
- **Multi-level ingress:** L0 driver buffer → L1 server buffer → L3 ingress queue → L4 coalesce → L5 historian **or** L5′ event journal fast path ([0026-elastic-telemetry-ingress](decisions/0026-elastic-telemetry-ingress.md), [0027-event-journal-ingress-fast-path](decisions/0027-event-journal-ingress-fast-path.md))
- **Telemetry publish mode:** `FULL` | `TELEMETRY_ONLY` | `EVENT_JOURNAL_ONLY` on driver binding
- **Alert path:** `AlertRuleListener` → CEL → `EventService.fire` → `EventJournalAsyncWriter`
- **Ingress journal path:** `TelemetryEventJournalFastPath` → `EventService.fireIngress` → `EventJournalAsyncWriter` (no HTTP and no alert CEL)
- **Alert runtime state:** in-memory (`AlertRuleRuntimeStore`); periodic flush to object tree (default 30 s), not on every evaluation
- **Event journal storage:** prod VPS — `ISPF_EVENT_JOURNAL_STORE=clickhouse` ([0016-clickhouse-event-journal](decisions/0016-clickhouse-event-journal.md)); relational data remains in PostgreSQL. Fallback: `jdbc` + Timescale ([0015-event-history-timescale](decisions/0015-event-history-timescale.md)). Retention `ISPF_EVENT_JOURNAL_RETENTION_DAYS` (default 90). Scripts: `deploy/vps-clickhouse-setup.sh`, `deploy/vps-clickhouse-verify.sh`, rollback — `deploy/vps-event-journal-jdbc.sh`.

## Related documents

- [automation](automation.md) — events, alert rules, correlators
- [deployment](deployment.md) — VPS deploy, env vars
- [api](api.md) — `/api/v1/platform/metrics`
- [testing](testing.md) — JUnit / CI
