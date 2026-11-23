# Эпик: сертификация MySQL (ADR-0079)

> **Язык:** русская версия. Канон: [en/mysql-certification-epic.md](../en/mysql-certification-epic.md).  
> **Статус:** B1 **Prod Yes** только на MySQL Community Server 8.4.x + MariaDB Connector/J (owner extension 2026-11-22, [checklist](../en/mysql-sku-promotion-checklist.md)); opt-in single-engine **B2 Prod Yes** на той же ячейке (owner extension 2026-11-23, [B2 checklist](../en/mysql-b2-sku-promotion-checklist.md)). Active-active не заявлен (boot отклоняет cluster). Остальные ячейки MySQL — **P-MYSQL-SKU-RERUN**.  
> **Политика:** lab после MSSQL B1 Prod Yes. MariaDB ≠ MySQL. MySQL B1 Prod Yes подписан — B1 pack Oracle может начинаться; Oracle позже подписал свой Prod Yes (B1) только на Oracle Database Free.

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

## B2 single-engine (opt-in) — Prod Yes подписан 2026-11-23

Конфигурация, historian и journal в одной базе MySQL, как MSSQL B2 ([0079](decisions/0079-mysql-metadata-certification.md) § Amendment 5).

- `ISPF_MYSQL_SINGLE_ENGINE` / `ispf.relational.mysql.single-engine` (по умолчанию выключен) и `max-retention-days` (по умолчанию 90) в `MysqlRelationalProperties`; deploy profile `mysql-single-engine`
- Fences: с флагом JDBC history и journal разрешены по правилам MSSQL single-engine (общая проверка); warm tier, cold archive, ClickHouse spool, dual-write — refuse; cluster, row isolation и `HARD` — по-прежнему refuse; флаг на другом движке — refuse
- Нативный bucket aggregate (`TIMESTAMPDIFF` / `TIMESTAMPADD`, выравнивание по epoch, без зависимости от часовых поясов); `/history/aggregate` считает в SQL, а не в JVM
- Purge journal пачками `DELETE … LIMIT 5000`, по транзакции на пачку (цикл общий с MSSQL `DELETE TOP`)
- Потолок retention `max-retention-days` под флагом (общий с MSSQL)
- Preflight: сессия пула `READ-COMMITTED` и база на `utf8mb4_0900_bin`, только single-engine
- Тесты: boot guard, customizer до Flyway, потолок retention, preflight, SQL диалекта, путь запроса, purge пачками; Docker TCK `MysqlHistoryBucketTckTest`
- Lab gate `tools/mysql-b1-lab/gate-b2.ps1`: 25 PASS / 0 FAIL на 8.4.13 Community (`gate-evidence-b2-community.json`); решение владельца — `gate-evidence-b2-prod-yes-community.json`; профиль [examples/mysql-single-engine](../../examples/mysql-single-engine/README.md)

## Tech debt после подписи

- **P-MYSQL-TLS** — повтор gate по TLS (`sslMode=verify-full`), рекомендуется на первом named хосте
- **P-MYSQL-PG-SQL** — проверить остальной JDBC SQL на PG-only конструкции и портировать или закрыть fence то, что найдёт проверка; пути, найденные при подписи, исправлены ([checklist](../en/mysql-sku-promotion-checklist.md#known-gaps-tracked-tech-debt))
- **P-MYSQL-SKU-RERUN** — повтор checklist B1 и B2 на каждой named Enterprise / managed / non-Community ячейке

## Не цели

- MariaDB как сертифицированный MySQL
- Active-active на MySQL (boot отклоняет `ISPF_CLUSTER_ENABLED=true` с single-engine и без)
- Rollups, партиции и partition-drop retention historian на MySQL; файловый буфер перед MySQL
- Живая миграция данных PG→MySQL
