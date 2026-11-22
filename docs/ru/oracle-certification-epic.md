# Эпик: сертификация Oracle (ADR-0080)

> **Язык:** русская версия. Канон: [en/oracle-certification-epic.md](../en/oracle-certification-epic.md).  
> **Статус:** **Lab green** на Oracle Database Free. **Prod: No** — Free не production-редакция.  
> **Политика:** MySQL B1 Prod Yes подписан 2026-11-22 ([0079](decisions/0079-mysql-metadata-certification.md)) — это разблокировало B1 pack. Для Prod Yes Oracle нужен свой named-SKU gate.

## Уже есть

- Schema-switch fail-closed, честность dialect (ORA-00001, `NUMTODSINTERVAL`, `1`/`0`, `SELECT 1 FROM DUAL`, identifier с `"` или NUL — fail-closed)
- `assertOracleB1Fences` + `OracleCertificationBootGuard` без single-engine bypass
- ADR-0080 amended: генератор + `db/migration/oracle/V1__baseline.sql` + lockstep CI
- Драйвер только на classpath оператора (`PropertiesLauncher` + `loader.path`); без драйвера — отказ с инструкцией; `oracleLabJdbc` только для Docker gate
- SQL под 19c: `limitClause`, `selectTopOneForUpdate` (`ROWID` + `ROWNUM`), lock-row `FROM DUAL`, claim `FOR UPDATE SKIP LOCKED` без row limit
- Совместимость с ojdbc: UUID как текст (ORA-17004), `Instant` как `TIMESTAMP` (ORA-18716), quoted `comment`
- Hot-reload guard JDBC journal, Testcontainers TCK, lab-стенд и 18/18 evidence ([checklist](../en/oracle-sku-promotion-checklist.md))

## Дальше (Prod Yes — named SKU)

- Named-редакция и хост: Enterprise / SE2 19c или 23ai/26ai, либо один managed-сервис (**P-ORACLE-SKU**)
- Прогон B1 gate на 19c
- Hibernate `ddl-auto=validate` — исправить или письменно снять
- Аудит записей `''` в `NOT NULL` на не покрытых путях; шифрование Oracle Net / TLS
