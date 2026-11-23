# MySQL active-active (A-A) promotion checklist

> **Language:** Canonical English.  
> **Status:** Operator checklist for **Prod Yes (A-A)** on MySQL Community Server 8.4.x + MariaDB Connector/J with `ISPF_MYSQL_SINGLE_ENGINE=true`. Dialect fence lifted 2026-11-23. Residual dual-JVM chaos soak is **tech-debt** ([parked-backlog](parked-backlog.md) **P-MYSQL-AA-SOAK**).

## Named cell

| Field | Value |
|-------|--------|
| Engine | MySQL Community Server 8.4.x + MariaDB Connector/J (same cell as [B1](mysql-sku-promotion-checklist.md) / [B2](mysql-b2-sku-promotion-checklist.md)) |
| Required flags | `ISPF_MYSQL_SINGLE_ENGINE=true`, `ISPF_CLUSTER_ENABLED=true`, JDBC history + journal, NATS JetStream |
| Signer / date | owner extension / 2026-11-23 |
| Stand / gate | [`stand-aa-local.ps1`](../../tools/mysql-b1-lab/stand-aa-local.ps1), [`gate-aa.ps1`](../../tools/mysql-b1-lab/gate-aa.ps1) |

## What Prod Yes (A-A) means

- `ispf.cluster.enabled=true` is **allowed** on MySQL only when single-engine is on.
- Topology remains **N JVMs, one writable primary**. Not multi-master Group Replication.
- `ClusterDependencyValidator` still requires NATS JetStream (and Redis when live-variable sync / path interest is on).

## Tech-debt (open)

- [ ] Dual-JVM chaos soak journal on MySQL ([cluster-chaos-soak-runbook](cluster-chaos-soak-runbook.md)) — **P-MYSQL-AA-SOAK**
- [ ] Failover under sustained historian / journal load
- [ ] Multi-hour soak on a named host (lab Docker stand is the owner-extension basis)

## Non-claims

- Cluster without `ISPF_MYSQL_SINGLE_ENGINE`
- Cluster without NATS
- Readable replicas as write targets
- MySQL Enterprise / managed cells without **P-MYSQL-SKU-RERUN**
- MariaDB Server

Related: [0079](decisions/0079-mysql-metadata-certification.md) § Amendment 6, [mysql-certification-epic](mysql-certification-epic.md).
