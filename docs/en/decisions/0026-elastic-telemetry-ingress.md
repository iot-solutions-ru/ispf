# ADR-0026: Multi-level async telemetry ingress

## Status

**Accepted** (2026-07-04) — layered last-value-wins ingress. Elastic min/max env keys are **overlays** ([0033](0033-prod-idle-demostand-tuning.md), [load-testing](../load-testing.md)), not the decision.

## Context

High-rate MQTT showed: sync coalesce saturates before stores finish; unbounded FIFO grows to OOM; poll/push drivers called `updateVariable` on protocol threads.

## Decision

### Ingress contract (all drivers)

The hot path from device samples to `DriverObject.updateVariable` must **not** perform platform persistence (DB, historian, event journal, disk). Durable writes are asynchronous in server tiers.

### Multi-level pipeline

| Level | Component | Role |
|-------|-----------|------|
| **L0** | `DriverIngressBuffer` / FIFO | Push drivers — last-value-wins or FIFO |
| **L1** | `ServerDriverObject` ingress buffer | All running drivers |
| **L2** | Async poll pool | Poll `readPoints` off the scheduler thread |
| **L3** | `TelemetryIngressDispatcher` | Platform last-value-wins lanes |
| **L4** | `RuntimeTelemetryCoalescer` | Time coalesce → pubsub / gateway |
| **L5** | Historian fast path + async writer | `TELEMETRY_ONLY` / overflow coalesce |
| **L5′** | Event-journal fast path | `EVENT_JOURNAL_ONLY` — [0027](0027-event-journal-ingress-fast-path.md) |

Live RAM updates via in-memory set (no DB flush per tick).

### Design properties (must hold)

| Flood risk | Mitigation |
|------------|------------|
| Unbounded FIFO → OOM | Bounded lanes + last-value-wins at L0/L1/L3 |
| Blocking protocol I/O | L0 buffers |
| Blocking poll scheduler | L2 async I/O pool |
| Sync DB on overload | L5 / L5′ async batch + overflow coalesce |

Elastic scale-up/down on L0/L1/L3/L5′ is an implementation of these properties. **Knobs and defaults** → [load-testing](../load-testing.md), `application.yml`, profile overlays ([0033](0033-prod-idle-demostand-tuning.md)).

## Consequences

- Prefer named **profiles** over hand-editing every `*_MIN/MAX` env ([0033](0033-prod-idle-demostand-tuning.md)).
- Absolute throughput claims belong in evidence / load-testing journals, not this ADR.

## Related

- [0017-telemetry-ingest-pipeline](0017-telemetry-ingest-pipeline.md)
- [0024-demand-driven-variable-change-pubsub](0024-demand-driven-variable-change-pubsub.md)
- [0027-event-journal-ingress-fast-path](0027-event-journal-ingress-fast-path.md)
- [0033-prod-idle-demostand-tuning](0033-prod-idle-demostand-tuning.md)
- [load-testing](../load-testing.md)
