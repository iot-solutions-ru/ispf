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
db/migration/postgresql/   — V1__baseline.sql (greenfield; incremental V1–V70 squashed)
db/migration/h2/           — edge overrides (if needed; default H2 uses postgresql pack in MODE=PostgreSQL)
db/migration/mssql/        — enterprise POC: one greenfield baseline
db/migration/mysql/
db/migration/oracle/
```

`FlywayConfigurationCustomizer` selects `locations` by active dialect.

#### Greenfield policy (until v1.0)

**We do not support** moving the relational core between engines (PG → MSSQL, etc.) and **do not support** Flyway upgrade from legacy incremental migrations (V1–V70 removed).

| Scenario | Action |
|----------|--------|
| New installation | Empty DB + `V1__baseline.sql` of active dialect |
| Change `ISPF_METADATA_DB_KIND` | New empty DB/schema from scratch; configuration via **bundles** (`package import`) |
| ISPF version upgrade on same PostgreSQL | Same `V1__baseline.sql`; schema already applied — Flyway no-op. DDL changes — via new baseline revisions or manual maintenance |
| Legacy DB with V1–V70 history | One-time `deploy/vps-flyway-repair.sh` — squash history to V1 + repair checksum |

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
| 0 | PostgreSQL 16 + Timescale | Yes | `postgresql/V1__baseline.sql` | Primary; software registry |
| 1 | PG-compatible (Yugabyte, Cockroach) | On request | `postgresql/` | Test per vendor |
| 2 | H2 (edge/dev) | Edge only | `postgresql/` + PG compat mode | `application-local.yml` |
| 3 | MS SQL Server | POC | `mssql/` | First enterprise target |
| 4 | MySQL, Oracle | Planned | `mysql/`, `oracle/` | After MSSQL POC |

## Consequences

- single abstraction; explicit single-DB scenario; external JDBC for integrations without mixing with metadata.


Risks:

- enterprise engines certified one at a time; cluster requires the same engine on all replicas; DBMS change — greenfield + bundles, not live migration.

**Code:** `com.ispf.server.relational.*`, `DataSourceSqlSession`, `ExternalDataSourceRegistry`, `data-source-v1` extension, UI DataSourceEditor + Database settings.

## Related

- [STORAGE_PORTABILITY_INVENTORY.md](../storage-portability-inventory.md)
- [DEPLOYMENT.md](../deployment.md) § Storage deployment modes
- [ADR-0028](0028-horizontal-active-active-cluster.md), [ADR-0016](0016-clickhouse-event-journal.md)
- [BLUEPRINTS.md](../blueprints.md) — `data-source-v1`
