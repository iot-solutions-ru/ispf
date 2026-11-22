# MSSQL active-active (A-A) promotion checklist

> **Language:** Canonical English.  
> **Status:** Operator checklist for **Prod Yes (A-A)** on MSSQL. Dialect fence lifted 2026-11-22. Residual AG failover / dual-JVM chaos soak is **tech-debt** ([parked-backlog](parked-backlog.md) **P-MSSQL-AA-SOAK**).

## Named cell

| Field | Value |
|-------|--------|
| Engine | SQL Server 2022 Enterprise Evaluation `16.0.4325.4` (engine-class basis; licensed Standard / Enterprise / Azure SQL DB\|MI inherit under owner extension — see [sku checklist](mssql-sku-promotion-checklist.md)) |
| Signer | local Evaluation + unit / 2026-11-22 |
| Evidence | [gate-evidence-aa-prod-yes-evaluation.json](../../tools/mssql-b1-lab/gate-evidence-aa-prod-yes-evaluation.json) |

## What Prod Yes (A-A) means

- `ispf.cluster.enabled=true` is **allowed** on MSSQL at the dialect boot fence.
- Topology remains **N JVMs, one writable primary** (AG listener or single primary). Not multi-master.
- `ClusterDependencyValidator` still requires NATS JetStream (and Redis when live-variable sync / path interest is on).
- RCSI ON; dedicated DB (B1) / dedicated instance (B2 JDBC).

## Tech-debt (open)

- [ ] Dual-JVM chaos soak journal on MSSQL ([cluster-chaos-soak-runbook](cluster-chaos-soak-runbook.md))
- [ ] Planned / forced AG failover under load
- [ ] Live lease reclaim across two JVMs (not same-JVM TCK simulation only)

## Non-claims

- Readable secondaries as write targets
- Azure SQL Edge (`ispf-lab` aarch64) as A-A evidence
- Product/scheduled SWITCH retention
