# ADR-0078: MS SQL Server metadata certification (amends 0037)

## Status

**Accepted** (2026-11-19) — policy and certification contract. Implementation **Lab green (B1)** (Flyway `mssql/V1`, boot fences, TCKs, lockstep CI, lab harness [`tools/mssql-b1-lab`](../../tools/mssql-b1-lab/README.md)); production ban remains until the §8 named-SKU gate.

Amends [0037-relational-core-portability](0037-relational-core-portability.md) and [0064-release-compatibility-lts](0064-release-compatibility-lts.md): MS SQL Server is the **first** non-PG production engine target. This ADR defines what “certified” means, what is explicitly **not** claimed, and the gate that must pass before operators may set `ISPF_METADATA_DB_KIND=mssql` in production.

MySQL / Oracle follow-on charters: [0079](0079-mysql-metadata-certification.md), [0080](0080-oracle-metadata-certification.md) — implementation starts only after this ADR’s named-SKU gate.

## Context

Operators (including GxP EMS sites) often standardize on Microsoft SQL Server for DBA process, backup, and corporate policy. ISPF today:

- Runs the relational core on **PostgreSQL 16 (+ Timescale)** or **H2** (local/edge).
- Ships `MssqlDialect`, generated `db/migration/mssql/V1__baseline.sql`, and fail-closed B1 boot fences — **lab-certified**, not production until §8.
- Already reaches corporate MSSQL via **external** `DATA_SOURCE` / JDBC for reports and bindings (read paths). That is **not** the same as hosting `ISPF_DB` on MSSQL.

ADR-0037 promised portability via dialect packs. Without a certification contract, “works with any DB through JDBC” is misread as “platform metadata runs on any JDBC URL.”

## Decision

### 1. Two certification scopes (do not conflate)

| Scope | `ISPF_DB` contents | Status after this ADR |
|-------|--------------------|------------------------|
| **B1 — Metadata** | Object tree, RBAC, sessions, workflows, leases, jobs, config/audit tables. Event journal and variable history use a **non-MSSQL** store (`clickhouse`, or experimental `cassandra`/`scylla`) **or** stay out of the certified claim until B2. **Note:** historian `dual-write` writes its **primary** to `ISPF_DB` — that path is **B2**, not B1. There is no separate PostgreSQL historian DataSource when `ISPF_DB` is already MSSQL. | **First delivery target** |
| **B2 — Single-DB JDBC** | B1 + `ISPF_EVENT_JOURNAL_STORE=jdbc` + `ISPF_VARIABLE_HISTORY_STORE=jdbc` on the **same** MSSQL database (includes jdbc primary for dual-write, if used) | **Optional second gate**; only after B1 green |

Shipping B1 does **not** imply B2. Marketing and EMS proposals must name the scope.

### 2. Historian and long-retention honesty (B2)

On MSSQL there is **no** Timescale. Native partitioning of `variable_samples` requires a **schema fork** (partition key in every unique index — today the surrogate `id` PK blocks naive PARTITION BY `sampled_at`).

| Claim | Decision |
|-------|----------|
| 10-year raw archive **in** MSSQL rowstore | **Non-claim** |
| Multi-year raw scan of `variable_samples` finishing ≤ 20 s | **Non-claim** |
| Hot/operator windows on MSSQL jdbc (bounded tags, bounded range) | Allowed under B2 only with a **named fixture** and recorded p95 |
| Long archive / plant-wide year reports | External MSSQL archive via `DATA_SOURCE`, and/or ClickHouse/Parquet **if the site allows non-Microsoft engines**; otherwise external warehouse / report tool — **site assumption, named in the epic** |
| Retention purge on large rowstore | Prefer partition `SWITCH`/`TRUNCATE` **after** schema fork; until then cap retention and measure log/lock impact of `DELETE` under leader lease |

#### 2b. Allowed claims (do not erase §2)

The two non-claims above stay in force for **unbounded** wording (any plant-wide raw scan, any 10-year rowstore query, “≤ 20 s” without a fixture). A later gate may add a filled row here only with dated evidence. Empty template — **not filled by this ADR**:

| Field | Value |
|-------|--------|
| Fixture id | *(unset)* |
| Edition / CU | *(unset — one named non-Developer SKU)* |
| Tags × sample period × window | *(unset)* |
| Query text | *(unset)* |
| p95 and concurrency | *(unset)* |
| Primary vs replica | *(unset)* |
| Evidence path | `docs/evidence/` *(none yet)* |

Marketing may quote a row only after it is filled. Delivery board: [mssql-single-engine-ems-epic](../mssql-single-engine-ems-epic.md).

**Topology for that epic:** one ISPF JVM plus a SQL Server availability-group listener. ISPF active-active stays refused. Partition grain target is **monthly**. Historian DDL on MSSQL is a **named divergence**, not a Timescale twin. JDBC disk buffer in front of MSSQL is opt-in resilience, excluded from the “bytes are only in SQL” sentence, and is not a second-node copy.

Cutover remains **greenfield + bundle import** (0037/0064). Bundles restore **solution configuration**, not `variable_samples`, `event_history`, users/MFA, in-flight workflows, or audit chains from a prior PostgreSQL site.

### 3. Tenancy / RLS

PostgreSQL production path uses session GUC + `FORCE ROW LEVEL SECURITY` ([0066](0066-security-model.md), migration V86).

On MSSQL until a native `SESSION_CONTEXT` + security policy is implemented and gated:

1. Boot **refuses** when `ispf.tenant.db-row-isolation=true` (or equivalent) and dialect is MSSQL. Default `ISPF_TENANT_DB_ROW_ISOLATION` is **true** today — so a default config **will not boot** on MSSQL; single-tenant B1 must set `ISPF_TENANT_DB_ROW_ISOLATION=false` explicitly.
2. Single-tenant EMS may run with row isolation **off** (app path checks only) — **no** RLS claim on that engine.
3. Multi-tenant SaaS on MSSQL is **out of scope** until the native policy lands and 0066 is amended.

Do **not** document “app-layer RLS” as an equivalent to PostgreSQL RLS.

### 4. Application / tenant schemas

`MssqlDialect.activateSchemaSql` and `resetPlatformSchemaSql` today emit `USE …` / `USE dbo` (database switch) and are **incorrect** for SQL Server schemas. PostgreSQL `search_path` has no safe one-line twin: default schema is a **user** property; mutating it races the connection pool.

**B1 decision:** per-application schema switching (`ApplicationSchemaSession` / hard-mode tenant schemas that rely on session search_path) is **unsupported** on MSSQL until a named session-scoped mechanism exists and is tested. Solutions that need isolated SQL schemas keep using **external** `DATA_SOURCE` or stay on PostgreSQL. Certification tests must **fail closed** if those features are invoked on MSSQL.

### 5. Cluster and “shared MSSQL”

ISPF active-active ([0028](0028-horizontal-active-active-cluster.md), [0069](0069-cluster-consistency.md)) means **N JVMs**, **one writable relational primary**. SQL Server Availability Groups are not multi-master for writes.

| Rule | Requirement |
|------|-------------|
| Topology | All replicas use the **AG listener** (or single primary). Readable secondaries are **not** write targets |
| Isolation | `READ_COMMITTED_SNAPSHOT` (RCSI) **ON** for the ISPF database — otherwise the A-A cell is void |
| Tenancy of the instance | **Dedicated database** required for B1. **Dedicated instance** required for B2 (`store=jdbc` history) — no co-tenant ERP OLTP on the same instance |
| Locks | Keep table leases (`platform_driver_locks`, `platform_leader_locks`). Do not replace working leases with `sp_getapplock` without a dedicated design. Soft unique/PK conflicts must detect SQL Server **error codes `2627` / `2601`** (under SQLSTATE `23000`) — not bare `23000`, which also covers FK/CHECK/NOT NULL |

### 6. Flyway and lockstep upgrades

1. Install path: `db/migration/mssql/V1__baseline.sql` (greenfield squash of the relational core).
2. After certification, **every** new `postgresql/Vnn__*.sql` that changes the relational core needs an `mssql` twin **or** an explicit ANSI exception listed in CI — same expand/contract spirit as 0064.
3. Types: prefer `UNIQUEIDENTIFIER`, `BIT`, `VARBINARY(MAX)`, `NVARCHAR`, `DATETIME2` / `DATETIMEOFFSET`. Collation for object paths: **case-sensitive** collation named in the epic (CI and prod). Do not certify Developer-edition-only features as Standard.

### 7. Dialect and SQL surfaces beyond today’s SPI

Before lifting the production ban, extend portability for at least:

- Pagination (`LIMIT` → `OFFSET/FETCH` or `TOP`) in event journal and other JDBC stores that hardcode PostgreSQL/`LIMIT`
- Audit-chain concurrency (two connections append; chain verifies; dialect-aware conflict SQLSTATE)
- `pg_advisory_xact_lock` call sites (`AuditChainStore`, function-script `advisoryLock`) → dialect API with a correct MSSQL implementation or table-lock fallback that cannot silently succeed

Solution SQL (`ON CONFLICT`, PG-only functions in shipped scripts) is **not** auto-portable. B1 either ships a dialect-safe test bundle or lists function-script / internal SQL bindings against `ISPF_DB` as **unsupported** on MSSQL (external `DATA_SOURCE` remains the supported report path).

### 8. Certification gate (lift ban only after)

Lift “do not set `ISPF_METADATA_DB_KIND=mssql` in production” in 0037, both language editions of the inventory/README, **only** when all of the following pass for the named SKU (version + Standard|Enterprise|Azure SQL):

1. Empty DB → Flyway mssql baseline → server boot
2. Bundle import of a fixed certification bundle
3. Leader lease failover (two JVMs, one primary)
4. Concurrent audit-chain append + verify
5. Boot refuse when `db-row-isolation=true`
6. Unsupported schema-switch paths fail closed
7. For **B2 only**: history write + bucket aggregate query; retention/purge behavior documented; report SLO fixture (if any) recorded — else SLO stays a non-claim
8. Chaos/soak checklist from [cluster-chaos-soak-runbook](../cluster-chaos-soak-runbook.md) re-run against MSSQL for the A-A cell, or mark A-A **not certified** on that engine

Testcontainers Developer edition on Linux is a **lab** signal only; the epic must name the SKU that sales may claim.

### 9. Effort honesty

A plausible first slice is “empty dedicated DB, platform tables, login, one bundle import” (order of **weeks**, not a quarter). Full B1 gate including lockstep migration tax, audit concurrency, cluster failover, and SKU matrix is larger. B2 + year-scale rowstore is a **separate** project and must not be sold inside the B1 estimate.

## Consequences

- Sales / EMS proposals: prefer **external MSSQL** for corporate reports today; B1 removes PG only for **platform metadata** when certified; B2 is optional and capped.
- 0037 tier 3 stays **Not started** until the gate in §8 passes; then **Prod Yes (B1)** and optionally **Prod Yes (B2)** as separate cells.
- Risks: dual migration stream drift; pool poisoning if schema/`USE` is mishandled; noisy-neighbor if B2 shares an instance; false confidence from Developer-edition CI.

## Related

- [0037-relational-core-portability](0037-relational-core-portability.md)
- [0064-release-compatibility-lts](0064-release-compatibility-lts.md)
- [0066-security-model](0066-security-model.md)
- [0068-backup-and-dr](0068-backup-and-dr.md), [0069-cluster-consistency](0069-cluster-consistency.md)
- [storage-portability-inventory](../storage-portability-inventory.md)
- Epic: [mssql-certification-epic](../mssql-certification-epic.md)
