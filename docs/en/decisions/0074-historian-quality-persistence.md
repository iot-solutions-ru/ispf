# ADR-0074: Historian quality persistence (sample v2)

## Status

**Accepted** (2026-10-13) — amends [0025](0025-telemetry-quality-flags.md): live `quality` stays Done; **persisted historian quality** is the next gated slice.

## Context

Live variables carry quality flags (0025). Historian path today is value-centric; `object-model` / 0025 already call historian quality a follow-up. Phase 28 closed query SLO tiers without quality columns.

## Decision

### 1. Target contract

1. Each stored sample may carry `quality` (enum/code compatible with live flags) beside `value`, `observed_at`, `sampled_at` / ingest time as already modeled.
2. Dual-write backends ([0035](0035-historian-dual-write.md)) must either persist quality on **both** or document the backend as quality-incapable (experimental).
3. A **TCK** (JUnit) asserts round-trip GOOD / UNCERTAIN / BAD for the primary JDBC path before claiming Done.

### 2. Non-goals (this ADR)

- Changing live CEL quality semantics
- Compression/deadband algorithms (separate historian v2 epic)
- Raising scorecard Historian to 10 without TCK green

### 3. Rollout

- Expand schema (expand/contract per [0064](0064-release-compatibility-lts.md)).
- Backfill: null quality = “unknown / pre-v2”, not GOOD.

## Consequences

- Implementation PR must include TCK + docs; until then status remains **Accepted / implementation partial**.
- Amends 0025 follow-up list.

## Related

- [0025-telemetry-quality-flags](0025-telemetry-quality-flags.md), [0035-historian-dual-write](0035-historian-dual-write.md), [historian-tiers](../historian-tiers.md)
