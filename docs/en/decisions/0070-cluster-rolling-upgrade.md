# ADR-0070: Cluster rolling upgrade (N → N+1)

## Status

**Accepted** (2026-10-13) — defines the Wave 3 gate above [0064](0064-release-compatibility-lts.md) lab Wave 2.

## Context

[release-upgrade](../release-upgrade.md) and `BundleUpgradeCompatGateTest` prove **formatVersion** accept/reject and catalog redeploy on one platform build. Same-version rolling restart scripts exist. **Cross-version** cluster upgrade (image N → N+1 on shared PG) is still unproven in CI.

## Decision

### 1. What “Done” means

All of the following:

1. **Expand/contract** Flyway only ([0064](0064-release-compatibility-lts.md) §4) — no destructive drop in the same release that still reads the column.
2. **Dual-image lab**: compose/Helm job runs replica A on version N, replica B on N+1, shared PostgreSQL, JetStream on.
3. **Soak**: ≥ 30 min mixed traffic (login, poll DEVICE, history query, one bundle redeploy) with incident log.
4. **Evidence** under `docs/evidence/release-compat/` with image digests and pass/fail.

Until then status stays **lab Wave 2 only**.

### 2. Non-goals

- Zero-downtime guarantee for every site topology
- Automatic downgrade
- Multi-major jumps (N → N+2) in one job

## Consequences

- [release-upgrade](../release-upgrade.md) points here for the residual.
- Scorecard Deploy 9+ blocked until Wave 3 evidence exists.

## Related

- [0064-release-compatibility-lts](0064-release-compatibility-lts.md), [0069-cluster-consistency](0069-cluster-consistency.md), [release-upgrade](../release-upgrade.md)
