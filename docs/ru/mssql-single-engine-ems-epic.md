# Эпик: один MS SQL (метаданные + журнал + история)

> Канон: [en/mssql-single-engine-ems-epic.md](../en/mssql-single-engine-ems-epic.md).  
> Политика: [ADR-0078](decisions/0078-mssql-metadata-certification.md) §2 **не снимается**. Prod Yes этим файлом **не** ставится.

Кратко: single-tenant, один выделенный экземпляр SQL Server, профиль `mssql-single-engine`. Журнал и история в той же БД — только после opt-in и только в границах именованной фикстуры.

Гейты продаются раздельно: **A** B1 Prod Yes (Evaluation подписан), **B** capped JDBC (lab на Evaluation 2026-11-20), **C** партиции и rollup до Prod Yes B2 ([ADR-0081](decisions/0081-mssql-historian-switch-retention.md) + lab SWITCH + dry-run evidence; amend разрешает только empty staging DDL; JVM/schedule SWITCH и Prod Yes B2 не открыты), **D** локальный буфер (не часть первой фразы «всё в SQL»), **E** упаковка и DR без цифр RPO/RTO.

Нельзя обещать: неограниченный raw-scan ≤ 20 с, 10 лет rowstore «на любой отчёт», active-active ISPF, RLS как в PostgreSQL, RPO = 0 при гибели диска узла.
