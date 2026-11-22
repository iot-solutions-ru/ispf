# MSSQL B2 SKU promotion checklist (ADR-0078 single-engine)

> **Language:** Canonical English.  
> **Status:** Operator checklist for **Prod Yes (B2)** on the named Evaluation cell (owner-extended to licensed Standard / Enterprise / Azure SQL DB|MI). Not a perpetual license from Evaluation. A-A signed separately.

Promote opt-in `ISPF_MSSQL_SINGLE_ENGINE=true` JDBC history/journal only after signing **one** named cell below.

## Named cell (signed)

| Field | Value |
|-------|-------|
| Product | SQL Server 2022 **Enterprise (Evaluation)** — 180-day Enterprise-class trial |
| Build / CU | `16.0.4325.4` RTM (`Enterprise Evaluation Edition (64-bit)`) |
| Collation | Case-sensitive (`_CS_`) on the single-engine DB (`ispf_b2` lab) |
| RCSI | `READ_COMMITTED_SNAPSHOT ON` |
| Dedicated database | Yes |
| Dedicated instance | Lab: Evaluation container hosts only ISPF DBs. Production B2 still requires a dedicated SQL Server instance (no co-tenant OLTP) |
| `ISPF_MSSQL_SINGLE_ENGINE` | `true` |
| History / journal | JDBC (`ISPF_VARIABLE_HISTORY_STORE=jdbc`, `ISPF_EVENT_JOURNAL_STORE=jdbc`) |
| Cluster | Optional; **Prod Yes (A-A)** — [A-A checklist](mssql-aa-sku-promotion-checklist.md); NATS required when enabled |
| Wall-clock gate re-run | Capped at **≤1 hour**; signed re-run completed in ~20 s (2026-11-22) |
| `ISPF_TENANT_DB_ROW_ISOLATION` | `false` (single-tenant; no RLS claim) |
| `ISPF_HISTORIAN_DEPLOY_PROFILE` | `mssql-single-engine` (lab) |

## What Prod Yes (B2) means on this cell

- JDBC variable history write + `DATE_BUCKET` aggregate on `observed_at`
- JDBC event journal list/smoke
- Retention **in force**: application `DELETE` + `ispf.relational.mssql.max-retention-days` (default **90**)
- Empty new DB may get Gate C1 monthly partitions (`V101.2` `APPLIED`); nonempty DBs stay `SKIPPED_NONEMPTY`
- Lab maintenance SWITCH path exists (ADR-0081 script + confirm evidence) — **not** a JVM/scheduled product job

## Explicit non-claims (still)

- Unbounded multi-year raw archive / 10-year sales claim
- Plant-wide ≤20 s raw scans (ADR-0078 §2b stays empty unless a named fixture is filed)
- Product or scheduled monthly `SWITCH` retention
- Default-on chart cutover (`ISPF_MSSQL_OBSERVED_ROLLUP_READ` stays default off)
- Completed AG failover / dual-JVM chaos soak (tech-debt **P-MSSQL-AA-SOAK**; A-A dialect fence lifted; NATS JetStream required)
- Per-SKU re-run still recommended (tech-debt **P-MSSQL-SKU-RERUN**); owner extension already signs licensed cells

## Gate evidence (attach)

1. Gate B-core — **PASS** 13/0 — [`gate-evidence-b2-prod-yes-evaluation.json`](../../tools/mssql-b1-lab/gate-evidence-b2-prod-yes-evaluation.json) (2026-11-22, `:18082`, DB `ispf_b2`)
2. Prior Gate B lab — [`gate-evidence-b2-core-evaluation.json`](../../tools/mssql-b1-lab/gate-evidence-b2-core-evaluation.json)
3. Lab SWITCH confirm (maintenance, disposable) — [`gate-evidence-switch-confirm-evaluation.json`](../../tools/mssql-b1-lab/gate-evidence-switch-confirm-evaluation.json)
4. B1 metadata cell remains signed — [mssql-sku-promotion-checklist](mssql-sku-promotion-checklist.md)

Signer / date: local Evaluation B2 gate / 2026-11-22

Related: [0078](decisions/0078-mssql-metadata-certification.md), [0037](decisions/0037-relational-core-portability.md), [mssql-single-engine-ems-epic](mssql-single-engine-ems-epic.md), [ADR-0081](decisions/0081-mssql-historian-switch-retention.md).
