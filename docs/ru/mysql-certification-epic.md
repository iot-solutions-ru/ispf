# Эпик: сертификация MySQL (ADR-0079)

> **Язык:** русская версия. Канон: [en/mysql-certification-epic.md](../en/mysql-certification-epic.md).  
> **Статус:** B1 **Prod Yes** только на MySQL Community Server 8.4.x + MariaDB Connector/J (owner extension 2026-11-22, [checklist](../en/mysql-sku-promotion-checklist.md)). Остальные ячейки MySQL — **P-MYSQL-SKU-RERUN**.  
> **Политика:** lab после MSSQL B1 Prod Yes. MariaDB ≠ MySQL. MySQL B1 Prod Yes подписан — B1 pack Oracle может начинаться; Oracle остаётся Prod: No.

## Phase 1 — честность диалекта (есть)

- Schema-switch fail-closed
- MariaDB не алиасится в MySQL
- Dialect honesty (1062, DATE_ADD, fences без single-engine bypass)

## B1 pack + Lab green (есть, 2026-11-22)

- Клиент — MariaDB Connector/J (LGPL); GPL `mysql-connector-j` нет; сервер MariaDB по-прежнему refuse, в том числе при `ISPF_METADATA_DB_KIND=mysql`
- `tools/mysql-baseline-generate.py` + `mysql/V1__baseline.sql`; lockstep + CI
- TCK (`mysql-docker`): Flyway, аудит (8 писателей), lease failover, live boot + import
- Исправления boot-пути: закреплён `MySQLDialect`, проверка каталога через dialect quoting, lock row через `ON DUPLICATE KEY UPDATE`
- Hard refuse снят только для MySQL; boot под B1 fences; Oracle — refuse
- Стенд `tools/mysql-b1-lab`: 17 PASS / 0 FAIL на MySQL 8.4.13 Community ([checklist](../en/mysql-sku-promotion-checklist.md))

## Подпись Prod Yes (B1) — подписано 2026-11-22

- Hibernate `ddl-auto=validate` на MySQL — **сделано**: закреплён `MysqlHibernateDialect` (регистр идентификаторов, когда MariaDB Connector/J его не сообщает; `BINARY` против `LONGBLOB`); TCK стартует с validate при `lower_case_table_names=0`
- TLS на соединении `ISPF_DB` — зафиксирован как обязанность оператора, этой подписью **не сертифицирован**; `allowPublicKeyRetrieval` — настройка только lab-стенда (**P-MYSQL-TLS**)
- PG-only SQL, найденный при подписи (platform jobs, триггеры SQL-binding) — **исправлен** через `RelationalDialect`, проверен на MySQL в `MysqlFlywayBaselineTckTest`, `MysqlLiveBundleImportTckTest` и пунктах gate `g9_metrics` / `g9_job_queue`; полного паритета SQL нет (**P-MYSQL-PG-SQL**)
- Owner extension из Lab-green ячейки вместо named self-managed хоста — **сделано**: повтор gate с validate на JVM стенда и втором JVM, 20 PASS / 0 FAIL (`gate-evidence-validate-community.json`); решение владельца — `gate-evidence-prod-yes-community.json`; повтор на named хосте рекомендуется, не блокирует; **P-MYSQL-SKU-RERUN** для Enterprise / managed / non-Community

## Tech debt после подписи

- **P-MYSQL-TLS** — повтор gate по TLS (`sslMode=verify-full`), рекомендуется на первом named хосте
- **P-MYSQL-PG-SQL** — проверить остальной JDBC SQL на PG-only конструкции и портировать или закрыть fence то, что найдёт проверка; пути, найденные при подписи, исправлены ([checklist](../en/mysql-sku-promotion-checklist.md#known-gaps-tracked-tech-debt))
- **P-MYSQL-SKU-RERUN** — повтор checklist на каждой named Enterprise / managed / non-Community ячейке
