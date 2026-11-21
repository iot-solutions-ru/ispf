# Эпик: сертификация MS SQL Server (ADR-0078)

> Каноническая доска поставки (фазы, DoD, CI-матрица): **[en/mssql-certification-epic.md](../en/mssql-certification-epic.md)**.  
> Политика: [ADR-0078](decisions/0078-mssql-metadata-certification.md).

Кратко: сначала **B1** (метаданные на MSSQL), затем отдельные гейты **B2** ([single-engine](mssql-single-engine-ems-epic.md)). Неограниченные 10 лет сырого архива и multi-year отчёт ≤20 с на rowstore MSSQL — **не claim**. MySQL/Oracle — отдельные ADR-0079/0080 (не этот эпик).

### Lab / стенд (B1 evidence)

- Harness: [`tools/mssql-b1-lab/`](../../tools/mssql-b1-lab/README.md) — `stand-local.ps1` (SQL Server 2022 AMD64), `run.sh` (Azure SQL Edge на `ispf-lab` aarch64), `loadtest.py`.
- Lab-green: Flyway V1, cert-bundle, fence cluster, metadata load (~4350 read rps / ~187 write rps; import serial).
- Официальный `mssql/server` под QEMU на aarch64 падает — на lab используем Edge.

### Фаза 4 — синхронность миграций

- [x] Lockstep-проверка `tools/mssql-flyway-lockstep-check.py` включена в CI; grandfather list хранится в `docs/en/mssql-flyway-exceptions.txt`.
- [x] Дисциплина expand/contract — процессная заметка в [0064](decisions/0064-release-compatibility-lts.md) (grandfather ≠ Prod Yes).

### Фаза 6 — честность кластерного режима

- [x] Active-active на MSSQL **не сертифицирован** — boot отказывает `ispf.cluster.enabled=true` (`MssqlCertificationBootGuard`).
- [x] AG listener / RCSI / выделенная БД документированы в checklist продвижения SKU и README миграций MSSQL.

**Prod Yes (B1)** подписан 2026-11-20 на SQL Server 2022 Enterprise Evaluation `16.0.4325.4` ([checklist](../en/mssql-sku-promotion-checklist.md), issue #814). Это 180-дневный trial класса Enterprise, не бессрочная лицензия. B1 fences для JDBC history/journal/dual-write/cluster остаются fail-closed (JDBC history/journal — только opt-in `ISPF_MSSQL_SINGLE_ENGINE`). **Gate B (B2-core)** прогнан на Evaluation (`gate-evidence-b2-core-evaluation.json`); **Prod Yes (B2)** в 0037 — только после полного Gate C (C1 + C2 writer есть; [ADR-0081](decisions/0081-mssql-historian-switch-retention.md) + lab SWITCH-скрипт есть; product/scheduled SWITCH и read-path cutover ещё открыты). A-A не подписан.
