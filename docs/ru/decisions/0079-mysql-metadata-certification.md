# ADR-0079: Сертификация метаданных на MySQL (дополнение к 0037)

## Статус

**Accepted** (2026-11-20) — политика и charter. **Amended 2026-11-22 — B1 pack + Lab green:** lab Flyway SQL авторизован, драйвер выбран, Lab-green gate пройден на MySQL 8.4.13 Community. **Prod: No — Lab green.** MySQL `ISPF_DB` стартует только под B1 fences (`assertMysqlB1Fences`); named-SKU Prod Yes не подписан.

Канонический текст: [EN ADR-0079](../../en/decisions/0079-mysql-metadata-certification.md).

## Контекст

`MysqlDialect` и внешний MySQL через `DATA_SOURCE` не означают сертификацию `ISPF_DB`. MariaDB ≠ MySQL (config/product fail-closed).

## Решение

- Scope — **B1 metadata**; lab-ячейка — **MySQL 8.4 LTS Community** (8.0 не подписывается).
- Journal/history вне B1; **нет** single-engine bypass в fences.
- `assertEngineCertified` больше не отказывает `mysql`: как MSSQL, MySQL стартует только под B1 fences (до Flyway и на context refresh). Oracle — по-прежнему hard refuse.
- Testcontainers и dev-lab — только lab-сигнал, не production-сертификация.
- Oracle ([0080](0080-oracle-metadata-certification.md)) — только после MySQL B1 Prod Yes.

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
- Hibernate: `MySQLDialect` закреплён (иначе драйвер MariaDB даёт `MariaDBDialect` даже на MySQL).
- Блокировки аудита: `INSERT … ON DUPLICATE KEY UPDATE` (portable вариант давал deadlock 1213).

### Amendment 3 — Lab-green gate

Flyway TCK + concurrent audit + lease failover (`MysqlFlywayBaselineTckTest`), live boot + import (`MysqlLiveBundleImportTckTest`), lab-стенд `tools/mysql-b1-lab` (health, login, Flyway, dialect, import, второй JVM, аудит) и boot refuse для row isolation, `HARD`, JDBC history, dual-write, JDBC journal, cluster и сервера MariaDB. Evidence: `tools/mysql-b1-lab/gate-evidence-lab-community.json` — 2026-11-22, 17 PASS / 0 FAIL.

Открыто до Prod Yes: Hibernate `ddl-auto=validate` не проходит на этой ячейке (вне `local` нужен `SPRING_JPA_HIBERNATE_DDL_AUTO=none`), PG-only SQL на не покрытых путях, TLS вместо `allowPublicKeyRetrieval`. Owner-extended Prod Yes — только после повторного checklist на named self-managed хосте и с tech debt **P-MYSQL-SKU-RERUN** для Enterprise / managed / non-Community ячеек.

## Последствия

MySQL — **Lab green, Prod: No**; смена движка — новая БД и импорт bundle. В продажах — «MySQL 8.4 B1 metadata (lab)», не «MySQL/MariaDB».

## Связанные

- [0037](0037-relational-core-portability.md), [0078](0078-mssql-metadata-certification.md), [0080](0080-oracle-metadata-certification.md)
- [mysql-certification-epic](../mysql-certification-epic.md)
- [mysql-sku-promotion-checklist](../../en/mysql-sku-promotion-checklist.md)
- [storage-portability-inventory](../storage-portability-inventory.md)
