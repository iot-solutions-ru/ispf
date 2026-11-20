# MSSQL B1 SKU promotion checklist (ADR-0078 §8)

> **Language:** Canonical English.  
> **Status:** Operator checklist — **not** a pass by itself. Testcontainers Developer edition is a **lab** signal only.  
> **Policy note:** Absolute forbid of `ISPF_METADATA_DB_KIND=mssql` is waived for the unsigned B1 envelope in [0037](decisions/0037-relational-core-portability.md). That waiver is **not** this checklist signed and **not** Prod Yes.

Promote `ISPF_METADATA_DB_KIND=mssql` to a production claim only after signing **one** named cell below (do not batch Standard + Azure + Enterprise into one signature).

## Named cell (fill one)

| Field | Value |
|-------|-------|
| Product | SQL Server 2022 **Standard** / Enterprise / Azure SQL DB / Azure SQL MI (circle one) |
| Build / CU | |
| Collation | Must be **case-sensitive** for paths (lab uses `Latin1_General_CS_AS`) |
| RCSI | `READ_COMMITTED_SNAPSHOT ON` verified |
| Dedicated database | Yes (B1) |
| Dedicated instance | Required only for B2 (not this checklist) |
| `ISPF_TENANT_DB_ROW_ISOLATION` | `false` (single-tenant) |
| History / journal | Non-JDBC (ClickHouse/Cassandra/Scylla) or disabled — B2 jdbc stores refuse boot |
| Cluster | `ISPF_CLUSTER_ENABLED=false` until MSSQL A-A soak exists |

## Gate evidence (attach)

1. Empty DB → Flyway `mssql/V1` → boot + health + login  
2. Cert bundle import (`mssql-b1-cert`) + `by-path` resolves  
3. Leader/driver lease failover (**two JVMs**, one writable primary)  
4. Concurrent audit-chain append + verify  
5. Boot refuse when row isolation on  
6. App schema switch fail-closed  
7. Boot refuse for jdbc history / dual-write / jdbc journal / cluster on MSSQL  

### Lab rehearsal (not a SKU signature)

| Evidence | Where |
|----------|--------|
| CI TCK / boot fences | PR matrix + `Mssql*` tests |
| AMD64 SQL Server 2022 stand | [`tools/mssql-b1-lab/stand-local.ps1`](../../tools/mssql-b1-lab/stand-local.ps1) |
| aarch64 Edge stand + fence | [`tools/mssql-b1-lab/run.sh`](../../tools/mssql-b1-lab/run.sh) |
| Metadata load numbers | [mssql-certification-epic](mssql-certification-epic.md) Phase 3 + [`loadtest.py`](../../tools/mssql-b1-lab/loadtest.py) |

Re-run the same seven gates on the **named** Standard / Enterprise / Azure SQL cell before signing.

Signer / date: __________________

Related: [0078](decisions/0078-mssql-metadata-certification.md), [mssql-certification-epic](mssql-certification-epic.md), [tools/mssql-b1-lab](../../tools/mssql-b1-lab/README.md).
