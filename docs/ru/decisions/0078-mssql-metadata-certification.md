# ADR-0078: Сертификация метаданных на MS SQL Server (дополнение к 0037)

## Статус

**Accepted** (2026-11-19) — контракт политики и сертификации. **Prod Yes (B1)** 2026-11-20, **B2** 2026-11-22 на Evaluation `16.0.4325.4`. **Prod Yes (A-A)** 2026-11-22 — dialect fence снят ([A-A checklist](../../en/mssql-aa-sku-promotion-checklist.md)); NATS всё ещё нужен; AG soak = tech-debt **P-MSSQL-AA-SOAK**. Licensed Standard / Enterprise / Azure SQL DB|MI наследуют B1+B2+A-A (owner 2026-11-22; tech-debt **P-MSSQL-SKU-RERUN**). Без single-engine B1 fences отказывают JDBC history/journal/dual-write.

Дополняет [0037-relational-core-portability](0037-relational-core-portability.md) и [0064-release-compatibility-lts](0064-release-compatibility-lts.md): MS SQL Server — **первый** non-PG целевой движок для продакшена. ADR фиксирует смысл «certified», явные **non-claims** и gate, без которого нельзя ставить `ISPF_METADATA_DB_KIND=mssql` в проде.

**Не** планирует MySQL/Oracle. В 0037 они остаются заглушками «после MSSQL» без обязательств по срокам.

Канонический текст (детали таблиц и gate): английская редакция [en/decisions/0078-mssql-metadata-certification.md](../../en/decisions/0078-mssql-metadata-certification.md). Эпик: [en/mssql-certification-epic.md](../../en/mssql-certification-epic.md).

## Контекст

Площадки (в т.ч. GxP EMS) часто требуют Microsoft SQL Server для DBA/бэкапов/политики. Сейчас ISPF:

- Relational core — **PostgreSQL 16 (+ Timescale)** или **H2** (local/edge).
- Есть `MssqlDialect`, Flyway `mssql/V1` и fail-closed B1 fences — **Prod Yes (B1)** на подписанной ячейке Evaluation.
- Корпоративный MSSQL уже доступен как **внешний** `DATA_SOURCE` / JDBC для отчётов — это **не** хостинг `ISPF_DB` на MSSQL.

Формулировка «через JDBC работает с любой БД» не означает «метаданные платформы поднимаются на любом JDBC URL».

## Решение (кратко)

1. **Два скоупа:** **B1** — только метаданные на MSSQL; journal/history — другой store или вне claim. **B2** — single-DB jdbc journal+history — **отдельный** gate после B1.
2. **История:** 10 лет сырого архива в rowstore MSSQL и multi-year raw scan ≤ 20 с — **non-claim** (без границ). §2b в EN — пустой шаблон allowed claims; заполняется только после evidence. Доска: [mssql-single-engine-ems-epic](../../en/mssql-single-engine-ems-epic.md).
3. **RLS:** пока нет native security policy — boot **refuse** при `db-row-isolation=true` на MSSQL; single-tenant EMS с isolation off без RLS-claim; multi-tenant SaaS на MSSQL — out of scope.
4. **App schemas / `search_path`:** на MSSQL **unsupported** до отдельного механизма; текущий `USE` / `USE dbo` в диалекте некорректен.
5. **Кластер:** N JVM → один writable primary (AG listener); RCSI обязателен; dedicated DB (B1), dedicated instance (B2).
6. **Flyway:** greenfield `mssql/V1`; далее lockstep twin к каждому PG `Vnn` или явный ANSI exception в CI.
7. **Бан B1 снят**; **B2** и **A-A** подписаны 2026-11-22 (capped JDBC; cluster dialect allowed). Developer / Edge — не эта подпись. Без single-engine refuse jdbc history/journal/dual-write **остаётся**. AG soak = **P-MSSQL-AA-SOAK**.
8. **RLS default:** `ISPF_TENANT_DB_ROW_ISOLATION` по умолчанию **true** → на MSSQL boot refuse; single-tenant B1 явно ставит `false`.

## Последствия

- B1+B2+A-A: Evaluation + owner-extended licensed Standard / Enterprise / Azure SQL DB|MI. Product SWITCH вне claim; AG soak tech-debt.
- Оценка «недели DDL» ≠ полный B1; B2 и 10y SLO — отдельные проекты.

## Связанные

- [0037](0037-relational-core-portability.md), [0064](0064-release-compatibility-lts.md), [0066](0066-security-model.md)
- [en/mssql-certification-epic](../../en/mssql-certification-epic.md)
