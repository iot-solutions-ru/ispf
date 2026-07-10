# ADR-0031: Cluster replica roles and platform job queue

## Status

Accepted (2026-07-05)

## Context

The ISPF cluster (ADR-0028) uses symmetric active-active replicas with shared PostgreSQL. REST reports (`ReportService.run`) execute **synchronously** on whichever replica nginx routes to. Heavy SQL blocks the HTTP thread and competes with HMI/API on the same JVM.

Operators request:

- offload heavy reports to dedicated worker nodes;
- no duplicate work on all replicas (not map-reduce «on every node»);
- keep a single object tree and PostgreSQL as source of truth.

**Federation (ADR-0008)** is for multi-site hub/spoke, not compute offload within one data center.

## Decision

### 1. Replica roles

Env `ISPF_REPLICA_ROLE`:

| Role | REST/WS | Driver poll | Job consumer |
| ---- | ------- | ----------- | ------------ |
| `all` (default) | yes | yes (with cluster + ownership) | yes |
| `api` | yes | no | no |
| `worker` | health + jobs API | no | yes |

Backward compatibility: no role = `all` (current behaviour).


- `ISPF_CLUSTER_JOB_CONSUMER_ENABLED` — explicit consumer disable on `all`/`worker`.
- Driver ownership disabled on `api` and `worker` regardless of `ISPF_CLUSTER_DRIVER_OWNERSHIP`.

Role is stored in `platform_cluster_replicas.replica_role` and exposed in `/api/v1/info`, cluster health.

### 2. Platform job queue (`platform_jobs`)

JDBC queue in PostgreSQL (like `platform_driver_locks` / `platform_leader_locks`):

```text
QUEUED → RUNNING → COMPLETED | FAILED
         ↑ expired TTL → re-QUEUED
```

- **Claim:** `SELECT … FOR UPDATE SKIP LOCKED` + UPDATE holder.
- **Worker:** `@Scheduled` loop on replicas with `isJobConsumerActive()`.
- **Result:** JSONB in `platform_jobs.result` (for `report_run` — same payload as sync `POST /run`).

### 3. Async reports API

| Method | Path | Response |
| ------ | ---- | -------- |
| POST | `/api/v1/reports/by-path/run-async?path=…` | `202 { jobId, status: QUEUED }` |
| GET | `/api/v1/platform/jobs/{jobId}` | `{ jobId, status, result?, errorMessage?, … }` |

Sync `POST /run` **unchanged** — for tests and legacy.

Web console: `runReportByPath` → submit async + poll until `COMPLETED`.

### 4. Topology (example)

```text
Clients → nginx (ip_hash) → api replicas (role=api)
                         ↘ worker replicas (role=worker, internal)
PostgreSQL ← platform_jobs (claim by workers)
```

On a small cluster without a dedicated worker, all replicas are `all` — jobs run locally (same as single-node).

### 5. Out of scope v1

- Map-reduce / sharded report jobs.
- NATS JetStream as queue transport (PostgreSQL sufficient for v1).
- Async export (PDF/YARG) — separate BL.
- Federation as compute tier.

## Consequences

- Flyway: `V62__platform_jobs.sql`, `V63__platform_cluster_replica_role.sql`.
- Classes: `PlatformJobService`, `PlatformJobWorkerScheduler`, `PlatformJobController`.
- `ClusterProperties.replicaRole()`, `isJobConsumerActive()`, `isDriverOwnershipActive()` account for role.
- ROADMAP: BL-144.

## Related

- [0028-horizontal-active-active-cluster](0028-horizontal-active-active-cluster.md) — base cluster
- [0029-cluster-live-variable-replica-sync](0029-cluster-live-variable-replica-sync.md) — live sync
- [cluster](../cluster.md) — roles and jobs runbook
- [roadmap](../roadmap.md) — replica roles + platform jobs
