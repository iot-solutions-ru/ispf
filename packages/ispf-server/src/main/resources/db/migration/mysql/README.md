# MySQL migration pack (ADR-0037 / ADR-0079)

| | |
|---|---|
| Production claim | **Prod Yes (B1)** on MySQL Community Server 8.4.x + MariaDB Connector/J only — owner extension from the 8.4.13 Lab-green cell ([checklist](../../../../../../../../docs/en/mysql-sku-promotion-checklist.md)); opt-in single-engine **Prod Yes (B2)** on the same cell ([B2 checklist](../../../../../../../../docs/en/mysql-b2-sku-promotion-checklist.md)). Other MySQL cells: **P-MYSQL-SKU-RERUN** |
| Baseline | `V1__baseline.sql`, generated: `python tools/mysql-baseline-generate.py` (greenfield only) |
| Lockstep | `python tools/mysql-flyway-lockstep-check.py` + `docs/en/mysql-flyway-exceptions.txt` (CI) |

`ISPF_METADATA_DB_KIND=mysql` boots only under its fences (`MetadataDialectBootPolicy.assertMysqlB1Fences`,
pre-Flyway and on context refresh). Boot refuses:

- `ISPF_TENANT_DB_ROW_ISOLATION=true` and `ISPF_TENANT_ISOLATION_MODE=HARD`
- Historian dual-write
- JDBC variable history and JDBC event journal on `ISPF_DB`, unless `ISPF_MYSQL_SINGLE_ENGINE=true` (B2, below)
- `ISPF_CLUSTER_ENABLED=true`, with or without single-engine
- A MariaDB server — config `mariadb`, product-name MariaDB, and `mysql` against a MariaDB server

**Single-engine (opt-in, Prod Yes B2):** `ISPF_MYSQL_SINGLE_ENGINE=true` allows JDBC history and journal on this database and
refuses warm tier, cold archive, the ClickHouse spool, and any history store other than `jdbc`. Boot also requires a
`READ-COMMITTED` pool session and the database on `utf8mb4_0900_bin`. Retention is application `DELETE`, clamped to
`ispf.relational.mysql.max-retention-days` (default 90). Buckets are native SQL (`MysqlDialect.variableSampleBucketAggregateSql`);
the journal purges in `DELETE … LIMIT` batches. B2 adds no migration: `V1` already carries `variable_samples` and `event_history`.
Profile: [examples/mysql-single-engine](../../../../../../../../examples/mysql-single-engine/README.md).

Client: LGPL MariaDB Connector/J (`org.mariadb.jdbc.Driver`). Connector/J 3.x accepts `jdbc:mysql:` only with
`permitMysqlScheme=true`:

```text
SPRING_DATASOURCE_URL=jdbc:mysql://<host>:3306/ispf?permitMysqlScheme=true&sslMode=verify-full
SPRING_DATASOURCE_DRIVER_CLASS_NAME=org.mariadb.jdbc.Driver
ISPF_METADATA_DB_KIND=mysql
```

TLS (`sslMode=verify-full` above) is the operator's responsibility: Prod Yes (B1) did not run a gate over TLS
(**P-MYSQL-TLS**). The lab stand's `allowPublicKeyRetrieval=true` is lab-only.

Database: `CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin`; server `transaction_isolation=READ-COMMITTED`.
Hibernate runs `MysqlHibernateDialect`, a `MySQLDialect` subclass pinned in `RelationalConfiguration`. The default
`ddl-auto=validate` passes, including on `lower_case_table_names=0`; no property is needed. Do not set
`hibernate.dialect`: plain `MySQLDialect` looks tables up in upper case there and validation fails.

MySQL DDL is not transactional: if V1 fails, drop the database and retry. An applied V1 is frozen — new PG
`Vnn` scripts need a `mysql/Vnn` twin or an exception line. Per-app schemas and in-DB PG-flavored solution SQL are
unsupported; external `DATA_SOURCE` JDBC to MySQL for reports remains supported. Queued report runs,
`GET /api/v1/platform/metrics` and function-success SQL-binding triggers go through `RelationalDialect` and run on
MySQL; the rest of the platform SQL has not been reviewed in full for PostgreSQL-only constructs (**P-MYSQL-PG-SQL**).
GPL `mysql-connector-j` is never on the classpath.

Charter: [0079-mysql-metadata-certification](../../../../../../../../docs/en/decisions/0079-mysql-metadata-certification.md).  
Epic: [mysql-certification-epic](../../../../../../../../docs/en/mysql-certification-epic.md).  
Lab: [tools/mysql-b1-lab](../../../../../../../../tools/mysql-b1-lab/README.md).
