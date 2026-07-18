# ADR-0048: Швы модулизации сервера (ObjectTreePort → AI-модуль → ObjectManager)

Канонический текст: [../../en/decisions/0048-server-modularization-seams.md](../../en/decisions/0048-server-modularization-seams.md).

**Кратко (Accepted 2026-07-18):** `ObjectTreePort` → `ispf-ai-agent` → декомпозиция `ObjectManager` (+ `ObjectMetadataService`) → folder hygiene web-console. Драйверы / BL-191 вне программы. Multi-day cluster soak — lab runbook, не код. Wave 4 (`TreeCrudService`, `ObjectVariableService`, `ObjectTreeBootstrapFacade`; `ObjectManager` как фасад) — 2026-07-18. Wave 3 (`packages/ispf-ai-agent` для `com.ispf.server.ai.*`) — 2026-07-18. Wave 6 (доказательство кластера) — [cluster-chaos-soak-runbook](../cluster-chaos-soak-runbook.md).
