# ADR-0028: Horizontal active-active cluster

## Status

Accepted (2026-07-04)

## Context

ISPF targets **one object tree** (`root.platform.*`) shared by all application nodes. Operators and admins need:

- **Active-active API/HMI** — any healthy replica serves REST/BFF/WebSocket traffic.
- **Scale-out** — add Docker nodes behind nginx when load grows.
- **Failover** — when one node dies, others absorb traffic without manual intervention.
- **No duplicate driver I/O** — two replicas must not poll/write the same device concurrently.

Existing building blocks (Phase 2–22):

- PostgreSQL as single source of truth for object tree.
- `PlatformLeaderLockService` — JDBC locks for singleton schedulers.
- NATS/JetStream replica fan-out — cross-replica WebSocket/object-change sync ([messaging](../messaging.md)).
- Redis optional — correlator windows + ACL cache shared across replicas.
- Optimistic concurrency (`If-Match` / revision) — safe parallel config edits ([collaboration](../collaboration.md)).
- Demand-driven object change pub/sub ([0024-demand-driven-variable-change-pubsub](0024-demand-driven-variable-change-pubsub.md)) — reduces useless work when scaling API tier.

**Cluster ≠ federation:** cluster = N replicas, **one database**, one site. Federation ([0008-federation-topology](0008-federation-topology.md)) = multiple sites / edge agents with catalog sync.

## Decision

### 1. Topology

```text
Clients → nginx (round-robin REST, sticky WS) → ispf-server × N
                                              ↓
                         PostgreSQL + Redis (optional) + NATS (required for multi-replica WS sync)
```

Each replica:

- Unique `ISPF_REPLICA_ID` (defaults to random UUID; set explicitly in Docker/K8s).
- Same `ISPF_DB_*` JDBC URL.
- `ISPF_NATS_ENABLED=true`, `ISPF_NATS_REPLICA_EVENTS=true` recommended.
- `ISPF_CLUSTER_ENABLED=true` for driver ownership and cluster health API.

### 2. Active-active tiers

| Tier | Mode | Mechanism |
| ---- | ---- | --------- |
| REST / BFF / reports | Active-active | Stateless handlers; shared DB; nginx round-robin |
| WebSocket `/ws/objects` | Active-active + sticky | Client pinned to one replica; NATS fan-out syncs other replicas |
| Platform schedulers | Active-passive (one leader) | `platform_leader_locks` ([PlatformLeaderLockService](../../../packages/ispf-server/src/main/java/com/ispf/server/platform/PlatformLeaderLockService.java)) |
| Device driver poll loops | **Exactly-one owner** | `platform_driver_locks` + `DriverOwnershipService` (BL-136) |
| Binding periodic tick | Active-passive (one leader) | Existing leader lock on `binding_periodic_scheduler` |
| Event journal / historian writes | Active-active (DB) | Append to shared store; ClickHouse optional for scale ([roadmap](../roadmap.md)) |

### 3. Driver ownership

Before starting a driver poll loop, replica **must acquire** JDBC lock on `device_path`:

- Heartbeat renew every `ispf.cluster.driver-lock-renew-seconds` (default 10s).
- TTL `ispf.cluster.driver-lock-ttl-seconds` (default 30s).
- On stop/shutdown: release lock.
- On renew failure: stop local poll (another replica may take over).
- Background reaper: expired locks → eligible replica auto-starts configured driver.

When `ispf.cluster.enabled=false` (default single-node): ownership is no-op (always local owner).

### 4. Ingress (nginx)

- `/api/` — `upstream` with multiple `ispf-server-*` backends, `max_fails` + `fail_timeout`.
- `/ws/` — `ip_hash` for sticky sessions + WebSocket upgrade headers.
- Static web-console served from nginx; API proxied to backend pool.

Reference: `deploy/nginx-cluster.conf`, `deploy/docker-compose.cluster.yml`.

### 5. Environment variables (multi-replica)

| Variable | Required | Description |
| -------- | -------- | ----------- |
| `ISPF_REPLICA_ID` | Recommended | Stable per-node id (e.g. `replica-1`) |
| `ISPF_CLUSTER_ENABLED` | For cluster | `true` enables driver ownership + cluster health |
| `ISPF_NATS_ENABLED` | Recommended | Cross-replica event fan-out |
| `ISPF_NATS_REPLICA_EVENTS` | Recommended | `true` (default) |
| `ISPF_REDIS_ENABLED` | Optional | Shared correlator windows / ACL cache |
| `ISPF_DB_URL` | Required | Same JDBC URL on all replicas |

Platform properties mirror: `ispf.cluster.*`, `ispf.nats.*` in [application.yml](../../../packages/ispf-server/src/main/resources/application.yml).

## Consequences

- Horizontal scale for API/automation read path.
- Survives single-node failure with nginx passive health.
- Driver I/O safe across replicas via DB locks.

Risks:

- PostgreSQL remains single writer — scale-out has limits on write-heavy historian; use ClickHouse path ([roadmap](../roadmap.md)).
- NATS + Redis become operational dependencies for full multi-replica UX.
- Sticky WS optional when [0029-cluster-live-variable-replica-sync](0029-cluster-live-variable-replica-sync.md) live sync + Redis path interest are enabled; REST round-robin safe for HMI reads.

## Related

- [0024-demand-driven-variable-change-pubsub](0024-demand-driven-variable-change-pubsub.md) — demand-driven pub/sub
- [0029-cluster-live-variable-replica-sync](0029-cluster-live-variable-replica-sync.md) — live variable RAM mirror
- [roadmap](../roadmap.md) — cluster backlog
- [cluster](../cluster.md) — runbook
- [deployment](../deployment.md) — multi-instance deploy
- [messaging](../messaging.md) — NATS replica fan-out
