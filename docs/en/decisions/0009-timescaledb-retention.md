# ADR-0009: TimescaleDB retention for variable history

## Status

Accepted (2026-06-21)

## Context

Variable history (`variable_samples`) grows with device count and poll frequency. A retention policy is required: platform default, per-variable override, hypertable in production.

## Decision

1. **Production storage** — PostgreSQL with TimescaleDB extension; table `variable_samples` is a hypertable (initialized at startup by `TimescaleHypertableInitializer`).
2. **Platform default** — `ispf.variable-history.retention-days` (default **90** days); applies when the variable has `historyRetentionDays = null`.
3. **Per-variable override** — flags `historyEnabled`, `historyRetentionDays` on the variable (and in model template); cleanup scheduler respects both levels.
4. **Write path** — debounce by `ispf.variable-history.min-interval-ms` on `VARIABLE_UPDATED`; disabled history writes no samples.
5. **Federated paths** — historian proxy through federation API; retention stays on the data source side (remote peer).
6. **Edge profile** — H2/SQLite without Timescale; same retention logic, without hypertable chunking.

## Consequences

- [variable-history](../variable-history.md) — operator and API contract.
- [deployment](../deployment.md) — docker image `timescale/timescaledb` for prod.
- Increasing retention in prod — ops: change config + if needed `SELECT drop_chunks` / Timescale policy (outside hot-path code).

## Related

- ROADMAP Phase 2.2 — TimescaleDB hypertables + retention
- REQ-PF variable history — [object-model](../object-model.md)
