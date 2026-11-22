# ADR-0079: Сертификация метаданных на MySQL (дополнение к 0037)

## Статус

**Accepted** (2026-11-20) — политика и charter. **Implementation: Phase 1 lab** (2026-11-22). **Prod: No** — boot по-прежнему отказывает `mysql`.

Канонический текст: [EN ADR-0079](../../en/decisions/0079-mysql-metadata-certification.md).

## Контекст

`MysqlDialect` и внешний MySQL через `DATA_SOURCE` не означают сертификацию `ISPF_DB`. Flyway pack ещё не авторизован. MariaDB ≠ MySQL (config/product fail-closed).

## Решение

- Первый scope — **B1 metadata** на MySQL 8.x (предпочтительно 8.4 LTS).
- Lab Phase 1 после MSSQL B1 Prod Yes; Prod Yes MySQL — отдельный gate.
- Journal/history вне B1; **нет** single-engine bypass в fences.
- Без GPL `mysql-connector-j` до решения counsel; Flyway SQL / generator — следующий PR после amend ADR.
- Oracle ([0080](0080-oracle-metadata-certification.md)) — только после MySQL B1 Prod Yes.

## Последствия

MySQL остаётся запрещённым как production metadata DB; смена движка — новая БД и импорт bundle.

## Связанные

- [0037](0037-relational-core-portability.md), [0078](0078-mssql-metadata-certification.md), [0080](0080-oracle-metadata-certification.md)
- [mysql-certification-epic](../mysql-certification-epic.md)
- [storage-portability-inventory](../storage-portability-inventory.md)
