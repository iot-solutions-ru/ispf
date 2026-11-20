# Эпик: сертификация MS SQL Server (ADR-0078)

> Каноническая доска поставки (фазы, DoD, CI-матрица): **[en/mssql-certification-epic.md](../en/mssql-certification-epic.md)**.  
> Политика: [ADR-0078](decisions/0078-mssql-metadata-certification.md).

Кратко: сначала **B1** (метаданные на MSSQL), опционально **B2** (journal/history jdbc в той же БД). 10 лет сырого архива и multi-year отчёт ≤20 с на rowstore MSSQL — **не claim**. MySQL/Oracle не планируются этим эпиком.

### Фаза 4 — синхронность миграций

- [x] Lockstep-проверка `tools/mssql-flyway-lockstep-check.py` включена в CI; grandfather list хранится в `docs/en/mssql-flyway-exceptions.txt`.
- [ ] Дисциплина expand/contract (ADR-0064) — пока только процессная заметка до изменения LTS.

### Фаза 6 — честность кластерного режима

- [x] A-A на MSSQL отмечен как **не сертифицированный**; при `ispf.cluster.enabled=true` загрузка блокируется.
- [x] AG listener / RCSI / выделенная БД документированы в checklist продвижения SKU и README миграций MSSQL.

Снятие запрета и статус **Prod Yes (B1)** остаются заблокированы до проверки на именованном SKU, отличном от Developer.
