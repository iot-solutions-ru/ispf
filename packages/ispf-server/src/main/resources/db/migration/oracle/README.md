# Oracle migration pack (ADR-0037 / ADR-0080)

**B1 Lab green on Oracle Database Free. Not certified. Prod: No.**

`V1__baseline.sql` is the generated greenfield baseline (`python tools/oracle-baseline-generate.py`, from postgresql
`V1` + `V71`…`V101`). Its header lists the type map and every Oracle-specific decision (`''` is `NULL`, quoted reserved
words, the skipped duplicate index, skipped no-op data rewrites). Empty schema only: Oracle DDL auto-commits, so after a
failed V1 drop and recreate the schema user before retrying.

An applied V1 is frozen. A new postgresql `Vnn` needs an `oracle/Vnn` twin or a line in
[`docs/en/oracle-flyway-exceptions.txt`](../../../../../../../../docs/en/oracle-flyway-exceptions.txt);
`tools/oracle-flyway-lockstep-check.py` enforces that in CI.

Boot admits `ISPF_METADATA_DB_KIND=oracle` only when this file is on the classpath and the operator put the Oracle JDBC
thin driver on the runtime classpath. ISPF does not ship that driver
([checklist § Adding the Oracle JDBC driver](../../../../../../../../docs/en/oracle-sku-promotion-checklist.md#adding-the-oracle-jdbc-driver)).
`assertOracleB1Fences` / `OracleCertificationBootGuard` then refuse row isolation, `HARD`, dual-write, cluster, and JDBC
history/journal (no single-engine bypass).

External JDBC `DATA_SOURCE` for reports remains supported.

Charter: [0080-oracle-metadata-certification](../../../../../../../../docs/en/decisions/0080-oracle-metadata-certification.md).  
Epic: [oracle-certification-epic](../../../../../../../../docs/en/oracle-certification-epic.md).
