# ISPF — Cursor subagents

Субагенты для делегирования из чата ISPF. **Канон платформы** — в **aggregatePlatformKnowledgeAi** и `docs/en/`.

| Файл | `name` (Task `subagent_type`) | Роль | Модель (пресет B) |
|------|-------------------------------|------|-------------------|
| `scenario-architect.md` | `ispf-scenario-architect` | Архитектор: правила проекта, Scenario Frame, Problem Brief | `claude-4.6-sonnet-medium-thinking` |
| `platform-implementer.md` | `ispf-platform-implementer` | Разработчик: только внутри Frame, LIVE_PLATFORM | `gpt-5.3-codex` |
| `judge.md` | `ispf-judge` | Судья: conformance к Frame, User Gate | `gpt-5.5-medium` |

**Родитель (чат):** `composer-2.5-fast`, **не Auto** — оркестрация pipeline и governance `REPO` / `LIVE_*`.
