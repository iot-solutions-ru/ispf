# ADR-0080: Сертификация метаданных на Oracle (дополнение к 0037)

## Статус

**Accepted** (2026-11-20) — политика и charter сертификации. **Implementation: Phase 1 lab** (2026-11-22). **Prod: No** — boot по-прежнему отказывает `oracle`.

Канонический текст: [EN ADR-0080](../../en/decisions/0080-oracle-metadata-certification.md).

## Контекст

Наличие `OracleDialect` и внешнего Oracle через `DATA_SOURCE` не означает, что `ISPF_DB` сертифицирован на Oracle. Flyway pack ещё не авторизован; версия, редакция и JDBC driver должны быть названы и проверены. Quoted identifier в Oracle не может содержать двойную кавычку или NUL; схема в Oracle — это пользователь БД.

## Решение

- Первый scope — **B1 metadata** на именованной версии/редакции Oracle и JDBC driver (кандидаты: 19c или 23ai). Oracle Database Free — только lab.
- Lab Phase 1 начат 2026-11-22 решением владельца, до MySQL B1 Prod Yes: только честность dialect и fences. B1 pack Oracle (Flyway SQL, driver, TCK) и любой Prod Yes — только после MySQL B1 Prod Yes из [0079](0079-mysql-metadata-certification.md).
- `Prod: No`; `UncertifiedRelationalEngineBootGuard` / `assertEngineCertified` запрещают boot для `oracle` до полного gate. `assertOracleB1Fences` / `OracleCertificationBootGuard` заранее подключены: без row isolation, `HARD`, dual-write, cluster и JDBC history/journal на `ISPF_DB`; **нет** single-engine bypass.
- Без `ojdbc*` на classpath до решения counsel ([0016b — AGPL/dual-licensing](0016-agpl-dual-licensing.md)); Flyway SQL / generator — следующий PR после amend ADR.
- Container/free edition/lab не сертифицирует другую production edition.

Этот ADR пока не создаёт и не разрешает Flyway SQL.

## Последствия

Oracle остаётся запрещённым как production metadata DB; смена движка — новая БД и импорт bundle. Ранний Phase 1 не меняет порядок: MySQL B1 Prod Yes идёт до B1 pack Oracle.

## Связанные

- [0037](0037-relational-core-portability.md), [0078](0078-mssql-metadata-certification.md), [0079](0079-mysql-metadata-certification.md)
- [oracle-certification-epic](../oracle-certification-epic.md)
- [storage-portability-inventory](../storage-portability-inventory.md)
