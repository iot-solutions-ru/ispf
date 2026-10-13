# ADR-0069: Cluster consistency contract (honesty)

## Status

**Accepted** (2026-10-13) — consolidates what active-active already guarantees; does **not** claim linearizability for live telemetry.

## Context

Cluster behaviour is split across [0028](0028-horizontal-active-active-cluster.md)–[0032](0032-replica-profiles-and-capabilities.md) and [cluster](../cluster.md). Buyers and agents need one table: what is strongly consistent, what is eventual, and what fails closed.

## Decision

### 1. Consistency by data plane

| Plane | Model | Notes |
|-------|--------|-------|
| **Structure / config (PG)** | Strong within the DB transaction isolation ISPF uses | Single logical writer for schema mutations; replicas apply via structure sync ([0030](0030-cluster-config-structure-replica-sync.md)) |
| **Live variable values** | **Single-writer** owner + **eventual** mirror ([0029](0029-cluster-live-variable-replica-sync.md)) | Coalesced NATS updates; not linearizable cross-replica reads without sticky routing |
| **Driver I/O** | Sticky ownership on monotonic clocks ([0028](0028-horizontal-active-active-cluster.md)) | Dual I/O forbidden; on lease loss stop |
| **Historian samples** | Append-mostly; dual-write secondary may lag ([0035](0035-historian-dual-write.md)) | Secondary errors must not invent success |
| **Correlator / jobs** | Profile/capability ownership ([0031](0031-cluster-replica-roles-platform-jobs.md), [0062](0062-correlator-keyed-dispatch.md)) | Not “every replica runs every job” |

### 2. Hard rules

1. **Do not claim linearizable live reads** across replicas without sticky session / owner affinity.
2. Core NATS **without** JetStream: structure loss is **logged**, not magically repaired — production clusters must enable JetStream.
3. **Fail-closed startup (Wave 1C):** when `ispf.cluster.enabled=true`, `ClusterDependencyValidator` aborts JVM start if NATS/JetStream is unavailable for structure sync, or if Redis is disabled while live mirror / path interest is active. Silent no-op bridge is not allowed.
4. Failover SLOs are **ops evidence**, not product certificates, until a dated chaos/restore journal exists.

### 3. Failover honesty

| Event | Expected | Not claimed |
|-------|----------|-------------|
| Owner lease loss | Previous owner stops I/O; new owner after lease | Zero sample loss |
| PG primary failover | Platform follows DB HA runbook | Automatic multi-region |
| NATS partition | Degraded sync; no dual-write invent | Perfect split-brain healing |

## Consequences

- Amends reading of 0028–0032: this ADR is the tender-facing consistency summary.
- Follow-up: optional CI assert “no dual owner”; compose profiles should set `ISPF_NATS_JETSTREAM_ENABLED=true` when NATS runs with `-js`.

## Related

- [0028](0028-horizontal-active-active-cluster.md) … [0032](0032-replica-profiles-and-capabilities.md), [cluster](../cluster.md), [0070-cluster-rolling-upgrade](0070-cluster-rolling-upgrade.md)
