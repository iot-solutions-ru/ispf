> **Язык:** русская версия (вычитка). Канонический английский: [en/decisions/0034-agent-observability-and-session-knowledge.md](../../en/decisions/0034-agent-observability-and-session-knowledge.md).

# ADR-0034: Наблюдаемость агента, session knowledge и глубина плана

## Статус

Принято (2026-07-06)

## Контекст

Tree-first agent (ADR-0005) вырос: режимы Ask / Plan / Execute, Judge, guards, 90+ tools. Операторам и администраторам нужно:

- Latency и token usage по шагам (паттерны Sim, Dify)
- Session-scoped document RAG для загрузки ТЗ/спецификаций (AnythingLLM)
- Визуальный turn pipeline без LangGraph runtime
- Prompt version в audit для regression analysis
- Более лёгкий Plan mode по умолчанию (decompose в стиле AutoAgent) vs полный SIF intake

Мы явно **не** встраиваем Dify, LangGraph или AutoAgent как runtime dependencies.

## Решение

### FW-49 — Agent trace and audit metrics

- Расширить `ai_tool_audit` полями `latency_ms`, `prompt_tokens`, `completion_tokens`, `turn_id`, `step_no`, `interaction_mode`, `prompt_profile`.
- `GET /api/v1/ai/agent/sessions/{id}/trace` — агрегация turn steps + audit rows.
- Web Console: `AgentTracePanel` в деталях agent chat.

### FW-50 — Session knowledge

- Таблица `agent_session_documents` (FK `agent_sessions`, CASCADE delete).
- REST upload/list/delete; tool `search_session_context` (read-only).
- Prompt injection в Ask and Plan builders (excerpt block, как operator app docs).

### FW-51 — Turn graph view

- Client-side graph из `steps_json` (list / graph tabs в `AgentRunDetails`).

### FW-52 — Agent metrics and prompt versioning

- Константа `AGENT_PROMPT_VERSION` пишется в audit.
- `GET /api/v1/ai/agent/metrics?days=N` — aggregates для AI Studio.

### FW-53 — Plan depth LITE vs FULL

- Default Plan: `goal` + 3–7 `steps` без обязательного `specBrief` / 8 sections.
- Full SIF intake только когда пользователь запрашивает полное ТЗ, attachment TZ или explicit keywords.

### Ops

- `apply-platform-update.sh` health wait: 120s → 180s (slow VPS cold start).

## Последствия

- Audit CSV получает новые колонки; старые строки — NULL metrics.
- Session documents делят лимиты operator docs (2 MB, text formats).
- Plan panel по умолчанию может показывать более короткие планы; full TZ без изменений по запросу.

## Рассмотренные альтернативы

| Alternative | Rejected because |
|-------------|------------------|
| Embed Dify / LangGraph | Duplicates tree-first tools; license and ops burden |
| Vector DB for session RAG v1 | ILIKE/keyword sufficient for 2 MB docs per session |
| Mandatory 8-layer plan always | UX friction; Ask/Execute fixes showed planning leakage |
