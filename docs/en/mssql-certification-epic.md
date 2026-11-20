# Epic: MS SQL Server relational certification (ADR-0078)

> **Language:** Canonical English. Russian summary: [ru/mssql-certification-epic.md](../ru/mssql-certification-epic.md).  
> **Status:** Draft / Charter — delivery board for [ADR-0078](decisions/0078-mssql-metadata-certification.md). Tags: [doc-status.md](doc-status.md).  
> **Policy:** Greenfield only; no live PG→MSSQL data migration. MySQL/Oracle **not** scheduled.

## Goal

Make `ISPF_METADATA_DB_KIND=mssql` a **certified** production option for platform metadata (**scope B1**), with an optional second gate for jdbc journal/history on the same database (**scope B2**), without overselling 10-year raw archives or multi-year ≤20 s scans on MSSQL rowstore.

## Non-goals

- Live migration of an existing PostgreSQL `ISPF_DB`
- “Any JDBC URL” without a Flyway pack
- Claiming PostgreSQL RLS equivalence via “app-layer checks”
- Scheduling MySQL/Oracle certification
- Selling B2 as a GxP EMS 10-year / 20 s plant-wide report path

## Site assumptions (fill per customer)

| Assumption | Owner | GxP EMS example |
|------------|-------|-----------------|
| Non-Microsoft engines allowed? (ClickHouse / Parquet) | Customer + sales | TBD — if **no**, long archive stays external MSSQL / warehouse |
| SKU | Ops | SQL Server **Standard / Enterprise / Azure SQL** + version |
| Collation | Ops | Named **case-sensitive** collation; `NVARCHAR` paths |
| RCSI | DBA | `READ_COMMITTED_SNAPSHOT ON` |
| Dedicated DB / instance | Ops | DB required (B1); instance required (B2) |
| Tenant row isolation | Ops | Default is **on** → MSSQL boot refuses; single-tenant B1 sets `ISPF_TENANT_DB_ROW_ISOLATION=false` |

## Phases

### Phase 0 — Contract (docs) — **this epic + ADR-0078**

- [x] ADR-0078 Accepted (policy)
- [ ] Customer assumptions table filled for the named deal
- [ ] Explicit B1 vs B2 in proposal text

### Phase 1 — Dialect honesty (code prerequisites)

- [x] Fix schema activate/reset on MSSQL (**no** database `USE` for app schemas); fail closed for unsupported search_path features
- [x] Dialect API: pagination, unique-violation codes, advisory/table lock for audit-chain + function `advisoryLock`
- [x] Boot refuse: `db-row-isolation=true` + MSSQL (default isolation is on — B1 boot must set `ISPF_TENANT_DB_ROW_ISOLATION=false`)
- [x] Driver dependency: `mssql-jdbc` + `flyway-sqlserver` (runtime)
- [x] Unique/PK soft-conflict: SQL Server error codes `2627` / `2601` (not bare `23000`)

### Phase 2 — Flyway B1 baseline

- [x] `db/migration/mssql/V1__baseline.sql` (generator: `tools/mssql-baseline-generate.py`; greenfield translate from PG pack)
- [x] Types: `UNIQUEIDENTIFIER`, `BIT`, `VARBINARY(MAX)`, `NVARCHAR`, `DATETIME2`/`DATETIMEOFFSET`
- [x] Omit / replace: GIN, Timescale, PG RLS/plpgsql (V86) — skipped in generator
- [x] CI: empty MSSQL → Flyway → boot (`MssqlFlywayBaselineTckTest`; skips without Docker; image `mssql/server:2022-latest`)
- [x] B1 JDBC `LIMIT` stores wired through `RelationalDialect.limitClause()` (audit/config/bundle/function/federation/operator/event frames)

### Phase 3 — B1 behavioral gate

- [x] Bundle import (certification bundle) — fixture `mssql-b1-cert-bundle.json` + always-on parse/`register`-skip tests; **lab PG** (`ispf-lab`): import + `by-path` 200 for application/device/dashboard/data-source; **MSSQL** Testcontainers live Spring Boot import green (`MssqlLiveBundleImportTckTest`, dedicated `ispf` DB + RCSI via `MssqlTestSupport`); **AMD64 stand** (`tools/mssql-b1-lab/stand-local.ps1`, SQL Server 2022 + branch `bootJar` on `:18080`): Flyway V1, health UP, import + 20×6 path soak 0 fails; **ispf-lab aarch64** (`tools/mssql-b1-lab/run.sh`, Azure SQL Edge + same `bootJar` on `:18080`): RCSI+CS DB, health UP, import + 15×4 soak 0 fails, cluster fence refuses; **B1 metadata load** (`tools/mssql-b1-lab/loadtest.py`): read ~4350 rps p99~8 ms, write ~187 rps p99~114 ms, serial re-import ~5/s, mixed read/write ~3080 rps, 0 fails / health UP (concurrent import saturates Hikari on Edge — keep import serial). Official `mssql/server` amd64 image segfaults under QEMU on `ispf-lab` aarch64 (Edge used as ARM64 lab stand-in). Register no longer CREATE/USE app schemas on MSSQL
- [x] Leader/driver lease reclaim against one writable primary (`MssqlFlywayBaselineTckTest` ownership failover; Docker lab — same-JVM dual `DriverOwnershipService`, ADR-0078 CI matrix `lease-failover-mssql`)
- [x] Concurrent audit-chain append + verify (`AuditChainConcurrentAppendTest` H2 always-on; live MSSQL path in `MssqlFlywayBaselineTckTest` when Docker available)
- [x] Unsupported app-schema paths fail closed (`ApplicationSchemaSession.ensureSchemaExists` / `callInSchema` + `MssqlDialect`; `ApplicationSchemaSessionMssqlTest`)
- [x] Solution SQL policy: documented **unsupported** for in-DB function `exec` / PG-only scripts against `ISPF_DB` on MSSQL (`db/migration/mssql/README.md`, [known-limitations](known-limitations.md)); dialect-safe cert bundle still optional later
- [x] Absolute production forbid waived for the **unsigned B1 operator-risk envelope** in [0037](decisions/0037-relational-core-portability.md) (EN+RU) — product direction; **not** Prod Yes / not §8 pass
- [ ] Update 0037 tier 3 → **Prod Yes (B1)** — still **blocked** until one named non-Developer SKU is signed ([mssql-sku-promotion-checklist](mssql-sku-promotion-checklist.md))

### Phase 4 — Lockstep tax (ongoing while certified)

- [x] CI rule: `tools/mssql-flyway-lockstep-check.py` in `.github/workflows/ci.yml` (grandfather list in `mssql-flyway-exceptions.txt` — regenerate baseline to shrink)
- [x] Expand/contract discipline (0064) — process note in [0064](decisions/0064-release-compatibility-lts.md) pointing at lockstep script + exception file (grandfather ≠ dual-pack maturity / Prod Yes)

### Phase 5 — B2 (optional, separate estimate)

- [ ] Event journal stores return rows (no hardcoded `LIMIT`-only path)
- [ ] History write + bucket aggregate on MSSQL
- [ ] Retention: schema-fork partitioning **or** capped retention + measured purge
- [ ] Named report fixture (tag count, window, query text, p95) **or** keep ≤20 s as non-claim
- [ ] Dedicated **instance** requirement enforced in deploy docs
- [ ] 0037 cell **Prod Yes (B2)** only after gate

### Phase 6 — Cluster honesty

- [x] Mark A-A cell **not certified** on MSSQL — boot refuses `ispf.cluster.enabled=true` (`MssqlCertificationBootGuard`); PG lab soak is not MSSQL evidence
- [x] Document AG listener / RCSI / dedicated DB in [mssql-sku-promotion-checklist](mssql-sku-promotion-checklist.md) + `db/migration/mssql/README.md`

## Definition of Done (Prod Yes B1 — named-SKU §8)

All Phase 1–3 items green on the **named SKU**. Phase 4 process exists. Phase 5–6 only if those cells are sold.

**Not DoD:** Testcontainers Developer edition alone; H2-in-PG-mode green; README-only edits.

## CI matrix (minimum)

| Gate | Engine | What |
|------|--------|------|
| `flyway-mssql-baseline` | MSSQL Testcontainers (lab) | Empty DB apply V1 |
| `boot-mssql-b1` | same | Spring boot + health + login |
| `bundle-import-mssql` | same | Fixed cert bundle |
| `lease-failover-mssql` | 2 JVM or simulated ownership | Driver/leader lease reclaim |
| `audit-chain-concurrent-mssql` | same | Two writers, chain OK |
| `rls-boot-refuse-mssql` | same | Isolation on → refuse boot |
| `schema-unsupported-mssql` | same | App schema switch rejected |
| `lockstep-migration-check` | CI on PR | PG Vnn ↔ mssql twin / exception |
| `history-bucket-mssql` | **B2 only** | Write + aggregate |
| `report-slo-fixture-mssql` | **B2 only, optional** | Named p95 or skip cell |

SKU promotion (Standard / Azure SQL) is a **manual** checklist attached to the release note — not implied by Linux Developer containers.

## Blocking PG features (inventory for implementers)

| Feature | PG today | MSSQL B1 | MSSQL B2 |
|---------|----------|----------|----------|
| Flyway baseline | `postgresql/V1` + V71+ | Required rewrite | Same DB |
| Timescale hypertables | Optional accelerator | N/A | Non-goal; partition fork or cap |
| RLS + GUC (V86) | Prod default path | Boot refuse if isolation on | Same |
| `search_path` app schemas | Supported | Unsupported until designed | Same |
| `LIMIT` in JDBC stores | Audit/config + journal | Must dialect for **B1** (audit/config) and **B2** (journal) | Same |
| `pg_advisory_xact_lock` | Audit + scripts | Dialect / table lock | Same |
| Unique / PK violation | Soft conflict `23505` | Error codes **`2627` / `2601`** (not bare `23000`) | Same |
| GIN indexes | Present in DDL | Omit / btree substitute | Same |
| Leader/driver leases | Table-based | Keep tables | Same |
| External JDBC reports | Supported | **Still preferred** for corporate SQL | Same |

Keep [storage-portability-inventory](storage-portability-inventory.md) aligned when Phase 1 lands.

## Effort bands (planning only)

| Slice | Band | Notes |
|-------|------|-------|
| Phase 1–3 B1 gate | Larger than “weeks of DDL only” | Includes cluster/audit/boot guards |
| Phase 4 ongoing | Continuous | Dual migration tax |
| Phase 5 B2 | Separate project | Do not bundle into B1 quote |
| GxP EMS 10y / 20s plant-wide | Outside this epic | External archive / CH / warehouse |

## Related

- [ADR-0078](decisions/0078-mssql-metadata-certification.md)
- [ADR-0037](decisions/0037-relational-core-portability.md)
- [known-limitations](known-limitations.md) — do not claim MSSQL **Prod Yes** until §8 gate
