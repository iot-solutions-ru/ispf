# Эпик: сертификация Oracle (ADR-0080)

> **Язык:** русская версия. Канон: [en/oracle-certification-epic.md](../en/oracle-certification-epic.md).  
> **Статус:** Phase 1 lab. **Prod: No** — boot по-прежнему отказывает `oracle`.  
> **Политика:** Phase 1 начат раньше решением владельца. MySQL B1 Prod Yes подписан 2026-11-22 — B1 pack может начинаться.

## Уже есть

- Boot refuse + schema-switch fail-closed (сообщения ADR-0080)
- Dialect honesty (ORA-00001, `NUMTODSINTERVAL`, `1`/`0`, `SELECT 1 FROM DUAL`, identifier с `"` или NUL — fail-closed)
- `assertOracleB1Fences` + `OracleCertificationBootGuard` без single-engine bypass

## Дальше (отдельный PR; MySQL B1 Prod Yes подписан)

- Драйвер `ojdbc*` только после решения counsel
- Amend ADR-0080, lab Flyway `V1` + generator + lockstep
- Oracle SQL для `limitClause`, `selectTopOneForUpdate`, lock-row insert и claim задач
- TCK на named SKU → только потом Prod Yes
