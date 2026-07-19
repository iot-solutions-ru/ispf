# ADR-0051: Poka-yoke — constraints вместо гвардов

Канонический текст: [../../en/decisions/0051-poka-yoke-constraints-over-guards.md](../../en/decisions/0051-poka-yoke-constraints-over-guards.md).

**Кратко (Accepted 2026-07-19):** качество агента и конфигурации строится по Toyota *poka-yoke* — три уровня защиты (prevention → detection → correction). Эвристические гварды в `packages/ispf-ai-agent` (~3.7k LOC: Plan/GroundTruth/JSON salvage/Loop/…) — временные леса: в ревью каждый гвард обязан назвать level-1 constraint, который его заменит. Не демонтировать ACL/scope, mutate approval, bundle gates и CONTROL allowlist. Порядок внедрения: схемы инструментов → `bundle.schema.json` → structured errors → acceptance-вердикт → native FC → демонтаж scaffolding по inventory в ADR. Стек творения — [application-principles P7](../application-principles.md) (AUTHOR / SHAPE / SHIP / PROMOTE).

**Wave 1 (landed):** `AgentToolInputSchemas` на все platform tools; validate-before-execute; MCP `tools/list` отдаёт реальные `inputSchema` (заглушка `GENERIC_INPUT_SCHEMA` снята).

**Wave 1b (landed):** консоль передаёт `uiLocale` на каждый turn; system prompt требует отвечать на языке локали UI (en/ru/de/zh).
