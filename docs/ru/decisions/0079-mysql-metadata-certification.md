# ADR-0079: Сертификация метаданных на MySQL (дополнение к 0037)

## Статус

**Accepted** (2026-11-20) — политика и charter. **Amended 2026-11-22 — B1 pack + Lab green:** lab Flyway SQL авторизован, драйвер выбран, Lab-green gate пройден на MySQL 8.4.13 Community. **Prod Yes (B1)** 2026-11-22 — owner extension (§ Amendment 4) на одной ячейке: **MySQL Community Server 8.4.x + MariaDB Connector/J** (только этот клиент) ([checklist](../../en/mysql-sku-promotion-checklist.md)). **Amended 2026-11-23 — B2 single-engine:** opt-in `ISPF_MYSQL_SINGLE_ENGINE=true` кладёт JDBC history и journal в ту же базу MySQL (§ Amendment 5); **Prod Yes (B2)** 2026-11-23 — owner extension на той же ячейке ([B2 checklist](../../en/mysql-b2-sku-promotion-checklist.md)). **Amended 2026-11-23 — A-A:** с флагом single-engine `ispf.cluster.enabled=true` разрешён (§ Amendment 6); **Prod Yes (A-A)** — owner extension на той же ячейке ([A-A checklist](../../en/mysql-aa-sku-promotion-checklist.md)); NATS по-прежнему обязателен; tech debt по soak — **P-MYSQL-AA-SOAK**. MySQL `ISPF_DB` стартует только под своими fences (`assertMysqlB1Fences`): по умолчанию B1, с флагом — B2/A-A (cluster только с флагом). Остальные ячейки MySQL — не Prod Yes (**P-MYSQL-SKU-RERUN**); MariaDB не покрывается никогда. TLS и SQL вне путей gate не сертифицированы (**P-MYSQL-TLS**, **P-MYSQL-PG-SQL**).

Канонический текст: [EN ADR-0079](../../en/decisions/0079-mysql-metadata-certification.md).

## Контекст

`MysqlDialect` и внешний MySQL через `DATA_SOURCE` не означают сертификацию `ISPF_DB`. MariaDB ≠ MySQL (config/product fail-closed).

## Решение

- Scope — **B1 metadata**; lab-ячейка — **MySQL 8.4 LTS Community** (8.0 не подписывается).
- Journal/history вне B1; B2 (§ Amendment 5) покрывает их только под opt-in флагом single-engine, без флага fences по-прежнему refuse JDBC history и journal.
- `assertEngineCertified` больше не отказывает `mysql`: как MSSQL, MySQL стартует только под B1 fences (до Flyway и на context refresh). У Oracle свой engine gate ([0080](0080-oracle-metadata-certification.md)).
- Testcontainers и dev-lab — только lab-сигнал, не production-сертификация. § Amendment 4 — явное исключение владельца для одной ячейки.
- MySQL B1 Prod Yes (§ Amendment 4) разблокировал B1 pack Oracle ([0080](0080-oracle-metadata-certification.md)); Oracle подписал свой Prod Yes (B1) только на Oracle Database Free (0080, Amendment 4) и эту ячейку не наследует. Oracle Phase 1 (честность dialect и fences; boot refuse) начат раньше решением владельца.

### Amendment 1 — lab Flyway SQL

- `db/migration/mysql/V1__baseline.sql` генерируется `tools/mysql-baseline-generate.py` из postgresql `V1` + `V71`…`V101` (пропущены V86 RLS, V89 `random_uuid`, data-rewrite через `regexp_replace`).
- Типы: `UUID` → `CHAR(36)`, `BOOLEAN` (хранится как `TINYINT(1)`), `TIMESTAMP` → `DATETIME(6)`, `TEXT` → `LONGTEXT`, `BYTEA` → `LONGBLOB`, identity → `AUTO_INCREMENT`; все таблицы InnoDB + `utf8mb4_0900_bin` (регистрозависимые пути).
- Inline `REFERENCES` (MySQL 8.4 их игнорирует) → table-level `FOREIGN KEY`; два non-unique индекса > 3072 байт получают prefix; unique-ключ сверх лимита валит генератор.
- Lockstep: `tools/mysql-flyway-lockstep-check.py` + `docs/en/mysql-flyway-exceptions.txt` в CI. Применённый V1 заморожен.
- DDL в MySQL не транзакционный: при сбое V1 — удалить БД и повторить.

### Amendment 2 — драйвер (client ≠ server)

- Клиент `ISPF_DB` для серверов MySQL 8.4 — **MariaDB Connector/J** (LGPL-2.1-or-later), `runtimeOnly`, неизменённый JAR. URL `jdbc:mysql://…/ispf?permitMysqlScheme=true` + `driver-class-name=org.mariadb.jdbc.Driver`.
- `flyway-mysql` (`runtimeOnly`), `testcontainers-mysql:2.0.5` (`testImplementation`). GPL `mysql-connector-j` — **нет**.
- **Сервер MariaDB** по-прежнему refuse, в том числе при явном `ISPF_METADATA_DB_KIND=mysql`.
- Hibernate: закреплён `MySQLDialect` (иначе драйвер MariaDB даёт `MariaDBDialect` даже на MySQL) — подкласс `MysqlHibernateDialect`, с которым проходит `ddl-auto=validate`.
- Блокировки аудита: `INSERT … ON DUPLICATE KEY UPDATE` (portable вариант давал deadlock 1213).

### Amendment 3 — Lab-green gate

Flyway TCK + concurrent audit + lease failover, после исправления P-MYSQL-PG-SQL также очередь задач и триггеры SQL-binding (`MysqlFlywayBaselineTckTest`), live boot + import (`MysqlLiveBundleImportTckTest`), lab-стенд `tools/mysql-b1-lab` (health, login, Flyway, dialect, import, второй JVM, аудит) и boot refuse для row isolation, `HARD`, JDBC history, dual-write, JDBC journal, cluster и сервера MariaDB. Evidence: `tools/mysql-b1-lab/gate-evidence-lab-community.json` — 2026-11-22, 17 PASS / 0 FAIL.

Закрыто после Lab green (2026-11-22): Hibernate `ddl-auto=validate`. MariaDB Connector/J при `lower_case_table_names=0` не сообщает регистр хранения идентификаторов, и Hibernate искал таблицы в верхнем регистре; затем `report_templates.content` (маппинг `BINARY`, колонка `LONGBLOB`) не проходил проверку типа. `MysqlHibernateDialect` ищет имена как написаны, если драйвер регистр не сообщает (сообщённый регистр по-прежнему главнее), и принимает `LONGBLOB` для `BINARY`. `MysqlLiveBundleImportTckTest` стартует с validate на MySQL 8.4.13 при `lower_case_table_names=0`. Свойство оператору не нужно; явный `hibernate.dialect` заменяет закреплённый диалект и возвращает сбой.

### Amendment 4 (2026-11-22) — Prod Yes (B1), owner extension

- Ячейка: self-managed **MySQL Community Server 8.4.x** LTS, единственный клиент `ISPF_DB` — **MariaDB Connector/J**.
- Основание: Lab-green ячейка Amendment 3 (8.4.13 Community, локальный Docker), повтор 2026-11-22 с `ddl-auto=validate` на JVM стенда и втором JVM, плюс platform metrics и очередь задач — 20 PASS / 0 FAIL (`tools/mysql-b1-lab/gate-evidence-validate-community.json`, вывод collector). Решение владельца — `tools/mysql-b1-lab/gate-evidence-prod-yes-community.json`.
- Owner extension: lab Docker-ячейка заменяет named self-managed хост (исключение из правила checklist «не lab Docker-стенд» только для этой ячейки). Повтор gate на первом named хосте рекомендуется, но не блокирует.
- Fences без изменений: boot отказывает JDBC history / journal, dual-write, cluster, row isolation, `HARD` и серверу MariaDB.
- Tech debt **P-MYSQL-SKU-RERUN**: MySQL Enterprise, HeatWave, Amazon RDS / Aurora MySQL, Azure Database for MySQL и любая другая non-Community ячейка — свой прогон checklist до любого claim.
- TLS: обязанность оператора, этой подписью **не сертифицирован**. Ячейка evidence работала без TLS, с `allowPublicKeyRetrieval=true` — только для lab. Tech debt **P-MYSQL-TLS**.
- Покрытие SQL: пути gate и MySQL TCK; полного паритета SQL с PostgreSQL **нет**. PG-only SQL, найденный при подписи, исправлен через `RelationalDialect` и работает на MySQL 8.4: `PlatformJobService` читает `job_id` строкой вместо приведения `getObject` на `CHAR(36)` к `UUID` и сортирует выполняющиеся задачи через `orderByNullsLast` вместо `NULLS LAST` (отчёты через очередь задач, `GET /api/v1/platform/metrics`); `ApplicationSqlBindingStore` собирает список триггеров по успеху функции через `concatExpression` (`CONCAT`) вместо `||`, который MySQL читает как OR. Остальной SQL полностью не проверялся. Tech debt **P-MYSQL-PG-SQL** ([checklist](../../en/mysql-sku-promotion-checklist.md#known-gaps-tracked-tech-debt)).
- Non-claims: MySQL 8.0 и 9.x; сервер MariaDB; другие JDBC-клиенты (GPL `mysql-connector-j` в classpath не бывает); B2 history / journal (подписан позже, § Amendment 5); active-active; tenant row isolation; `HARD` и per-app схемы; TLS на соединении `ISPF_DB` (**P-MYSQL-TLS**); полный паритет SQL с PostgreSQL (**P-MYSQL-PG-SQL**).

### Amendment 5 (2026-11-23) — B2 single-engine (opt-in) и Prod Yes (B2), owner extension

B2 повторяет MSSQL B2 ([0078](0078-mssql-metadata-certification.md) § 1): B1 плюс `ISPF_VARIABLE_HISTORY_STORE=jdbc` и `ISPF_EVENT_JOURNAL_STORE=jdbc` в **той же** базе MySQL — конфигурация, historian и journal в одной БД. Режим opt-in; по умолчанию остаётся B1.

- Флаг: `ISPF_MYSQL_SINGLE_ENGINE=true` (`ispf.relational.mysql.single-engine`, по умолчанию `false`). Задавать явно: deploy profile `mysql-single-engine` заполняет только ключи, которых нет ни в одном источнике свойств, а `application.yml` этот ключ задаёт ([B2 checklist](../../en/mysql-b2-sku-promotion-checklist.md#deploy-profile)).
- Правила store (до Flyway и на context refresh): с флагом history store — ровно `jdbc` (ClickHouse, Cassandra/Scylla, `jpa` и опечатки — refuse), включённый journal — только JDBC; warm tier, cold Parquet archive и ClickHouse spool — refuse. Это правила MSSQL single-engine, одна общая проверка.
- С флагом по-прежнему refuse (до § Amendment 6): dual-write, row isolation, `HARD`, сервер MariaDB. Cluster был refuse до § Amendment 6. Флаг на любом движке, кроме MySQL, — refuse: он режет retention везде, где задан.
- Preflight: с флагом boot отказывает, если сессия пула не `READ-COMMITTED` или collation базы по умолчанию не `utf8mb4_0900_bin` (`MysqlDatabasePreflightRunner`).
- Bucket aggregate: нативный `MysqlDialect.variableSampleBucketAggregateSql` — `TIMESTAMPDIFF` / `TIMESTAMPADD` по `DATETIME(6)` `observed_at` (UTC wall clock, как записано), выравнивание по epoch, как в PostgreSQL и JVM-пути, без зависимости от `time_zone` сессии и зоны JVM. `/history/aggregate` считает в SQL, а не в JVM.
- Purge journal: `DELETE … LIMIT 5000`, по транзакции на пачку; цикл общий с MSSQL `DELETE TOP (5000)`.
- Retention: прикладной `DELETE`; retention каждой переменной ограничен `ispf.relational.mysql.max-retention-days` (по умолчанию **90**), ≤0 (без ограничения) превращается в этот потолок. Rollups, партиций и partition drop на MySQL нет.
- Схема: миграций нет, `variable_samples` и `event_history` уже есть в `mysql/V1`.
- Gate: `tools/mysql-b1-lab/gate-evidence-b2-community.json` — 25 PASS / 0 FAIL на MySQL 8.4.13 Community, 2026-11-23 (живая запись historian, нативный bucket aggregate, raw read, потолок retention, живой journal и keyset, purge 20 000 строк, 6 boot refuse, 2 preflight refuse). CI (opt-in Docker): `MysqlHistoryBucketTckTest`.
- Prod Yes (B2): owner extension 2026-11-23 только на ячейке § Amendment 4, с флагом, выделенной базой и выделенным инстансом сервера (без co-tenant OLTP). Решение владельца — `tools/mysql-b1-lab/gate-evidence-b2-prod-yes-community.json`.
- Non-claims (только B2): rollups, партиции и partition-drop retention; файловый буфер перед MySQL; многолетний raw-архив; latency raw-сканов без named fixture; non-Community ячейки (**P-MYSQL-SKU-RERUN**); MariaDB; TLS (**P-MYSQL-TLS**); полный паритет SQL (**P-MYSQL-PG-SQL**). Active-active — § Amendment 6.

### Amendment 6 (2026-11-23) — A-A (active-active) на ячейке single-engine

A-A повторяет MSSQL A-A ([0078](0078-mssql-metadata-certification.md)): **N JVM, один writable primary**, не multi-master. Cluster разрешён только вместе с `ISPF_MYSQL_SINGLE_ENGINE=true` (dual-JVM gate шёл с history и journal в этой базе).

- Флаги: `ISPF_CLUSTER_ENABLED=true` **и** `ISPF_MYSQL_SINGLE_ENGINE=true`. B1 с cluster по-прежнему refuse на boot.
- Зависимости: `ClusterDependencyValidator` по-прежнему требует NATS JetStream (и Redis, если включены live-variable sync / path interest). Claim «без NATS» нет.
- Топология: один writable primary MySQL; все реплики используют один `ISPF_METADATA_DB_KIND` и `ISPF_DB_URL`.
- Gate: `tools/mysql-b1-lab/gate-aa.ps1` + `stand-aa-local.ps1`; CI opt-in `MysqlClusterLeaseTckTest`.
- Prod Yes (A-A): owner extension на ячейке Community § Amendment 4 / 5; решение — `tools/mysql-b1-lab/gate-evidence-aa-prod-yes-community.json` (измерения — `gate-evidence-aa-community.json`, 35/0). Остаток — dual-JVM chaos soak / failover под нагрузкой — tech debt **P-MYSQL-AA-SOAK** ([checklist](../../en/mysql-aa-sku-promotion-checklist.md)).
- Non-claims: multi-master Group Replication; readable secondaries как цели записи; cluster без NATS; завершённый многочасовой soak (**P-MYSQL-AA-SOAK**); non-Community ячейки.

## Последствия

MySQL — **Prod Yes (B1)** на одной ячейке: self-managed MySQL Community Server 8.4.x с MariaDB Connector/J, под B1 fences. На той же ячейке opt-in single-engine — **Prod Yes (B2)**: JDBC history и journal в той же базе, retention `DELETE` с потолком 90 дней по умолчанию. С тем же флагом **Prod Yes (A-A)** разрешает `ispf.cluster.enabled` (NATS по-прежнему обязателен; soak — **P-MYSQL-AA-SOAK**). Остальные ячейки MySQL — lab до своего прогона checklist (**P-MYSQL-SKU-RERUN**). TLS — обязанность оператора, не сертифицирован (**P-MYSQL-TLS**); SQL вне путей gate не заявлен (**P-MYSQL-PG-SQL**). Смена движка — новая БД и импорт bundle. В продажах — «MySQL Community Server 8.4 B1 metadata», «B2 single-engine» и «A-A», где применимо, с клиентом MariaDB Connector/J; не «MySQL/MariaDB» и не Enterprise / managed MySQL без повторного прогона.

## Связанные

- [0037](0037-relational-core-portability.md), [0078](0078-mssql-metadata-certification.md), [0080](0080-oracle-metadata-certification.md)
- [mysql-certification-epic](../mysql-certification-epic.md)
- [mysql-aa-sku-promotion-checklist](../../en/mysql-aa-sku-promotion-checklist.md)
- [mysql-sku-promotion-checklist](../../en/mysql-sku-promotion-checklist.md)
- [mysql-b2-sku-promotion-checklist](../../en/mysql-b2-sku-promotion-checklist.md)
- [storage-portability-inventory](../storage-portability-inventory.md)
