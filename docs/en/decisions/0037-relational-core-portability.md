# ADR-0037: Relational core portability

## Status

Accepted (2026-07-07)

## Context

ISPF stores platform configuration (object tree, RBAC, workflow, federation, cluster jobs) in **one** relational backend via Flyway + JPA/JDBC. Time-series (event journal, variable history) are already abstracted (`EventJournalStore`, `VariableHistoryQueryStore`).

Operators need:

1. **Single-DB** — metadata + events + history in one DBMS (`store=jdbc`).
2. **Certification matrix** — PostgreSQL (prod), PG-compatible engines, H2 (edge), enterprise (MSSQL, MySQL, Oracle) as migration packs mature.
3. **External JDBC Data Sources** — `data-source-v1` objects as connectors to **external** databases for reports, SQL bindings, and application migrations (they do not replace the metadata DB).

Previously ADR-0015/0016/0025 stated «PostgreSQL for relational core» — this ADR **does not revoke** PostgreSQL as the primary certified prod engine; it introduces an **SPI and roadmap** for portability.

## Decision

### 1. RelationalDialect SPI

Package `com.ispf.server.relational`:

- `RelationalDialect` — quoting, schema switch, `SKIP LOCKED`, bucket aggregation SQL, Flyway locations, Timescale support flag.
- `RelationalDialectDetector` — `ISPF_METADATA_DB_KIND` or auto-detect from JDBC URL.
- Implementations: `PostgreSqlDialect`, `H2Dialect`, `MssqlDialect`, `MysqlDialect`, `OracleDialect` (enterprise — as certified).

### 2. Flyway migration packs

```
db/migration/postgresql/   — V1__baseline.sql + incremental V71+ (prod path)
db/migration/h2/           — README only; default H2 uses postgresql pack in MODE=PostgreSQL
db/migration/mssql/        — README only until certified greenfield baseline
db/migration/mysql/        — README only (planned)
db/migration/oracle/       — README only (planned)
```

`FlywayConfigurationCustomizer` selects `locations` by active dialect.

#### Amendment (2026-10-12) — PostgreSQL after the V1–V70 squash + expand/contract

Legacy increments **V1–V70 were squashed** into `V1__baseline.sql` (2026-07-07). **After that squash**, PostgreSQL again uses **incremental Flyway scripts** (`V71__…` onward). New installs apply `V1` then every later version in order. Prefer **expand/contract** DDL ([0064-release-compatibility-lts](0064-release-compatibility-lts.md)). Do **not** treat the pack as “baseline only forever”.

| Scenario | Action |
|----------|--------|
| New PostgreSQL install | Empty DB → Flyway applies `V1__baseline.sql` then `V71+` |
| ISPF version upgrade on same PostgreSQL | Flyway applies new `Vnn__*.sql` not yet in `flyway_schema_history` |
| Change `ISPF_METADATA_DB_KIND` | New empty DB/schema; configuration via **bundles** (`package import`) — **no** in-place engine migration |
| Legacy DB still on pre-squash V1–V70 history | One-time `deploy/vps-flyway-repair.sh` — squash history to V1 + repair checksum, then apply `V71+` |
| MSSQL / MySQL / Oracle | **Not started** — README placeholders only; do not set `ISPF_METADATA_DB_KIND` to those engines in production |

Solution configuration (objects, applications, mimics) lives in **bundle export/import**, not in «moving» metadata rows between DBMS engines.

### 3. Cluster policy

All cluster replicas (ADR-0028) use the **same** `ISPF_METADATA_DB_KIND` and `ISPF_DB_URL`. Engine change — new DB + bundle import (planned maintenance), not in-place data migration.

### 4. Data Sources: internal vs external

| `connectionMode` | Fields | Purpose |
|------------------|--------|---------|
| `internal` (default) | `schemaName` | Schema inside `ISPF_DB` (as before) |
| `external` | `jdbcUrl`, `jdbcDriverClass`, `jdbcUsername`, `jdbcPassword`, `poolSize` | External DB for SQL bindings / reports / migrations |

`ExternalDataSourceRegistry` — Hikari pool per object; `DataSourceSqlSession` — single SQL execution point.

### 5. Single-DB preset

Default mode: `ISPF_EVENT_JOURNAL_STORE=jdbc`, `ISPF_VARIABLE_HISTORY_STORE=jdbc` — everything in `ISPF_DB`. UI: «Single DB» preset on Database settings tab.

## Certification matrix

| Tier | Engine | Prod | Migrations | Notes |
|------|--------|------|------------|-------|
| 0 | PostgreSQL 16 + Timescale | Yes | `postgresql/V1` + `V71+` | Primary; software registry |
| 1 | PG-compatible (Yugabyte, Cockroach) | On request | `postgresql/` | Test per vendor |
| 2 | H2 (edge/dev) | Edge only | `postgresql/` + PG compat mode | `application-local.yml` |
| 3 | MS SQL Server | Not started | `mssql/README.md` only | First enterprise target when baseline lands |
| 4 | MySQL, Oracle | Not started | README only | After MSSQL |

## Consequences

- single abstraction; explicit single-DB scenario; external JDBC for integrations without mixing with metadata.

Risks:

- enterprise engines certified one at a time; cluster requires the same engine on all replicas; DBMS change — greenfield + bundles, not live migration.

**Code:** `com.ispf.server.relational.*`, `DataSourceSqlSession`, `ExternalDataSourceRegistry`, `data-source-v1` extension, UI DataSourceEditor + Database settings.

## Related

- [storage-portability-inventory](../storage-portability-inventory.md)
- [deployment](../deployment.md) § Storage deployment modes
- [0028-horizontal-active-active-cluster](0028-horizontal-active-active-cluster.md), [0016-clickhouse-event-journal](0016-clickhouse-event-journal.md)
- [0064-release-compatibility-lts](0064-release-compatibility-lts.md) — expand/contract + platform LTS
- [blueprints](../blueprints.md) — `data-source-v1`
