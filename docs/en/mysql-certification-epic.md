# Epic: MySQL relational certification (ADR-0079)

> **Language:** Canonical English. Russian: [ru/mysql-certification-epic.md](../ru/mysql-certification-epic.md) (if present).  
> **Status:** Charter — delivery board for [ADR-0079](decisions/0079-mysql-metadata-certification.md).  
> **Policy:** Starts **after** MSSQL named-SKU gate ([0078](decisions/0078-mssql-metadata-certification.md)). An MSSQL B1 **policy waiver** is not that gate. MariaDB is **not** this epic.

## Already landed (this train)

- [x] Boot refuse `ISPF_METADATA_DB_KIND=mysql` (`UncertifiedRelationalEngineBootGuard`)
- [x] Schema-switch fail-closed (`MysqlDialect.supportsSessionSchemaSwitch=false`)
- [x] README: pack not certified

## Next train (B1)

- [ ] Named SKU (MySQL 8.0 / 8.4) + RCSI-equivalent honesty
- [ ] `db/migration/mysql/V1__baseline.sql` + generator/lockstep
- [ ] Testcontainers TCK: Flyway → boot → cert bundle → lease/audit
- [ ] Boot fences for jdbc history/journal/cluster (mirror MSSQL B1)
- [ ] Lift prod ban only after § gate on named SKU

## Non-goals

- MariaDB as certified MySQL
- B2 jdbc historian on MySQL in the first quote
- Live PG→MySQL data migration
