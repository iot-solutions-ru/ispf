> **Язык:** русская версия (вычитка). Канонический английский: [en/decisions/0027-event-journal-ingress-fast-path.md](../../en/decisions/0027-event-journal-ingress-fast-path.md).

# ADR-0027: Event journal ingress fast path

## Статус

Принято (2026-07-04)

## Контекст

High-rate driver telemetry можно сохранять как **event journal** records (одно ingress update → одна строка `event_history`), а не только как historian samples или automation-side effects.

Под flood load наблюдались два anti-pattern:

1. **HTTP per message** — external tap с `POST /api/v1/events/fire` добавляет REST serialization и connection overhead поверх journal I/O; полезно для API smoke tests, но не отражает in-process driver ingress.
2. **FULL automation path** — coalesced telemetry → object-change bus → alert CEL → `EventService.fire` связывает journal throughput с binding evaluation и dual-lane scheduling.

Production event journal уже использует async batch writers ([0016-clickhouse-event-journal](0016-clickhouse-event-journal.md), [0015-event-history-timescale](0015-event-history-timescale.md)). Не хватало **driver-native hot path**, который ставит journal writes в очередь без HTTP и без traversing telemetry/automation bus.

## Решение

### Telemetry publish mode `EVENT_JOURNAL_ONLY`

Третий per-device mode рядом с `FULL` и `TELEMETRY_ONLY`:

| Mode | RAM live value | Historian | Object-change bus | Event journal |
|------|----------------|-----------|-------------------|---------------|
| `FULL` | yes | yes (via bus / bindings) | yes (automation lane) | via alerts, API, correlators |
| `TELEMETRY_ONLY` | yes | yes (fast path) | telemetry lane only | no |
| `EVENT_JOURNAL_ONLY` | yes | no | skipped | yes (fast path) |

Настройка на driver binding:

```json
{
  "driverId": "mqtt",
  "telemetryPublishMode": "EVENT_JOURNAL_ONLY",
  "telemetryCoalesceMs": 1,
  "configuration": {
    "brokerUrl": "tcp://127.0.0.1:1883",
    "ingressEventName": "messageReceived"
  }
}
```

- `ingressEventName` — event descriptor на устройстве (default `messageReceived`). Payload включает MQTT `raw`, когда он присутствует в updated variable.
- Object должен определить event (`EventDescriptor`) до fire; loadtest model `mqtt-sensor-v1` регистрирует `messageReceived`.

### `TelemetryEventJournalFastPath`

После `ObjectManager.setDriverTelemetryValueInMemory`, когда mode — `EVENT_JOURNAL_ONLY`:

1. Resolve event name из driver binding.
2. Вызов `EventService.fireIngress()` — тот же async journal enqueue, что alerts, **без** surrounding transaction (hot path).
3. Return immediately; skip `TelemetryHistorianFastPath`, `TelemetryIngressDispatcher` и `RuntimeTelemetryCoalescer`.

Metric source tag: `EventFireSource.INGRESS` (`ispf.automation.events_fired` by source).

### Direct ingress (skip L1 buffer)

То же правило, что historian fast path ([0026-elastic-telemetry-ingress](0026-elastic-telemetry-ingress.md)): devices на `EVENT_JOURNAL_ONLY` bypass server `DriverIngressBuffer` (L1), чтобы MQTT L0 и platform tiers не складывались.

### Elastic L0 (0.9.87+)

Journal load tests по умолчанию используют **`ingressCoalesceEnabled=false`** (FIFO per message, no last-value-wins на L0). Каждый Paho callback enqueue в `DriverIngressFifoExecutor` с elastic threads (platform default 4→32). При coalesce enabled L0 использует elastic `DriverIngressBuffer` с eager per-lane drain.

L1 остаётся bypassed для `EVENT_JOURNAL_ONLY`; elastic L3 не применяется, потому что telemetry ingress queue skipped. Bottlenecks под sustained load — **L0 FIFO** и **L5′ journal writer / store**, не L1/L3.

## Pipeline diagram

```mermaid
flowchart LR
    MQTT[MQTT broker]
    L0[DriverIngressFifoExecutor L0 elastic]
    DRV[MqttDeviceDriver]
    RAM[ObjectManager RAM update]
    EJF[TelemetryEventJournalFastPath]
    ES[EventService.fireIngress]
    EJW[EventJournalAsyncWriter elastic]
    CH[(event_history)]

    MQTT --> L0 --> DRV --> RAM --> EJF --> ES --> EJW --> CH
```

Contrast с HTTP tap (load test only):

```mermaid
flowchart LR
    MQTT[MQTT broker]
    TAP[External subscriber]
    HTTP[POST /events/fire]
    ES[EventService.fire API path]
    EJW[EventJournalAsyncWriter]

    MQTT --> TAP --> HTTP --> ES --> EJW
```

Internal path убирает HTTP и API transaction layers; absolute throughput всё ещё зависит от CPU, journal store и batch tuning.

## Load testing

Scripts (без fixed throughput claims — measure on your hardware):

| Script | Path measured |
|--------|----------------|
| `deploy/mqtt-event-journal-test-remote.sh` | mqtt driver → `fireIngress` → journal |
| `deploy/mqtt-event-ingest-test-remote.sh` | External MQTT tap → HTTP fire (API baseline) |
| `deploy/lab-mqtt-event-journal-multi-test.sh` | **Lab:** 16× mqtt → Scylla journal + Mosquitto `$SYS` metrics |
| `deploy/vps-ispf-fair-bench.sh` | **VPS:** 1× mqtt, sustained + peak emqtt phases, Scylla delta + `eventsFired` |
| `deploy/vps-ispf-fair-run.sh` | Orchestration: stack prep, restart ISPF, invoke fair bench |
| `deploy/vps-event-journal-peak-tuning.sh` | Idempotent VPS env: journal queue/batch/flush + elastic writers |

Setup helper: `deploy/setup-mqtt-event-journal.py` (single device); multi-device: `deploy/setup-mqtt-event-journal-devices.py` with `--bench-no-l0-coalesce`.

См. [load-testing](../load-testing.md) и **[LAB_EVENT_JOURNAL_STRESS](../LAB_EVENT_JOURNAL_STRESS.md)** (Scylla lab host, multi-device emqtt, metrics interpretation).

**Lab baseline (2026-07-04, ISPF 0.9.88, 16 mqtt devices, Scylla):** sustained journal **~110k events/s** (~6.8k/device); `eventsFired` → flushed → Scylla meta **100%** (no journal loss). Apparent «~17% efficiency vs configured MQTT target» — emqtt formula / CPU-cap artifact, not ISPF dropping messages. Bottleneck at peak: Scylla write CPU.

**VPS prod single-device (2026-07-05, ISPF 0.9.87, Scylla 1 SMP / 750 MB, `EVENT_JOURNAL_ONLY`, `ingressCoalesceEnabled=false`):**

| Phase | emqtt params | eventsFired/s | Notes |
|-------|--------------|---------------|-------|
| Before elastic L0 (fixed 4 threads) | 65s, 20×10ms | **~384** | L0 thread pool saturated |
| After elastic L0 + L5′ (defaults) | 65s, 20×10ms | **~1878** | ~4.9×; `eventJournalSyncFallbackTotal=0` |
| After journal peak tuning | 65s, 32×1ms | **~15k** (eventsFired delta) | Queue 500k, batch 1k, flush 20ms; no sync fallback |

Journal peak tuning на VPS (`deploy/vps-event-journal-peak-tuning.sh`): `ISPF_EVENT_JOURNAL_QUEUE_CAPACITY=500000`, `BATCH_SIZE=1000`, `FLUSH_INTERVAL_MS=20`, elastic writers 4→32. Without larger queue peak overload вызывал `Event journal queue full` и sync persist на L0 FIFO threads.

Interpretation: используйте **`eventsFired` delta** и **`eventJournalSyncFallbackTotal`** during/after run; `SELECT COUNT(*)` на Scylla может timeout, пока node hot (1 SMP). Allow settle (25s+) before partition counts на peak phases.

## Последствия

- **Use case:** audit trail raw ingress (messages, frames) at high rate без historian или alert rules.
- **Not a replacement for** `FULL` automation или `TELEMETRY_ONLY` dashboards — orthogonal modes.
- **Coalesce:** per-device `telemetryCoalesceMs` still applies before platform tiers; for near 1:1 message→event use minimal coalesce in lab only.
- **Bench L0:** set `ingressCoalesceEnabled=false` на mqtt driver (loadtest scripts default) so L0 last-value-wins coalesce off; elastic workers via server defaults (`ISPF_DRIVER_MQTT_CALLBACK_ELASTIC`, min/max threads, scale thresholds) or per-device `callbackElasticEnabled` / `callbackThreadsMin/Max`. Optional `deploy/vps-event-journal-peak-tuning.sh` for journal queue under peak. High-rate fast paths skip RAM live-value updates when event journal or historian-only handles ingress.
- **WebSocket:** `publishEventFired` still runs on fast path; UI fan-out may become limiter at extreme rates — tune separately ([0024-demand-driven-variable-change-pubsub](0024-demand-driven-variable-change-pubsub.md)).

## Связанные материалы

- [0026-elastic-telemetry-ingress](0026-elastic-telemetry-ingress.md) — multi-level telemetry ingress
- [0017-telemetry-ingest-pipeline](0017-telemetry-ingest-pipeline.md) — publish modes and gateway
- [0016-clickhouse-event-journal](0016-clickhouse-event-journal.md) — ClickHouse journal store
- [AUTOMATION](../AUTOMATION.md) — events API and descriptors
- [load-testing](../load-testing.md) — measurement scripts
