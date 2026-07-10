# ADR-0032: Replica profiles and capabilities

## Status

Accepted (2026-07-05)

## Context

ADR-0031 introduced enum `ISPF_REPLICA_ROLE` (`all` / `api` / `worker`). On prod, replica-3 uses `all` outside nginx for drivers + jobs — role does not express intent. A composable model is needed without `ISPF_CLUSTER_JOB_CONSUMER_ENABLED` workarounds.

## Decision

### Profiles (presets)

| Profile | Env | Capabilities |
| ------- | --- | ------------ |
| `unified` | `ISPF_REPLICA_PROFILE=unified` (default) | all |
| `edge-api` | `edge-api` | http-public, ws, replica-sync, config-write, schedulers |
| `hmi-read` | `hmi-read` | http-public, ws, replica-sync |
| `io` | `io` | drivers, replica-sync, schedulers |
| `compute` | `compute` | jobs, replica-sync |

Backward compatibility: `ISPF_REPLICA_ROLE=all|api|worker` maps to profile.

Explicit override: `ISPF_REPLICA_CAPABILITIES=http-public,ws,replica-sync` (replaces preset caps).

Priority: `ISPF_REPLICA_CAPABILITIES` > `ISPF_REPLICA_PROFILE` > `ISPF_REPLICA_ROLE`.

### HTTP / WS gate

- `io` / `compute` — no public API (503 `REPLICA_CAPABILITY_DENIED`)
- `hmi-read` — no config mutations
- `/api/v1/info`, `/actuator/health`, `/api/v1/auth/*` — always
- `/ws/objects` — requires `ws`

### Subsystem gates

| Subsystem | Capability |
| --------- | ---------- |
| Driver ownership | `drivers` |
| platform_jobs consumer | `jobs` |
| Leader schedulers | `schedulers` |
| NATS replica subscribe | `replica-sync` |

### DB

`platform_cluster_replicas`: `replica_profile`, `replica_capabilities` (V64). `replica_role` — deprecated alias.

### API

`/api/v1/info` and `/api/v1/platform/cluster/health` return `replicaProfile`, `replicaCapabilities[]`, `replicaRole` (legacy).

## Consequences

- Classes: `ReplicaCapability`, `ReplicaProfile`, `ReplicaCapabilitySet`, `ReplicaCapabilityHttpFilter`
- ROADMAP: BL-145

## Related

- [0031-cluster-replica-roles-platform-jobs](0031-cluster-replica-roles-platform-jobs.md)
- [cluster](../cluster.md)
