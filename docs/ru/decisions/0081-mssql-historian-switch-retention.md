# ADR-0081: Месячный SWITCH retention историка MSSQL (только дизайн)

## Статус

**Accepted as design** (2026-11-20). Реализация **Not started**.

Канонический текст: [EN ADR-0081](../../en/decisions/0081-mssql-historian-switch-retention.md).

Этот ADR фиксирует правила будущего `SWITCH` / staging-`TRUNCATE` под maintenance login. Он **не** разрешает скрипт, Flyway, JVM-задачу, TCK со `SWITCH` месяца и копируемый SQL.

Retention сейчас — app `DELETE` + `max-retention-days`. Charts на raw `observed_at`. §2b / Prod Yes B2 не открываются.

## Контекст

C1 может дать `APPLIED` только на пустой БД; `SKIPPED_NONEMPTY` постоянен ([0064](0064-release-compatibility-lts.md)). Cursor и ingest-rollups — не печать на drop месяца.

## Решение (кратко; полное AND — в EN)

- Только maintenance login вне JVM; runtime без `ALTER`.
- Отказ без `APPLIED` и выровненных индексов; staging пустой и идентичный.
- Один UTC-месяц за прогон, только oldest eligible; месяц, пересекающий `max-retention-days`, не роняется.
- Global floor; список рядов без rollup-копии уничтожается вместе с месяцем.
- Cursor/report не seal и не precondition; left catch-all без MERGE/SWITCH.
- Без executable SQL и без stub с одним `RAISERROR`.

## Последствия

Первый PR со скриптом или миграцией — отдельно, R4, со ссылкой на каждое правило EN ADR. Prod Yes B2 этим документом не ставится.

## Связанные

- [0064](0064-release-compatibility-lts.md), [0078](0078-mssql-metadata-certification.md)
- [mssql-single-engine-ems-epic](../mssql-single-engine-ems-epic.md) (канон EN)
