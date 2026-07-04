# ADR-0026: Multi-level async telemetry ingress

## Status

Accepted (2026-07-04)

## Context

High-rate MQTT load tests showed:

- Publisher at 10k msg/s overloads the synchronous coalesce path before historian writes.
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

Live RAM values are updated in `ObjectManager.setDriverTelemetryValueInMemory` (no DB flush per tick).

## Decision

### L0 — Driver protocol buffers (`DriverIngressBuffer`)

- Shared last-value-wins buffer in `ispf-driver-api`.
- Wired in MQTT (Paho callbacks), OPC UA (`subscribe` mode), IEC104-server (ASDU handler).
- Config keys: `callbackThreads`, `callbackQueueCapacity`.

### L1 — Server driver bridge (`ServerDriverObject`)

- Per-device ingress buffer before `ObjectManager.setDriverTelemetryValue`.
- Enabled by default: `ispf.driver.ingress-buffer-enabled=true`.

### L2 — Async driver poll (`DriverRuntimeService`)

- Scheduler only schedules ticks; blocking `readPoints` runs on `ispf-driver-io` pool.
- `ispf.driver.async-poll-enabled=true`, `ispf.driver.io-threads=16`.

### L3 — Platform ingress queue (`TelemetryIngressDispatcher`)

- `ispf.runtime-telemetry.ingress-queue-enabled=true`.
- Elastic workers (4–32); lane-capacity eviction publishes immediately.

### L4–L5 — Coalescer, historian fast path, elastic writer

- `TelemetryHistorianFastPath` for `TELEMETRY_ONLY` historian-only devices.
- `VariableHistoryAsyncWriter` overflow coalesce (no sync persist on producer threads).
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

- `ispf.driver.ingress-buffer-*`, `ispf.driver.async-poll-enabled`, `ispf.driver.io-threads`
- `ispf.runtime-telemetry.ingress-*`, `ispf.runtime-telemetry.fast-historian-path`
- `ispf.variable-history.elastic-writer-enabled`, `ispf.variable-history.overflow-coalesce-enabled`
- Per-driver: `callbackThreads`, `callbackQueueCapacity`

## Related

- [ADR-0017](0017-telemetry-ingest-pipeline.md)
- [ADR-0024](0024-demand-driven-variable-change-pubsub.md)
- [LOAD_TESTING.md](../LOAD_TESTING.md)
