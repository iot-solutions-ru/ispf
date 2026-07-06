# docanima — Cursor subagents

Субагенты для делегирования из чата docanima. **Канон платформы** — в **aggregatePlatformKnowledgeAi**; домен MES — в **docanima**.

| Файл | `name` (Task `subagent_type`) | Роль | Модель (пресет B) |
|------|-------------------------------|------|-------------------|
| `scenario-architect.md` | `scenario-architect` | Архитектор: правила проекта, Scenario Frame, Problem Brief | `claude-4.6-sonnet-medium-thinking` |
| `platform-implementer.md` | `platform-implementer` | Разработчик: только внутри Frame, LIVE_PLATFORM | `gpt-5.3-codex` |
| `judge.md` | `docanima-judge` | Судья: conformance к Frame, User Gate | `gpt-5.5-medium` |

**Родитель (чат):** `composer-2.5-fast`, **не Auto** — оркестрация pipeline и governance `REPO` / `LIVE_*`.

