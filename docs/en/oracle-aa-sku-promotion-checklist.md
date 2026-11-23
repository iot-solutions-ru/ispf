# Oracle active-active (A-A) promotion checklist

> **Language:** Canonical English.  
> **Status:** Operator checklist for **Prod Yes (A-A)** on Oracle Database Free 23.26.x + operator-supplied `ojdbc17` with `ISPF_ORACLE_SINGLE_ENGINE=true`. Dialect fence lifted 2026-11-23. Residual dual-JVM chaos soak is **tech-debt** ([parked-backlog](parked-backlog.md) **P-ORACLE-AA-SOAK**).

## Named cell

| Field | Value |
|-------|--------|
| Engine | Oracle Database Free 23.26.x + `ojdbc17` on `loader.path` (same cell as [B1](oracle-sku-promotion-checklist.md) / [B2](oracle-b2-sku-promotion-checklist.md)) |
| Required flags | `ISPF_ORACLE_SINGLE_ENGINE=true`, `ISPF_CLUSTER_ENABLED=true`, JDBC history + journal, NATS JetStream, `SPRING_JPA_HIBERNATE_DDL_AUTO=none` |
| Signer / date | owner extension / 2026-11-23 |
| Stand / gate | [`stand-aa-local.ps1`](../../tools/oracle-b1-lab/stand-aa-local.ps1), [`gate-aa.ps1`](../../tools/oracle-b1-lab/gate-aa.ps1) |
| Gate evidence | [`gate-evidence-aa-free.json`](../../tools/oracle-b1-lab/gate-evidence-aa-free.json) (35 PASS / 0 FAIL) |
| Owner decision | [`gate-evidence-aa-prod-yes-free.json`](../../tools/oracle-b1-lab/gate-evidence-aa-prod-yes-free.json) |

## What Prod Yes (A-A) means

- `ispf.cluster.enabled=true` is **allowed** on Oracle only when single-engine is on.
- Topology remains **N JVMs, one writable primary**. Not RAC multi-master.
- `ClusterDependencyValidator` still requires NATS JetStream (and Redis when live-variable sync / path interest is on).

## Tech-debt (open)

- [ ] Dual-JVM chaos soak journal on Oracle ([cluster-chaos-soak-runbook](cluster-chaos-soak-runbook.md)) — **P-ORACLE-AA-SOAK**
- [ ] Failover under sustained historian / journal load
- [ ] Multi-hour soak on a named host (lab Docker Free stand is the owner-extension basis; Free caps remain a non-preferred soak cell)

## Non-claims

- Cluster without `ISPF_ORACLE_SINGLE_ENGINE`
- Cluster without NATS
- Readable replicas as write targets
- SE2 / EE / Autonomous / RDS without **P-ORACLE-SKU**
- Shipping `ojdbc`

Related: [0080](decisions/0080-oracle-metadata-certification.md) § Amendment 5, [oracle-certification-epic](oracle-certification-epic.md).
