# MSSQL B1 SKU promotion checklist (ADR-0078 §8)

> **Language:** Canonical English.  
> **Status:** Operator checklist — **not** a pass by itself. Testcontainers Developer edition is a **lab** signal only.

Promote `ISPF_METADATA_DB_KIND=mssql` to a production claim only after signing **one** named cell below (do not batch Standard + Azure + Enterprise into one signature).

## Named cell (fill one)

| Field | Value |
|-------|-------|
| Product | SQL Server 2022 **Enterprise (Evaluation)** — 180-day Enterprise-class trial, not a perpetual Standard/Enterprise license |
| Build / CU | `16.0.4325.4` RTM (`Enterprise Evaluation Edition (64-bit)`); image `mcr.microsoft.com/mssql/server:2022-latest`; `MSSQL_PID=Evaluation` |
| Collation | `Latin1_General_CS_AS` on database `ispf` |
| RCSI | `READ_COMMITTED_SNAPSHOT ON` verified |
| Dedicated database | Yes (`ispf`) |
| Dedicated instance | Required only for B2 (not this checklist) |
| `ISPF_TENANT_DB_ROW_ISOLATION` | `false` (single-tenant) |
| History / journal | Disabled (`ISPF_VARIABLE_HISTORY_ENABLED=false`, `ISPF_EVENT_JOURNAL_ENABLED=false`) |
| Cluster | `ISPF_CLUSTER_ENABLED=false` (A-A **not certified**) |

## Gate evidence (attach)

1. Empty DB → Flyway `mssql/V1` → boot + health + login — **PASS** (2026-11-20, local `:18080`)
2. Cert bundle import (`mssql-b1-cert`) + `by-path` resolves — **PASS** (4 paths)
3. Two JVMs on one writable primary — second non-clustered JVM **PASS** (`:18081` health UP, shared `ispf`). Live lease reclaim is **not** asserted with cluster off; lease failover stays on CI (`MssqlFlywayBaselineTckTest`)
4. Audit chain — serial reimport **PASS** (`audit_event_chain` row count > 0). Concurrent append + hash verify is **CI only** (`AuditChainConcurrentAppendTest`, `MssqlFlywayBaselineTckTest`), not live on this cell
5. Boot refuse when row isolation on — **PASS**
6. App schema / HARD isolation fail-closed — HARD boot refuse **PASS** on this cell. Session schema switch (`ApplicationSchemaSession`) is CI `ApplicationSchemaSessionMssqlTest` (no live `USE` probe)
7. Boot refuse for jdbc history / dual-write / jdbc journal / cluster on MSSQL — **PASS** (issue #814 close: 14 PASS / 0 FAIL on Evaluation). `gate-evidence.sh` re-checks these fences

Evidence: issue [#814](https://github.com/iot-solutions-ru/ispf/issues/814) close comment + [`tools/mssql-b1-lab/gate-evidence-local-evaluation.json`](../../tools/mssql-b1-lab/gate-evidence-local-evaluation.json). Items 3 (lease reclaim), 4 (concurrent audit append), and 6 (session schema switch) also have CI TCK coverage (`MssqlFlywayBaselineTckTest`, `AuditChainConcurrentAppendTest`, `ApplicationSchemaSessionMssqlTest`).

**License note:** Evaluation proves the SQL Server 2022 **Enterprise** engine class for B1 metadata — not Standard feature caps and not Azure SQL DB/MI PaaS. A production host still needs a licensed SQL Server Standard or Enterprise, or Azure SQL Database / Managed Instance. Re-run this checklist before signing those cells. Azure SQL Edge and Developer edition are not this signature. B2 jdbc history/journal and active-active stay **not certified** (boot refuses; opt-in single-engine is a separate epic).

### Lab rehearsal (not a SKU signature)

| Evidence | Where |
|----------|--------|
| CI TCK / boot fences | PR matrix + `Mssql*` tests |
| AMD64 SQL Server 2022 stand | [`tools/mssql-b1-lab/stand-local.ps1`](../../tools/mssql-b1-lab/stand-local.ps1) |
| aarch64 Edge stand + fence | [`tools/mssql-b1-lab/run.sh`](../../tools/mssql-b1-lab/run.sh) |
| Metadata load numbers | [mssql-certification-epic](mssql-certification-epic.md) Phase 3 + [`loadtest.py`](../../tools/mssql-b1-lab/loadtest.py) |

This file is signed for the Evaluation cell above. Re-run the seven gates before signing any **additional** licensed Standard / Enterprise / Azure SQL cell.

Signer / date: local Evaluation gate / 2026-11-20

Related: [0078](decisions/0078-mssql-metadata-certification.md), [mssql-certification-epic](mssql-certification-epic.md), [tools/mssql-b1-lab](../../tools/mssql-b1-lab/README.md).
