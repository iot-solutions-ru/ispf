# Эпик: один MS SQL (метаданные + журнал + история)

> Канон: [en/mssql-single-engine-ems-epic.md](../en/mssql-single-engine-ems-epic.md).  
> Политика: [ADR-0078](decisions/0078-mssql-metadata-certification.md) §2 **не снимается**. Prod Yes этим файлом **не** ставится.

Кратко: single-tenant, один выделенный экземпляр SQL Server, профиль `mssql-single-engine`. Журнал и история в той же БД — только после opt-in и только в границах именованной фикстуры.

Гейты продаются раздельно: **A** подпись B1 (ещё не сделана), **B** capped JDBC, **C** партиции и rollup до Prod Yes B2, **D** локальный буфер (не часть первой фразы «всё в SQL»), **E** упаковка и DR без цифр RPO/RTO.

Нельзя обещать: неограниченный raw-scan ≤ 20 с, 10 лет rowstore «на любой отчёт», active-active ISPF, RLS как в PostgreSQL, RPO = 0 при гибели диска узла.
