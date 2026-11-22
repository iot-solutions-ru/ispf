# MySQL migration pack (ADR-0037 / ADR-0079)

| | |
|---|---|
| Production claim | **No — B1 Lab green** on MySQL 8.4.13 Community ([checklist](../../../../../../../../docs/en/mysql-sku-promotion-checklist.md)); no named-SKU Prod Yes |
| Baseline | `V1__baseline.sql`, generated: `python tools/mysql-baseline-generate.py` (greenfield only) |
| Lockstep | `python tools/mysql-flyway-lockstep-check.py` + `docs/en/mysql-flyway-exceptions.txt` (CI) |

`ISPF_METADATA_DB_KIND=mysql` boots only under the B1 fences (`MetadataDialectBootPolicy.assertMysqlB1Fences`,
pre-Flyway and on context refresh). Boot refuses:

- `ISPF_TENANT_DB_ROW_ISOLATION=true` and `ISPF_TENANT_ISOLATION_MODE=HARD`
- JDBC variable history, historian dual-write, and JDBC event journal on `ISPF_DB` (B2 is not decided)
- `ISPF_CLUSTER_ENABLED=true`
- A MariaDB server — config `mariadb`, product-name MariaDB, and `mysql` against a MariaDB server

Client: LGPL MariaDB Connector/J (`org.mariadb.jdbc.Driver`). Connector/J 3.x accepts `jdbc:mysql:` only with
`permitMysqlScheme=true`:

```text
SPRING_DATASOURCE_URL=jdbc:mysql://<host>:3306/ispf?permitMysqlScheme=true&sslMode=verify-full
SPRING_DATASOURCE_DRIVER_CLASS_NAME=org.mariadb.jdbc.Driver
ISPF_METADATA_DB_KIND=mysql
```

Database: `CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin`; server `transaction_isolation=READ-COMMITTED`.
Hibernate runs `MySQLDialect` (pinned in `RelationalConfiguration`; the HHH90000025 "does not need to be specified"
warning is expected). `ddl-auto=validate` does not pass on MySQL yet — outside the `local` profile set
`SPRING_JPA_HIBERNATE_DDL_AUTO=none`.

MySQL DDL is not transactional: if V1 fails, drop the database and retry. An applied V1 is frozen — new PG
`Vnn` scripts need a `mysql/Vnn` twin or an exception line. Per-app schemas and in-DB PG-flavored solution SQL are
unsupported; external `DATA_SOURCE` JDBC to MySQL for reports remains supported. GPL `mysql-connector-j` is never
on the classpath.

Charter: [0079-mysql-metadata-certification](../../../../../../../../docs/en/decisions/0079-mysql-metadata-certification.md).  
Epic: [mysql-certification-epic](../../../../../../../../docs/en/mysql-certification-epic.md).  
Lab: [tools/mysql-b1-lab](../../../../../../../../tools/mysql-b1-lab/README.md).
