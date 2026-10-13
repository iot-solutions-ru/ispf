# ADR-0025: Telemetry quality flags

> **Number alias:** **0025a** (telemetry quality). The same numeric id also labels [0025-cassandra-scylla-timeseries-store](0025-cassandra-scylla-timeseries-store.md) (**0025b**). Prefer the filename or alias in new links; do not renumber files.

## Status

**Accepted** (2026-07-03) — live quality contract. **Persisted historian quality** is gated by [0074-historian-quality-persistence](0074-historian-quality-persistence.md).

## Context

BL-82 (REQ-EX Wave J). Industrial protocols expose status/quality (OPC UA StatusCode, BACnet status-flags). ISPF needs a normalized contract for HMI charts and (later) historian samples comparable to PI-class stores.

## Decision

1. **Normalized levels** — `GOOD`, `UNCERTAIN`, `BAD` (`TelemetryQuality` in `ispf-driver-api`).
2. **Storage (live)** — optional string field `quality` on telemetry `DataRecord` rows (same row as `value`), not a separate platform variable type.
3. **Driver mapping**
   - OPC UA: Milo `StatusCode` → GOOD / UNCERTAIN / BAD.
   - Virtual demo: cycles quality on `temperature` for lab HMI.
4. **HMI charts** — trend/chart widgets **omit** `BAD` samples (line gap via `null` value, `connectNulls={false}`). `UNCERTAIN` remains plottable (future: dashed segment).
5. **Historian** — jdbc persists nullable `quality` beside the value ([0074](0074-historian-quality-persistence.md)). Null means unknown / pre-v2, not GOOD. ClickHouse bootstrap includes the column; a container round-trip is still open. Cassandra/Scylla does not store quality.

### Honesty — Done / Partial / Gap

| Claim | Status |
|-------|--------|
| Live `quality` on variable / driver payload | **Done** |
| HMI omit BAD | **Done** |
| OPC UA + virtual mapping | **Done** |
| BACnet status-flags mapping | **Partial** / deferred driver change |
| Historian column + dual-backend TCK | **Partial** — jdbc TCK green ([0074](0074-historian-quality-persistence.md)); ClickHouse container round-trip and Cassandra/Scylla still open |

Do **not** claim PI-class historian quality from 0025a alone.

## Consequences

- `DriverProductionMatrix` adds `QUALITY` capability for `virtual` and `opcua`.
- Chart widgets read `quality` from bound variable row when present.
- Scorecard Historian 10/10 blocked on quality persist evidence ([0074](0074-historian-quality-persistence.md)).

## Related

- [object-model](../object-model.md) § Telemetry quality
- [0074-historian-quality-persistence](0074-historian-quality-persistence.md)
- [roadmap](../roadmap.md)
