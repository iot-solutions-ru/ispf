# Oracle B2 single-engine promotion checklist

> **Language:** Canonical English.  
> **Status:** Operator checklist for opt-in **Prod Yes (B2)** on Oracle Database Free 23.26.x + operator-supplied `ojdbc17` with `ISPF_ORACLE_SINGLE_ENGINE=true`. Active-active is a separate claim ([A-A checklist](oracle-aa-sku-promotion-checklist.md)).

## Named cell

| Field | Value |
|-------|--------|
| Engine | Oracle Database Free 23.26.x + `ojdbc17` on `loader.path` (same cell as [B1](oracle-sku-promotion-checklist.md)) |
| Flags | `ISPF_ORACLE_SINGLE_ENGINE=true`, `ISPF_VARIABLE_HISTORY_STORE=jdbc`, `ISPF_EVENT_JOURNAL_STORE=jdbc` (journal on at boot), `SPRING_JPA_HIBERNATE_DDL_AUTO=none` |
| Retention | Application `DELETE`, clamped to `ISPF_ORACLE_MAX_RETENTION_DAYS` (default 90). Respect Free's 12 GB user-data cap |
| Signer / date | owner extension / 2026-11-23 |

## What B2 means

- Configuration, JDBC historian and JDBC journal on the **same** Oracle `ISPF_DB`.
- Native bucket aggregate SQL (`OracleDialect.variableSampleBucketAggregateSql`).
- Batched journal purge via `ROWID` + `FETCH FIRST`.
- Warm tier, cold archive, ClickHouse spool, dual-write, row isolation, `HARD` remain refused. Cluster is allowed only with this flag (A-A).

## Non-claims

- Active-active without the dual-JVM A-A gate / soak (**P-ORACLE-AA-SOAK**)
- SE2 / EE / Autonomous / RDS without **P-ORACLE-SKU**
- `ddl-auto=validate` (**P-ORACLE-VALIDATE**)
- Year-scale archive; rollups; partitions
- Shipping `ojdbc`

Related: [0080](decisions/0080-oracle-metadata-certification.md), [oracle-certification-epic](oracle-certification-epic.md).
