# Эпик: один MS SQL (метаданные + журнал + история)

> Канон: [en/mssql-single-engine-ems-epic.md](../en/mssql-single-engine-ems-epic.md).  
> Политика: [ADR-0078](decisions/0078-mssql-metadata-certification.md) §2 **не снимается**. Prod Yes этим файлом **не** ставится.

Кратко: single-tenant, один выделенный экземпляр SQL Server, профиль `mssql-single-engine`. Журнал и история в той же БД — только после opt-in и только в границах именованной фикстуры.

Гейты продаются раздельно: **A** B1 Prod Yes (Evaluation + owner-extended licensed SKUs), **B** capped JDBC = **Prod Yes B2**, **A-A** N JVMs / один writable primary (NATS; tech-debt **P-MSSQL-AA-SOAK**), **C** партиции/rollup/lab SWITCH ([ADR-0081](decisions/0081-mssql-historian-switch-retention.md); JVM/schedule SWITCH не product path), **D** локальный буфер, **E** упаковка и DR без цифр RPO/RTO.

Нельзя обещать: неограниченный raw-scan ≤ 20 с, 10 лет rowstore «на любой отчёт», multi-master / завершённый AG failover soak (tech-debt **P-MSSQL-AA-SOAK**), RLS как в PostgreSQL, RPO = 0 при гибели диска узла.
