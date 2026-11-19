# ADR-0080: Сертификация метаданных на Oracle (дополнение к 0037)

## Статус

**Accepted** (2026-11-20) — политика и charter сертификации. Реализация **Not started**.

Канонический текст: [EN ADR-0080](../../en/decisions/0080-oracle-metadata-certification.md).

## Контекст

Наличие `OracleDialect` и внешнего Oracle через `DATA_SOURCE` не означает, что `ISPF_DB` сертифицирован на Oracle. Flyway pack отсутствует; версия, редакция и JDBC driver должны быть названы и проверены.

## Решение

- Первый scope — **B1 metadata** на именованной версии/редакции Oracle и JDBC driver.
- Работы начинаются только после B1 gate MySQL из [0079](0079-mysql-metadata-certification.md).
- `Prod: No`; `UncertifiedRelationalEngineBootGuard` уже запрещает boot для `oracle` и остаётся fail-closed до полного gate.
- Journal/history не входят в B1. Container/free edition/lab не сертифицирует другую production edition.

Этот ADR не создаёт и не разрешает Flyway SQL.

## Последствия

Oracle остаётся запрещённым как production metadata DB; смена движка — новая БД и импорт bundle.

## Связанные

- [0037](0037-relational-core-portability.md), [0078](0078-mssql-metadata-certification.md), [0079](0079-mysql-metadata-certification.md)
