# ADR-0061: High-rate telemetry history defaults

## Status

**Accepted** (2026-10-08)

## Context

Legacy MQTT device setups often stored every inbound `message` event by default. Field teams then added retention rules after production incidents. ISPF must default to **safe** high-rate paths.

## Decision

1. MQTT driver default: `eventToVariable=false` (last-value variables / coalesce ingress).
2. New objects keep `eventJournalEnabled=false` unless an author explicitly enables it.
3. `Variable.includePreviousValueInEvent` remains **opt-in** (payload size / automation need).
4. Time-bucket analytics use historian **rollups** (`kind: historian`, `rollupBuckets`), not per-message event granules.
5. Bundle / authoring docs point at [anti-patterns.md](../anti-patterns.md) § MQTT and § Hand-rolled time buckets.

## Consequences

- High-rate demos stay responsive without silent history growth.
- Auditors enable journal/previous-value only where required.
- Agents must not flip `eventJournalEnabled` on catch-all MQTT devices without retention.

## Related

- [anti-patterns](../anti-patterns.md)
- [variable-history](../variable-history.md)
- [lab-mqtt-historian-stress](../lab-mqtt-historian-stress.md)
