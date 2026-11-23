# MySQL single-engine profile

Opt-in profile for metadata + JDBC journal + JDBC history on one MySQL database.

**Prod Yes (B2)** on MySQL Community Server 8.4.x with MariaDB Connector/J ([B2 checklist](../../docs/en/mysql-b2-sku-promotion-checklist.md)): capped JDBC history and journal, retention `DELETE` with a 90-day ceiling. This is **not** active-active (boot refuses `ISPF_CLUSTER_ENABLED=true` on MySQL), not historian rollups or partitions, and not a multi-year raw archive. See [0079](../../docs/en/decisions/0079-mysql-metadata-certification.md) § Amendment 5.

```text
# source examples/mysql-single-engine/single-engine.env
```

Helm chart defaults stay on PostgreSQL (`deploy/helm/ispf/values.yaml`). To point a deployment at this profile, set the same environment variables on the server process, plus the datasource:

```text
SPRING_DATASOURCE_URL=jdbc:mysql://<host>:3306/ispf?permitMysqlScheme=true&sslMode=verify-full
SPRING_DATASOURCE_DRIVER_CLASS_NAME=org.mariadb.jdbc.Driver
```

Set `ISPF_MYSQL_SINGLE_ENGINE=true` explicitly. `ISPF_HISTORIAN_DEPLOY_PROFILE=mysql-single-engine` only fills keys that no property source defines, and the shipped `application.yml` defines this flag, so the profile alone leaves ISPF in B1.

Before boot the DBA must provide:

- a dedicated MySQL Community Server 8.4.x instance (no co-tenant OLTP) and a dedicated database;
- `transaction_isolation=READ-COMMITTED` on the server;
- the ISPF database on `utf8mb4` / `utf8mb4_0900_bin`.

With the flag on, boot checks the last two and refuses to start if either is missing. Boot also refuses a history store other than `jdbc`, an enabled journal on another store, warm tier, cold archive, the ClickHouse spool, dual-write, row isolation, `HARD` and cluster.

**Retention:** every variable's retention is clamped to `ISPF_MYSQL_MAX_RETENTION_DAYS` (default 90); an unlimited retention becomes that ceiling. The journal purges in `DELETE … LIMIT 5000` batches, one transaction per batch.

**Journal:** enable it at boot (`ISPF_EVENT_JOURNAL_ENABLED=true`). Runtime settings refuse switching the JDBC journal on later on MySQL.

TLS on the `ISPF_DB` connection is the operator's responsibility and is not certified (**P-MYSQL-TLS**).
