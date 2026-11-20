# ADR-0078: Сертификация метаданных на MS SQL Server (дополнение к 0037)

## Статус

**Accepted** (2026-11-19) — контракт политики и сертификации. **Prod Yes (B1)** подписан 2026-11-20 на SQL Server 2022 Enterprise Evaluation `16.0.4325.4` ([checklist](../../en/mssql-sku-promotion-checklist.md), issue #814). Evaluation — 180-дневный trial класса Enterprise, не бессрочная лицензия. B1 boot fences по-прежнему отказывают JDBC history/journal, dual-write и `ispf.cluster.enabled=true`. Prod Yes для B2/A-A нет.

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
7. **Бан B1 снят** gate из EN ADR §8 на Enterprise Evaluation `16.0.4325.4` (2026-11-20). Developer и Azure SQL Edge — не эта подпись. Boot-refuse для jdbc history/journal/dual-write/cluster **остаётся**. Prod Yes B2 и A-A — не подписаны.
8. **RLS default:** `ISPF_TENANT_DB_ROW_ISOLATION` по умолчанию **true** → на MSSQL boot refuse; single-tenant B1 явно ставит `false`.

## Последствия

- B1: метаданные на MSSQL можно ставить на движке класса подписанной ячейки; хосту всё ещё нужна лицензия Standard/Enterprise или Azure SQL DB/MI. B2/A-A boot отказывает; Prod Yes для них — нет.
- Оценка «недели DDL» ≠ полный B1; B2 и 10y SLO — отдельные проекты.

## Связанные

- [0037](0037-relational-core-portability.md), [0064](0064-release-compatibility-lts.md), [0066](0066-security-model.md)
- [en/mssql-certification-epic](../../en/mssql-certification-epic.md)
