> **Язык:** русская версия (вычитка). Канонический английский: [en/decisions/0026-elastic-telemetry-ingress.md](../../en/decisions/0026-elastic-telemetry-ingress.md).

# ADR-0026: Многоуровневый асинхронный ingress телеметрии

## Статус

Принято (2026-07-04)

## Контекст

High-rate MQTT load tests показали:

- Publisher rates выше synchronous coalesce path насыщают pipeline до завершения historian или journal writes.
- Unbounded append-only FIFO ingress queues растут, пока JVM не исчерпает память.
- Poll и push drivers вызывали `updateVariable` из protocol или scheduler threads, связывая I/O latency с platform pipeline.

## Ingress contract (все drivers)

Hot path от device samples к `DriverObject.updateVariable` **не должен** выполнять platform persistence (database, historian, event journal или disk). Drivers могут держать short-lived in-memory caches; durable storage — асинхронно в server tiers ниже.

## Многоуровневый pipeline

| Уровень | Компонент | Scope |
|-------|-----------|--------|
| **L0** | `DriverIngressBuffer` (driver-api) | Push drivers: MQTT, OPC UA subscribe, IEC104-server |
| **L1** | `ServerDriverObject` ingress buffer | **All** running drivers (`ispf.driver.ingress-buffer-enabled`) |
| **L2** | Async poll pool (`ispf.driver.async-poll-enabled`) | **All** poll drivers — `readPoints` on `ispf-driver-io` threads |
| **L3** | `TelemetryIngressDispatcher` | Platform last-value-wins lanes → coalescer |
| **L4** | `RuntimeTelemetryCoalescer` | Time coalesce + route to pubsub / gateway |
| **L5** | `TelemetryHistorianFastPath` + elastic `VariableHistoryAsyncWriter` | Async historian; overflow coalesce, no sync DB on producers |
| **L5′** | `TelemetryEventJournalFastPath` + `EventService.fireIngress` | Async event journal для `EVENT_JOURNAL_ONLY`; no HTTP, no object-change bus |

Live RAM values обновляются в `ObjectManager.setDriverTelemetryValueInMemory` (no DB flush per tick).

## Решение

### L0 — Driver protocol buffers (`DriverIngressBuffer`)

- Shared last-value-wins buffer в `ispf-driver-api`.
- Wired в MQTT (Paho callbacks), OPC UA (`subscribe` mode), IEC104-server (ASDU handler).
- Config keys: `callbackThreads`, `callbackQueueCapacity`, plus elastic tuning (ниже).

### L0 — Elastic workers (0.9.87+)

Fixed thread pools на L0 и L1 ограничивали sustained journal throughput (~4 MQTT FIFO threads ≈ few hundred events/s на typical VPS). Все ingress tiers теперь используют тот же elastic pattern, что L3/L5 (`ElasticWorkerScaler` в `ispf-driver-api`).

| Mode | Component | Elastic default |
|------|-----------|-----------------|
| L0 coalesce (`ingressCoalesceEnabled=true`) | `DriverIngressBuffer` (eager per-lane drain) | 4→32 workers |
| L0 FIFO (`ingressCoalesceEnabled=false`) | `DriverIngressFifoExecutor` | 4→32 threads |
| L1 | `DriverIngressBuffer` (park-loop workers) | 2→32 workers |
| L5′ | `EventJournalAsyncWriter` | 4→32 writers (env min; was 2→32 in yml default) |

Platform env (см. `application.yml`):

- `ISPF_DRIVER_MQTT_CALLBACK_ELASTIC`, `ISPF_DRIVER_MQTT_CALLBACK_THREADS_MIN/MAX`, `ISPF_DRIVER_MQTT_CALLBACK_SCALE_*`
- `ISPF_DRIVER_INGRESS_BUFFER_ELASTIC`, `ISPF_DRIVER_INGRESS_BUFFER_THREADS_MIN/MAX`, `ISPF_DRIVER_INGRESS_BUFFER_SCALE_*`
- `ISPF_EVENT_JOURNAL_ELASTIC_WRITER`, `ISPF_EVENT_JOURNAL_WRITER_THREADS_MIN/MAX`, `ISPF_EVENT_JOURNAL_ELASTIC_SCALE_*`

Per-device overrides через driver binding `configuration`: `callbackElasticEnabled`, `callbackThreadsMin`, `callbackThreadsMax`, `callbackScaleUpQueueThreshold`, `callbackScaleDownSteps`, `callbackScaleCheckIntervalMs` (resolved through `IngressElasticSettings`).

Scale-up срабатывает, когда pending queue depth ≥ threshold; scale-down после consecutive empty checks. Hot reload (subset): runtime-settings `driver.mqtt-callback-elastic-enabled`, `driver.mqtt-callback-threads-max`, `driver.ingress-buffer-elastic-enabled`, `event-journal.elastic-writer-enabled`.

### L1 — Server driver bridge (`ServerDriverObject`)

- Per-device ingress buffer перед `ObjectManager.setDriverTelemetryValue`.
- Enabled by default: `ispf.driver.ingress-buffer-enabled=true`.

### L2 — Async driver poll (`DriverRuntimeService`)

- Scheduler только планирует ticks; blocking `readPoints` выполняется на `ispf-driver-io` pool.
- `ispf.driver.async-poll-enabled=true`, `ispf.driver.io-threads=16`.

### L3 — Platform ingress queue (`TelemetryIngressDispatcher`)

- `ispf.runtime-telemetry.ingress-queue-enabled=true`.
- Elastic workers (4–32); lane-capacity eviction публикует немедленно.

### L4–L5 — Coalescer, historian fast path, elastic writer, event journal fast path

- `TelemetryHistorianFastPath` для `TELEMETRY_ONLY` historian-only devices.
- `TelemetryEventJournalFastPath` для `EVENT_JOURNAL_ONLY` — driver ingress → async journal ([ADR-0027](0027-event-journal-ingress-fast-path.md)).
- `VariableHistoryAsyncWriter` overflow coalesce (no sync persist on producer threads).
- `EventJournalAsyncWriter` elastic writers (L5′) — та же scaler semantics, что variable history; avoids fixed 2-thread journal bottleneck under flood load.
- Scylla/Cassandra historian writes группируют samples **by partition** `(object_path, variable_name, field_name)` перед UNLOGGED batch execute (avoids cross-partition batch anti-pattern).
- Hot-path caches: `historyEnabled` lookup TTL cache; fast path skips duplicate tree walk via `recordFromDataRecordTrusted`.
- L3 ingress drain вызывает `publishCoalescedBatch` → single historian `enqueue` per drain batch.
- Scylla writes используют async driver API с partition-grouped UNLOGGED batches.

## Design properties

| Риск под flood load | Mitigation |
|-----------------------|------------|
| Unbounded FIFO → OOM | Bounded lanes + last-value-wins на L0/L1/L3 |
| Blocking protocol I/O threads | L0 driver buffers |
| Blocking shared poll scheduler | L2 async poll I/O pool |
| Sync DB on overload | L5 overflow coalesce + batch persist |

## Конфигурация

- `ispf.driver.ingress-buffer-*` (including `ingress-buffer-elastic-enabled`, min/max threads, scale thresholds)
- `ispf.driver.mqtt-callback-*` (including elastic and min/max threads)
- `ispf.driver.async-poll-enabled`, `ispf.driver.io-threads`
- `ispf.runtime-telemetry.ingress-*`, `ispf.runtime-telemetry.fast-historian-path`
- `ispf.variable-history.elastic-writer-enabled`, `ispf.variable-history.overflow-coalesce-enabled`
- `ispf.event-journal.elastic-writer-enabled`, `ispf.event-journal.writer-threads-min/max`, `ispf.event-journal.elastic-scale-*`
- Per-driver: `callbackThreads`, `callbackQueueCapacity`, elastic keys above

## Связанные материалы

- [ADR-0017](0017-telemetry-ingest-pipeline.md)
- [ADR-0024](0024-demand-driven-variable-change-pubsub.md)
- [ADR-0027](0027-event-journal-ingress-fast-path.md)
- [LOAD_TESTING.md](../load-testing.md)
