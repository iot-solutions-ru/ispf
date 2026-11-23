# Эпик: сертификация Oracle (ADR-0080)

> **Язык:** русская версия. Канон: [en/oracle-certification-epic.md](../en/oracle-certification-epic.md).  
> **Статус:** B1 **Prod Yes** только на self-managed Oracle Database Free 23.26.x + `ojdbc17` от оператора (owner override 2026-11-23, [checklist](../en/oracle-sku-promotion-checklist.md)). `ddl-auto=validate` не исправлен, ячейка работает с `none`. Остальные редакции и 19c — **P-ORACLE-SKU**, **P-ORACLE-19C**.  
> **Политика:** MySQL B1 Prod Yes подписан 2026-11-22 ([0079](decisions/0079-mysql-metadata-certification.md)) — это разблокировало B1 pack. Prod Yes Oracle получен своим gate и называет только ячейку Free.

## Уже есть

- Schema-switch fail-closed, честность dialect (ORA-00001, `NUMTODSINTERVAL`, `1`/`0`, `SELECT 1 FROM DUAL`, identifier с `"` или NUL — fail-closed)
- `assertOracleB1Fences` + `OracleCertificationBootGuard` без single-engine bypass
- ADR-0080 amended: генератор + `db/migration/oracle/V1__baseline.sql` + lockstep CI
- Драйвер только на classpath оператора (`PropertiesLauncher` + `loader.path`); без драйвера — отказ с инструкцией; `oracleLabJdbc` только для Docker gate
- SQL под 19c: `limitClause`, `selectTopOneForUpdate` (`ROWID` + `ROWNUM`), lock-row `FROM DUAL`, claim `FOR UPDATE SKIP LOCKED` без row limit
- Совместимость с ojdbc: UUID как текст (ORA-17004), `Instant` как `TIMESTAMP` (ORA-18716), quoted `comment`
- Hot-reload guard JDBC journal, Testcontainers TCK, lab-стенд и 18/18 evidence ([checklist](../en/oracle-sku-promotion-checklist.md))

## Подпись Prod Yes (B1) — 2026-11-23

- Named-ячейка: Oracle Database Free 23.26.x по owner override из Lab-green ячейки, 18 PASS / 0 FAIL (`tools/oracle-b1-lab/gate-evidence-lab-free.json`); решение владельца — `tools/oracle-b1-lab/gate-evidence-prod-yes-free.json`; повтор на первом named хосте рекомендован, но не блокирует
- Hibernate `ddl-auto=validate` — **письменно снят, не исправлен**; на любом развёртывании Oracle `SPRING_JPA_HIBERNATE_DDL_AUTO=none` (**P-ORACLE-VALIDATE**)
- 19c, запись `''` вне путей gate, PG-only SQL вне путей gate, шифрование Oracle Net / TLS — non-claims с тикетами, не проверены (**P-ORACLE-19C**, **P-ORACLE-EMPTY**, **P-ORACLE-PG-SQL**, **P-ORACLE-TLS**)
- B1 fences без изменений; ISPF по-прежнему не поставляет JDBC-драйвер Oracle

## Tech debt после подписи

- **P-ORACLE-SKU** — прогон checklist на каждой named non-Free редакции или managed-сервисе (Standard Edition 2, Enterprise Edition, Autonomous Database, Amazon RDS for Oracle)
- **P-ORACLE-19C** — прогон B1 gate на 19c, а не только на 23.26 Free
- **P-ORACLE-VALIDATE** — добиться прохождения Hibernate `ddl-auto=validate` на Oracle; до тех пор `none`
- **P-ORACLE-EMPTY** — аудит записей `''` в `NOT NULL` текстовые колонки на путях вне gate
- **P-ORACLE-PG-SQL** — проверить остальной JDBC SQL на PG-only конструкции (bucket aggregate, `CLOB` через `=`), портировать или закрыть fence то, что найдёт проверка
- **P-ORACLE-TLS** — повтор gate с шифрованием Oracle Net или TLS с wallet, рекомендован на первом named хосте
