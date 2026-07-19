# ADR-0050: Производственные паттерны как решения

Канонический текст: [../../en/decisions/0050-manufacturing-patterns-as-solutions.md](../../en/decisions/0050-manufacturing-patterns-as-solutions.md).

**Кратко (Accepted 2026-07-19):** traceability DAG, BoM, CTO, QMS lite, operations DAG и L4 outbox — это конфигурация решений / marketplace-бандлов ISPF, а не доменная логика платформы. Новые generic-возможности платформы допускаются только через явный REQ-PF; MES-сущности не добавляются в `main`.
