# ADR-0081: Месячный SWITCH retention историка MSSQL (только дизайн)

## Статус

**Accepted as design** (2026-11-20). **Amended** (2026-11-21): пустой Flyway staging DDL (`V101.5`) разрешён и отгружен; JVM/schedule `SWITCH` по-прежнему запрещены.

Канонический текст: [EN ADR-0081](../../en/decisions/0081-mssql-historian-switch-retention.md).

Lab helper: `tools/mssql-b1-lab/switch-variable-samples-month.sql`. Lab confirm (mutation) на disposable Evaluation: `gate-evidence-switch-confirm-evaluation.json` (2026-11-22). Retention продукта — app `DELETE` + `max-retention-days`. §2b / Prod Yes B2 не открываются.

## Контекст

C1 может дать `APPLIED` только на пустой БД; `SKIPPED_NONEMPTY` постоянен ([0064](0064-release-compatibility-lts.md)). Cursor и ingest-rollups — не печать на drop месяца.

## Решение (кратко; полное AND — в EN)

- Только maintenance login вне JVM; runtime без `ALTER`.
- Отказ без `APPLIED` и выровненных индексов; staging пустой и идентичный.
- Один UTC-месяц за прогон, только oldest eligible; месяц, пересекающий `max-retention-days`, не роняется.
- Global floor; список рядов без rollup-копии уничтожается вместе с месяцем.
- Cursor/report не seal и не precondition; left catch-all без MERGE/SWITCH.
- Без executable SQL в тексте этого ADR; lab-скрипт в `tools/mssql-b1-lab/` обязан держать гарды из Decision и не подключается к Gate B стендам.

## Последствия

`V101.5` + отказ скрипта при missing/nonempty/non-identical staging отгружены. JVM/schedule / Prod Yes B2 этим документом не открываются.

## Связанные

- [0064](0064-release-compatibility-lts.md), [0078](0078-mssql-metadata-certification.md)
- [mssql-single-engine-ems-epic](../mssql-single-engine-ems-epic.md) (канон EN)
