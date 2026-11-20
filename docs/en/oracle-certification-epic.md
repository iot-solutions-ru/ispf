# Epic: Oracle relational certification (ADR-0080)

> **Language:** Canonical English.  
> **Status:** Charter — delivery board for [ADR-0080](decisions/0080-oracle-metadata-certification.md).  
> **Policy:** Starts **after** MySQL B1 gate ([0079](decisions/0079-mysql-metadata-certification.md)).

## Already landed (this train)

- [x] Boot refuse `ISPF_METADATA_DB_KIND=oracle` (`UncertifiedRelationalEngineBootGuard`)
- [x] Schema-switch fail-closed (`OracleDialect.supportsSessionSchemaSwitch=false`)
- [x] README: pack not certified

## Next train (B1)

- [ ] Named SKU + identifier/NULL/boolean honesty
- [ ] `db/migration/oracle/V1__baseline.sql` + lockstep
- [ ] Testcontainers TCK matrix (mirror MSSQL/MySQL B1)
- [ ] Lift prod ban only after named-SKU gate

## Non-goals

- Bundling Oracle B1 into the MySQL first quote
- Claiming A-A without a dedicated soak
