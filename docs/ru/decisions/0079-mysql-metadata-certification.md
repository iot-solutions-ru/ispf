# ADR-0079: Сертификация метаданных на MySQL (дополнение к 0037)

## Статус

**Accepted** (2026-11-20) — политика и charter. **Amended 2026-11-22 — B1 pack + Lab green:** lab Flyway SQL авторизован, драйвер выбран, Lab-green gate пройден на MySQL 8.4.13 Community. **Prod Yes (B1)** 2026-11-22 — owner extension (§ Amendment 4) на одной ячейке: **MySQL Community Server 8.4.x + MariaDB Connector/J** (только этот клиент) ([checklist](../../en/mysql-sku-promotion-checklist.md)). MySQL `ISPF_DB` стартует только под B1 fences (`assertMysqlB1Fences`). Остальные ячейки MySQL — не Prod Yes (**P-MYSQL-SKU-RERUN**); MariaDB не покрывается никогда. TLS и SQL вне путей gate не сертифицированы (**P-MYSQL-TLS**, **P-MYSQL-PG-SQL**).

Канонический текст: [EN ADR-0079](../../en/decisions/0079-mysql-metadata-certification.md).

## Контекст

`MysqlDialect` и внешний MySQL через `DATA_SOURCE` не означают сертификацию `ISPF_DB`. MariaDB ≠ MySQL (config/product fail-closed).

## Решение

- Scope — **B1 metadata**; lab-ячейка — **MySQL 8.4 LTS Community** (8.0 не подписывается).
- Journal/history вне B1; **нет** single-engine bypass в fences.
- `assertEngineCertified` больше не отказывает `mysql`: как MSSQL, MySQL стартует только под B1 fences (до Flyway и на context refresh). Oracle — по-прежнему hard refuse.
- Testcontainers и dev-lab — только lab-сигнал, не production-сертификация. § Amendment 4 — явное исключение владельца для одной ячейки.
- MySQL B1 Prod Yes подписан (§ Amendment 4), поэтому B1 pack Oracle ([0080](0080-oracle-metadata-certification.md)) может начинаться; Oracle остаётся **Prod: No** до своего gate. Oracle Phase 1 (честность dialect и fences; boot refuse) начат раньше решением владельца.

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

Flyway TCK + concurrent audit + lease failover (`MysqlFlywayBaselineTckTest`), live boot + import (`MysqlLiveBundleImportTckTest`), lab-стенд `tools/mysql-b1-lab` (health, login, Flyway, dialect, import, второй JVM, аудит) и boot refuse для row isolation, `HARD`, JDBC history, dual-write, JDBC journal, cluster и сервера MariaDB. Evidence: `tools/mysql-b1-lab/gate-evidence-lab-community.json` — 2026-11-22, 17 PASS / 0 FAIL.

Закрыто после Lab green (2026-11-22): Hibernate `ddl-auto=validate`. MariaDB Connector/J при `lower_case_table_names=0` не сообщает регистр хранения идентификаторов, и Hibernate искал таблицы в верхнем регистре; затем `report_templates.content` (маппинг `BINARY`, колонка `LONGBLOB`) не проходил проверку типа. `MysqlHibernateDialect` ищет имена как написаны, если драйвер регистр не сообщает (сообщённый регистр по-прежнему главнее), и принимает `LONGBLOB` для `BINARY`. `MysqlLiveBundleImportTckTest` стартует с validate на MySQL 8.4.13 при `lower_case_table_names=0`. Свойство оператору не нужно; явный `hibernate.dialect` заменяет закреплённый диалект и возвращает сбой.

### Amendment 4 (2026-11-22) — Prod Yes (B1), owner extension

- Ячейка: self-managed **MySQL Community Server 8.4.x** LTS, единственный клиент `ISPF_DB` — **MariaDB Connector/J**.
- Основание: Lab-green ячейка Amendment 3 (8.4.13 Community, локальный Docker), повтор 2026-11-22 с `ddl-auto=validate` на каждом JVM — 17 PASS / 0 FAIL (`tools/mysql-b1-lab/gate-evidence-prod-yes-community.json`).
- Owner extension: lab Docker-ячейка заменяет named self-managed хост (исключение из правила checklist «не lab Docker-стенд» только для этой ячейки). Повтор gate на первом named хосте рекомендуется, но не блокирует.
- Fences без изменений: boot отказывает JDBC history / journal, dual-write, cluster, row isolation, `HARD` и серверу MariaDB.
- Tech debt **P-MYSQL-SKU-RERUN**: MySQL Enterprise, HeatWave, Amazon RDS / Aurora MySQL, Azure Database for MySQL и любая другая non-Community ячейка — свой прогон checklist до любого claim.
- TLS: обязанность оператора, этой подписью **не сертифицирован**. Ячейка evidence работала без TLS, с `allowPublicKeyRetrieval=true` — только для lab. Tech debt **P-MYSQL-TLS**.
- Покрытие SQL: только пути gate; полного паритета SQL с PostgreSQL **нет**. По коду, не запускалось: PG-only SQL в `PlatformJobService` (`(UUID) getObject` на `CHAR(36)`, `NULLS LAST`) ожидаемо ломает отчёты через очередь задач и `GET /api/v1/platform/metrics`, а `||` в `ApplicationSqlBindingStore` ожидаемо даёт лишние срабатывания триггеров SQL-binding по успеху функции. Tech debt **P-MYSQL-PG-SQL** ([checklist](../../en/mysql-sku-promotion-checklist.md#known-gaps-tracked-tech-debt)).
- Non-claims: MySQL 8.0 и 9.x; сервер MariaDB; другие JDBC-клиенты (GPL `mysql-connector-j` в classpath не бывает); B2 history / journal; active-active; tenant row isolation; `HARD` и per-app схемы.

## Последствия

MySQL — **Prod Yes (B1)** на одной ячейке: self-managed MySQL Community Server 8.4.x с MariaDB Connector/J, под B1 fences; остальные ячейки MySQL — lab до своего прогона checklist (**P-MYSQL-SKU-RERUN**). TLS — обязанность оператора, не сертифицирован (**P-MYSQL-TLS**); SQL вне путей gate не заявлен (**P-MYSQL-PG-SQL**). Смена движка — новая БД и импорт bundle. В продажах — «MySQL Community Server 8.4 B1 metadata» с клиентом MariaDB Connector/J, не «MySQL/MariaDB» и не Enterprise / managed MySQL.

## Связанные

- [0037](0037-relational-core-portability.md), [0078](0078-mssql-metadata-certification.md), [0080](0080-oracle-metadata-certification.md)
- [mysql-certification-epic](../mysql-certification-epic.md)
- [mysql-sku-promotion-checklist](../../en/mysql-sku-promotion-checklist.md)
- [storage-portability-inventory](../storage-portability-inventory.md)
