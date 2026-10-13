# ADR-0068: Backup and disaster recovery contract

## Status

**Accepted** (2026-10-13) — defines backup tiers and RPO/RTO honesty; does not invent a new backup product.

## Context

Operators already use tree JSON export (`PlatformBackupService` / Console) and ops `pg_dump` / volume snapshots ([deployment](../deployment.md), air-gap pack). Tenders ask for **RPO/RTO** and restore drills. Without an ADR, agents oversell “full platform backup” from tree export alone.

## Decision

### 1. Backup tiers (what is in scope)

| Tier | Artifact | Owner tool today | Restores |
|------|----------|------------------|----------|
| **T1 — Solution / tree** | Subtree JSON (`formatVersion` per backup API) | `PlatformBackupService`, Inspector export | Objects under chosen root; not PG app schemas alone |
| **T2 — Platform metadata DB** | PostgreSQL dump / volume snapshot | Ops `pg_dump`, VM/disk snapshot | Users, ACL, registry, bindings, deploy history |
| **T3 — Historian** | JDBC/Timescale and/or ClickHouse volumes per [0035](0035-historian-dual-write.md) | Ops snapshots / CH backup | Time-series; dual-write sites need **both** stores consistent |
| **T4 — Secrets** | Encrypted vault columns + **secrets-key** material | [0065](0065-secrets-key-rotation.md) | Useless without key; key is **not** in the tree |

A “full DR” claim requires **T2 + T3 + secrets-key escrow** at minimum; T1 alone is solution migration, not site DR.

### 2. Default RPO/RTO (lab honesty — not a SLA certificate)

| Profile | RPO (data loss window) | RTO (time to serve) | Status |
|---------|------------------------|---------------------|--------|
| **Dev / demostand** | Best-effort | Best-effort | Documented ops only |
| **Single-node prod** | ≤ 24 h if nightly `pg_dump` + volume snap | ≤ 4 h with runbook | **Partial** — runbook exists; no CI restore drill |
| **Cluster HA** | Bounded by PG HA + NATS durability ([0069](0069-cluster-consistency.md)) | Failover minutes for live path | **Partial** — chaos soak ≠ certified DR |

Sites may publish stricter numbers only with dated restore evidence under `docs/evidence/`.

### 3. Hard rules

1. Do not claim ClickHouse-only or tree-only backup as full DR.
2. Restore drills must name which tiers were restored and what was verified (login, one DEVICE poll, one history query).
3. Secrets-key escrow is a **people/process** control outside the object tree.

## Consequences

- Runbooks: [deployment](../deployment.md), [air-gap-deployment](../air-gap-deployment.md).
- Follow-up: CI or lab **restore drill** evidence (T2→login + T1 import smoke).
- Scorecard Deploy must not jump to 9+ without drill evidence.

## Related

- [0064-release-compatibility-lts](0064-release-compatibility-lts.md), [0065-secrets-key-rotation](0065-secrets-key-rotation.md), [0069-cluster-consistency](0069-cluster-consistency.md)
