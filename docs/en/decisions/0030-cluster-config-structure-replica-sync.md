# ADR-0030: Cluster config and structure replica sync

## Status

Accepted (2026-07-05)

## Context

[0028-horizontal-active-active-cluster](0028-horizontal-active-active-cluster.md) shares one PostgreSQL tree across N replicas. [0029-cluster-live-variable-replica-sync](0029-cluster-live-variable-replica-sync.md) mirrors **live telemetry** to follower RAM via NATS payloads with `value`.

Config and structure changes (create/update/delete objects, persisted variables such as mimic `diagram`, dashboard layout, bindings) were still inconsistent:

| Symptom | Cause |
| ------- | ----- |
| Mimic saved on replica-A, empty on replica-B | Config `VARIABLE_UPDATED` fan-out blocked or follower RAM stale |
| Delete «does not work» — object reappears | `DELETED` not fan-out (demand-driven gate) or follower RAM not updated |
| Rename/metadata stale on round-robin REST | `UPDATED` only reloaded path when missing from follower RAM |
| Factory reset as only fix | Desync between PG (truth) and per-replica RAM |

[0024-demand-driven-variable-change-pubsub](0024-demand-driven-variable-change-pubsub.md) skips publication when no local WS/automation subscribers. That is correct for telemetry volume but **wrong for cluster structure/config** — followers must always learn writes from other replicas.

## Decision

### 1. Always fan-out structure and config writes in cluster mode

When `ispf.cluster.enabled=true`:

- **`publishStructureChange`** (CREATED / UPDATED / DELETED) always publishes Spring events (after DB commit), regardless of `StructureChangeSubscriptionRegistry` interest.
- **`publishConfigVariableChange`** already always publishes (v0.9.92+).

Demand-driven gating remains for **telemetry** `VARIABLE_UPDATED` only.

### 2. Follower RAM apply via PostgreSQL reload

`ClusterObjectTreeReplicaSync` on NATS ingress (non-telemetry, non-`replicaIngress`):

| Event | Follower action |
| ----- | --------------- |
| `CREATED`, `UPDATED` | `ObjectManager.reloadPathFromDatabase(path)` |
| `DELETED` | `ObjectManager.removePathFromMemoryIfPresent(path)` (subtree cascade) |
| `VARIABLE_UPDATED` (config) | `ObjectManager.syncVariableFromDatabase(path, name)` |

`ObjectManager.delete` removes from PostgreSQL even when the path is absent from local RAM (LB may route DELETE to any replica). `require()` evicts RAM ghosts when PG row is missing (cluster mode, post-init).

`reloadPathFromDatabase` upserts node metadata and all persisted variables from PG; removes RAM variables absent in PG.

Live telemetry continues to use ADR-0029 NATS payload + `ClusterVariableReplicaApplier` (`replicaIngress`).

### 3. After-commit publication

Structure and config events defer fan-out until transaction commit (existing `*AfterCommit` hooks) so followers read committed PG rows.

### 4. No inline config value in NATS (v1)

Config variables reload from PG on follower. Optional future optimization: include `value` in NATS like telemetry.

## Pipeline

```text
Writer replica
  → PG commit
  → ObjectChangeEvent (structure or config)
  → NatsEventBridge → ispf.events.{created|updated|deleted|variable_updated}

Follower replica
  → NatsObjectChangeSubscriber
  → ClusterObjectTreeReplicaSync
  → reloadPathFromDatabase / removePath / syncVariableFromDatabase
  → local WS push (explorer invalidates tree)
```

## Consequences

- Create/update/delete and mimic/dashboard config consistent on any replica behind LB.
- No factory reset needed for RAM desync after normal CRUD.
- Complements ADR-0029 without duplicating telemetry path.

Risks:

- Extra PG reads on followers per config/structure event (low frequency vs telemetry).
- Slightly higher NATS traffic for admin CRUD (acceptable).

## Related

- [0028-horizontal-active-active-cluster](0028-horizontal-active-active-cluster.md) — cluster topology
- [0029-cluster-live-variable-replica-sync](0029-cluster-live-variable-replica-sync.md) — live telemetry mirror
- [0024-demand-driven-variable-change-pubsub](0024-demand-driven-variable-change-pubsub.md) — demand-driven (telemetry only in cluster)
- [cluster](../cluster.md) — operator guide and troubleshooting
- ROADMAP BL-143 — integration test + smoke `--config-sync`
