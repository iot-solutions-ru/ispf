# Эпик: сертификация MySQL (ADR-0079)

> **Язык:** русская версия. Канон: [en/mysql-certification-epic.md](../en/mysql-certification-epic.md).  
> **Статус:** Phase 1 lab. **Prod: No** — boot по-прежнему отказывает `mysql`.  
> **Политика:** lab после MSSQL B1 Prod Yes. MariaDB ≠ MySQL. Oracle — только после MySQL B1 Prod Yes.

## Уже есть

- Boot refuse + schema-switch fail-closed
- MariaDB не алиасится в MySQL
- Dialect honesty (1062, DATE_ADD, fences без single-engine bypass)

## Дальше (отдельный PR)

- Драйвер без GPL до решения counsel
- Lab Flyway `V1` + generator + lockstep
- TCK на named SKU → только потом Prod Yes
