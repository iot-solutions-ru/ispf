# ADR-0025: Telemetry quality flags

> **Number alias:** **0025a** (telemetry quality). The same numeric id also labels [0025-cassandra-scylla-timeseries-store](0025-cassandra-scylla-timeseries-store.md) (**0025b**). Prefer the filename or alias in new links; do not renumber files.

**Status:** Accepted  
**Date:** 2026-07-03  
**Context:** BL-82 (REQ-EX Wave J). Industrial protocols expose status/quality (OPC UA StatusCode, BACnet status-flags). ISPF needs a normalized contract for HMI charts and future historian gaps.

## Decision

1. **Normalized levels** — `GOOD`, `UNCERTAIN`, `BAD` (`TelemetryQuality` in `ispf-driver-api`).
2. **Storage** — optional string field `quality` on telemetry `DataRecord` rows (same row as `value`), not a separate platform variable type.
3. **Driver mapping**
   - OPC UA: Milo `StatusCode` → GOOD / UNCERTAIN / BAD.
   - Virtual demo: cycles quality on `temperature` for lab HMI.
4. **HMI charts** — trend/chart widgets **omit** `BAD` samples (line gap via `null` value, `connectNulls={false}`). `UNCERTAIN` remains plottable (future: dashed segment).
5. **Historian** — v1 stores numeric samples only; quality gaps apply to **live** binding and driver payloads. Follow-up BL may add `quality` column to history stores.

## Consequences

- `DriverProductionMatrix` adds `QUALITY` capability for `virtual` and `opcua`.
- Chart widgets read `quality` from bound variable row when present.
- BACnet `status-flags` mapping deferred to a follow-up driver change.

## References

- [OBJECT_MODEL.md § Telemetry quality](../object-model.md#telemetry-quality-bl-82)
- [roadmap](../roadmap.md)
