# ADR-0027: Event journal ingress fast path

## Status

Accepted (2026-07-04)

## Context

High-rate driver telemetry can be persisted as **event journal** records (one ingress update → one `event_history` row), not only as historian samples or automation-side effects.

Two anti-patterns were observed under flood load:

1. **HTTP per message** — external tap calling `POST /api/v1/events/fire` adds REST serialization and connection overhead on top of journal I/O; useful for API smoke tests, not representative of in-process driver ingress.
2. **FULL automation path** — coalesced telemetry → object-change bus → alert CEL → `EventService.fire` couples journal throughput to binding evaluation and dual-lane scheduling.

Production event journal already uses async batch writers ([ADR-0016](0016-clickhouse-event-journal.md), [ADR-0015](0015-event-history-timescale.md)). The missing piece was a **driver-native hot path** that enqueues journal writes without HTTP and without traversing the telemetry/automation bus.

## Decision

### Telemetry publish mode `EVENT_JOURNAL_ONLY`

Third per-device mode alongside `FULL` and `TELEMETRY_ONLY`:

| Mode | RAM live value | Historian | Object-change bus | Event journal |
|------|----------------|-----------|-------------------|---------------|
| `FULL` | yes | yes (via bus / bindings) | yes (automation lane) | via alerts, API, correlators |
| `TELEMETRY_ONLY` | yes | yes (fast path) | telemetry lane only | no |
| `EVENT_JOURNAL_ONLY` | yes | no | skipped | yes (fast path) |

Configure on driver binding:

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

- `ingressEventName` — event descriptor on the device (default `messageReceived`). Payload includes MQTT `raw` when present on the updated variable.
- Object must define the event (`EventDescriptor`) before fire; loadtest model `mqtt-sensor-v1` registers `messageReceived`.

### `TelemetryEventJournalFastPath`

After `ObjectManager.setDriverTelemetryValueInMemory`, when mode is `EVENT_JOURNAL_ONLY`:

1. Resolve event name from driver binding.
2. Call `EventService.fireIngress()` — same async journal enqueue as alerts, **without** surrounding transaction (hot path).
3. Return immediately; skip `TelemetryHistorianFastPath`, `TelemetryIngressDispatcher`, and `RuntimeTelemetryCoalescer`.

Metric source tag: `EventFireSource.INGRESS` (`ispf.automation.events_fired` by source).

### Direct ingress (skip L1 buffer)

Same rule as historian fast path ([ADR-0026](0026-elastic-telemetry-ingress.md)): devices on `EVENT_JOURNAL_ONLY` bypass the server `DriverIngressBuffer` (L1) so MQTT L0 and platform tiers are not stacked.

## Pipeline diagram

```mermaid
flowchart LR
    MQTT[MQTT broker]
    L0[DriverIngressBuffer L0]
    DRV[MqttDeviceDriver]
    RAM[ObjectManager RAM update]
    EJF[TelemetryEventJournalFastPath]
    ES[EventService.fireIngress]
    EJW[EventJournalAsyncWriter]
    CH[(event_history)]

    MQTT --> L0 --> DRV --> RAM --> EJF --> ES --> EJW --> CH
```

Contrast with HTTP tap (load test only):

```mermaid
flowchart LR
    MQTT[MQTT broker]
    TAP[External subscriber]
    HTTP[POST /events/fire]
    ES[EventService.fire API path]
    EJW[EventJournalAsyncWriter]

    MQTT --> TAP --> HTTP --> ES --> EJW
```

Internal path removes HTTP and API transaction layers; absolute throughput still depends on CPU, journal store, and batch tuning.

## Load testing

Scripts (no fixed throughput claims — measure on your hardware):

| Script | Path measured |
|--------|----------------|
| `deploy/mqtt-event-journal-test-remote.sh` | mqtt driver → `fireIngress` → journal |
| `deploy/mqtt-event-ingest-test-remote.sh` | External MQTT tap → HTTP fire (API baseline) |
| `deploy/lab-mqtt-event-journal-multi-test.sh` | **Lab:** 16× mqtt → Scylla journal + Mosquitto `$SYS` metrics |

Setup helper: `deploy/setup-mqtt-event-journal.py`.

See [LOAD_TESTING.md](../LOAD_TESTING.md) and **[LAB_EVENT_JOURNAL_STRESS.md](../LAB_EVENT_JOURNAL_STRESS.md)** (Scylla lab host, multi-device emqtt, metrics interpretation).

**Lab baseline (2026-07-04, ISPF 0.9.88, 16 mqtt devices, Scylla):** sustained journal **~110k events/s** (~6.8k/device); `eventsFired` → flushed → Scylla meta **100%** (no journal loss). Apparent «~17% efficiency vs configured MQTT target» is an emqtt formula / CPU-cap artifact, not ISPF dropping messages. Bottleneck at peak: Scylla write CPU.

## Consequences

- **Use case:** audit trail of raw ingress (messages, frames) at high rate without historian or alert rules.
- **Not a replacement for** `FULL` automation or `TELEMETRY_ONLY` dashboards — orthogonal modes.
- **Coalesce:** per-device `telemetryCoalesceMs` still applies before platform tiers; for near 1:1 message→event use minimal coalesce in lab only.
- **Bench L0:** set `ingressCoalesceEnabled=false` on the mqtt driver (loadtest scripts default) so L0 last-value-wins coalesce is off; optional `callbackThreads` / `callbackQueueCapacity` tune FIFO ingress.
- **WebSocket:** `publishEventFired` still runs on fast path; UI fan-out may become a limiter at extreme rates — tune separately ([ADR-0024](0024-demand-driven-variable-change-pubsub.md)).

## Related

- [ADR-0026](0026-elastic-telemetry-ingress.md) — multi-level telemetry ingress
- [ADR-0017](0017-telemetry-ingest-pipeline.md) — publish modes and gateway
- [ADR-0016](0016-clickhouse-event-journal.md) — ClickHouse journal store
- [AUTOMATION.md](../AUTOMATION.md) — events API and descriptors
- [LOAD_TESTING.md](../LOAD_TESTING.md) — measurement scripts
