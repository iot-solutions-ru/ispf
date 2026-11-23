# Oracle migration pack (ADR-0037 / ADR-0080)

| | |
|---|---|
| Production claim | **Prod Yes (B1)** on self-managed Oracle Database Free 23.26.x + operator-supplied `ojdbc17` only — owner override from the 23.26.4.0.0 Lab-green cell ([checklist](../../../../../../../../docs/en/oracle-sku-promotion-checklist.md)). Other editions, 19c, managed services: **P-ORACLE-SKU**, **P-ORACLE-19C** |
| Hibernate | Set `SPRING_JPA_HIBERNATE_DDL_AUTO=none`. The default profile's `validate` has not run on Oracle and is not claimed (**P-ORACLE-VALIDATE**) |

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

TLS (Oracle Net encryption, or TLS with a wallet) is the operator's responsibility: Prod Yes (B1) did not run a gate over
it (**P-ORACLE-TLS**). `''` writes on paths the gate does not exercise and PostgreSQL-only SQL outside the gate's paths are
not claimed (**P-ORACLE-EMPTY**, **P-ORACLE-PG-SQL**).

External JDBC `DATA_SOURCE` for reports remains supported.

Charter: [0080-oracle-metadata-certification](../../../../../../../../docs/en/decisions/0080-oracle-metadata-certification.md).  
Epic: [oracle-certification-epic](../../../../../../../../docs/en/oracle-certification-epic.md).  
Lab: [tools/oracle-b1-lab](../../../../../../../../tools/oracle-b1-lab/README.md).
