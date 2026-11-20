# ADR-0079: Сертификация метаданных на MySQL (дополнение к 0037)

## Статус

**Accepted** (2026-11-20) — политика и charter сертификации. Реализация **Not started**.

Канонический текст: [EN ADR-0079](../../en/decisions/0079-mysql-metadata-certification.md).

## Контекст

Наличие `MysqlDialect` и внешнего MySQL через `DATA_SOURCE` не означает, что `ISPF_DB` сертифицирован на MySQL. Flyway pack отсутствует. Сертификация MySQL **не** распространяется на MariaDB.

## Решение

- Первый scope — **B1 metadata** на именованной версии/редакции MySQL 8.x.
- Работы начинаются только после named-SKU gate MSSQL из [0078](0078-mssql-metadata-certification.md).
- `Prod: No`; `UncertifiedRelationalEngineBootGuard` уже запрещает boot для `mysql` и остаётся fail-closed до полного gate.
- Journal/history не входят в B1. Lab/Testcontainers не дают production claim.
- MariaDB требует отдельного charter, Flyway pack и gate.

Этот ADR не создаёт и не разрешает Flyway SQL.

## Последствия

MySQL остаётся запрещённым как production metadata DB; смена движка — новая БД и импорт bundle.

## Связанные

- [0037](0037-relational-core-portability.md), [0078](0078-mssql-metadata-certification.md), [0080](0080-oracle-metadata-certification.md)
