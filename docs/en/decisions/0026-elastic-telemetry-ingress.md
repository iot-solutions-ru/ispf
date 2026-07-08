# ADR-0026: Multi-level async telemetry ingress

## Status

Accepted (2026-07-04)

## Context

High-rate MQTT load tests showed:

- Publisher rates above the synchronous coalesce path saturate before historian or journal writes complete.
- Unbounded append-only FIFO ingest queues grow until the JVM runs out of memory.
- Poll and push drivers called `updateVariable` from protocol or scheduler threads, coupling I/O latency to the platform pipeline.

## Ingress contract (all drivers)

The hot path from device samples to `DriverObject.updateVariable` must **not** perform platform persistence (database, historian, event journal, or disk). Drivers may keep short-lived in-memory caches; durable storage is asynchronous in the server tiers below.

## Multi-level pipeline

| Level | Component | Scope |
|-------|-----------|--------|
| **L0** | `DriverIngressBuffer` (driver-api) | Push drivers: MQTT, OPC UA subscribe, IEC104-server |
| **L1** | `ServerDriverObject` ingress buffer | **All** running drivers (`ispf.driver.ingress-buffer-enabled`) |
| **L2** | Async poll pool (`ispf.driver.async-poll-enabled`) | **All** poll drivers — `readPoints` on `ispf-driver-io` threads |
| **L3** | `TelemetryIngressDispatcher` | Platform last-value-wins lanes → coalescer |
| **L4** | `RuntimeTelemetryCoalescer` | Time coalesce + route to pubsub / gateway |
| **L5** | `TelemetryHistorianFastPath` + elastic `VariableHistoryAsyncWriter` | Async historian; overflow coalesce, no sync DB on producers |
| **L5′** | `TelemetryEventJournalFastPath` + `EventService.fireIngress` | Async event journal for `EVENT_JOURNAL_ONLY`; no HTTP, no object-change bus |

Live RAM values are updated in `ObjectManager.setDriverTelemetryValueInMemory` (no DB flush per tick).

## Decision

### L0 — Driver protocol buffers (`DriverIngressBuffer`)

- Shared last-value-wins buffer in `ispf-driver-api`.
- Wired in MQTT (Paho callbacks), OPC UA (`subscribe` mode), IEC104-server (ASDU handler).
- Config keys: `callbackThreads`, `callbackQueueCapacity`, plus elastic tuning (below).

### L0 — Elastic workers (0.9.87+)

Fixed thread pools on L0 and L1 capped sustained journal throughput (~4 MQTT FIFO threads ≈ few hundred events/s on typical VPS). All ingress tiers now share the same elastic pattern as L3/L5 (`ElasticWorkerScaler` in `ispf-driver-api`).

| Mode | Component | Elastic default |
|------|-----------|-----------------|
| L0 coalesce (`ingressCoalesceEnabled=true`) | `DriverIngressBuffer` (eager per-lane drain) | 4→32 workers |
| L0 FIFO (`ingressCoalesceEnabled=false`) | `DriverIngressFifoExecutor` | 4→32 threads |
| L1 | `DriverIngressBuffer` (park-loop workers) | 2→32 workers |
| L5′ | `EventJournalAsyncWriter` | 4→32 writers (env min; was 2→32 in yml default) |

Platform env (see `application.yml`):

- `ISPF_DRIVER_MQTT_CALLBACK_ELASTIC`, `ISPF_DRIVER_MQTT_CALLBACK_THREADS_MIN/MAX`, `ISPF_DRIVER_MQTT_CALLBACK_SCALE_*`
- `ISPF_DRIVER_INGRESS_BUFFER_ELASTIC`, `ISPF_DRIVER_INGRESS_BUFFER_THREADS_MIN/MAX`, `ISPF_DRIVER_INGRESS_BUFFER_SCALE_*`
- `ISPF_EVENT_JOURNAL_ELASTIC_WRITER`, `ISPF_EVENT_JOURNAL_WRITER_THREADS_MIN/MAX`, `ISPF_EVENT_JOURNAL_ELASTIC_SCALE_*`

Per-device overrides via driver binding `configuration`: `callbackElasticEnabled`, `callbackThreadsMin`, `callbackThreadsMax`, `callbackScaleUpQueueThreshold`, `callbackScaleDownSteps`, `callbackScaleCheckIntervalMs` (resolved through `IngressElasticSettings`).

Scale-up triggers when pending queue depth ≥ threshold; scale-down after consecutive empty checks. Hot reload (subset): runtime-settings `driver.mqtt-callback-elastic-enabled`, `driver.mqtt-callback-threads-max`, `driver.ingress-buffer-elastic-enabled`, `event-journal.elastic-writer-enabled`.

### L1 — Server driver bridge (`ServerDriverObject`)

- Per-device ingress buffer before `ObjectManager.setDriverTelemetryValue`.
- Enabled by default: `ispf.driver.ingress-buffer-enabled=true`.

### L2 — Async driver poll (`DriverRuntimeService`)

- Scheduler only schedules ticks; blocking `readPoints` runs on `ispf-driver-io` pool.
- `ispf.driver.async-poll-enabled=true`, `ispf.driver.io-threads=16`.

### L3 — Platform ingress queue (`TelemetryIngressDispatcher`)

- `ispf.runtime-telemetry.ingress-queue-enabled=true`.
- Elastic workers (4–32); lane-capacity eviction publishes immediately.

### L4–L5 — Coalescer, historian fast path, elastic writer, event journal fast path

- `TelemetryHistorianFastPath` for `TELEMETRY_ONLY` historian-only devices.
- `TelemetryEventJournalFastPath` for `EVENT_JOURNAL_ONLY` — driver ingress → async journal ([ADR-0027](0027-event-journal-ingress-fast-path.md)).
- `VariableHistoryAsyncWriter` overflow coalesce (no sync persist on producer threads).
- `EventJournalAsyncWriter` elastic writers (L5′) — same scaler semantics as variable history; avoids fixed 2-thread journal bottleneck under flood load.
- Scylla/Cassandra historian writes group samples **by partition** `(object_path, variable_name, field_name)` before UNLOGGED batch execute (avoids cross-partition batch anti-pattern).
- Hot-path caches: `historyEnabled` lookup TTL cache; fast path skips duplicate tree walk via `recordFromDataRecordTrusted`.
- L3 ingress drain calls `publishCoalescedBatch` → single historian `enqueue` per drain batch.
- Scylla writes use async driver API with partition-grouped UNLOGGED batches.

## Design properties

| Risk under flood load | Mitigation |
|-----------------------|------------|
| Unbounded FIFO → OOM | Bounded lanes + last-value-wins at L0/L1/L3 |
| Blocking protocol I/O threads | L0 driver buffers |
| Blocking shared poll scheduler | L2 async poll I/O pool |
| Sync DB on overload | L5 overflow coalesce + batch persist |

## Configuration

- `ispf.driver.ingress-buffer-*` (including `ingress-buffer-elastic-enabled`, min/max threads, scale thresholds)
- `ispf.driver.mqtt-callback-*` (including elastic and min/max threads)
- `ispf.driver.async-poll-enabled`, `ispf.driver.io-threads`
- `ispf.runtime-telemetry.ingress-*`, `ispf.runtime-telemetry.fast-historian-path`
- `ispf.variable-history.elastic-writer-enabled`, `ispf.variable-history.overflow-coalesce-enabled`
- `ispf.event-journal.elastic-writer-enabled`, `ispf.event-journal.writer-threads-min/max`, `ispf.event-journal.elastic-scale-*`
- Per-driver: `callbackThreads`, `callbackQueueCapacity`, elastic keys above

## Related

- [ADR-0017](0017-telemetry-ingest-pipeline.md)
- [ADR-0024](0024-demand-driven-variable-change-pubsub.md)
- [ADR-0027](0027-event-journal-ingress-fast-path.md)
- [LOAD_TESTING.md](../load-testing.md)
