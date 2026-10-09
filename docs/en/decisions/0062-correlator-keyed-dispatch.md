# ADR-0062: Correlator keyed dispatch and per-correlator transactions

> **Status:** Accepted — 2026-10-09. Hub: [doc-status.md](../doc-status.md).

## Context

`EventCorrelatorListener` is a single bus subscriber. Matching correlators were evaluated in one `@Transactional` loop on `processEventFired`. That caused:

1. One correlator's nested write failure could mark the shared transaction rollback-only and undo siblings.
2. Blocking `SEND_WEBHOOK` / email / SMS held the automation lane for every peer on the same event.
3. Concurrent automation workers could evaluate the same correlator out of order (`Instant.now()` per hit), breaking SEQUENCE / cooldown.

## Decision

1. **One subscription, keyed fan-out** — keep `AutomationRuleIndex` by `eventName`; route each correlator to a single-thread lane hashed by `correlatorId` (`CorrelatorKeyedExecutor`).
2. **`REQUIRES_NEW` per correlator** — pattern eval, window hits, and `lastTriggeredAt` commit independently (`TransactionTemplate`).
3. **Async notifications** — after commit, `SEND_*` actions run on `CorrelatorActionExecutor`; workflow / `SET_VARIABLE` / `FIRE_EVENT` stay on the evaluation lane.
4. **Event time** — use `ObjectChangeEvent.timestamp()` (not wall clock at dequeue) for windows and cooldown.
5. **Sync mode for tests** — `ispf.correlator.async-dispatch=false` and `async-actions=false` in `application-test.yml` so HTTP acceptance tests stay deterministic.

## Cluster ownership (deferred)

Multi-replica ownership of a correlator (single leader per id) is **out of scope** for this ADR. Redis/JDBC window stores already share hits; full serial ownership across replicas can follow ADR-0028 driver-lock patterns when needed.

## Consequences

- Broken filter / payload parse fails one correlator without aborting siblings (#427 + this ADR).
- Config: `ispf.correlator.*` (lanes, queues, async flags).
- Metrics: `ispf.correlator.errors.total`, dispatch/action drop counters and queue gauges.

## References

- [0014-automation-pipeline-evolution](0014-automation-pipeline-evolution.md)
- [0028-horizontal-active-active-cluster](0028-horizontal-active-active-cluster.md)
- [automation.md](../automation.md)
