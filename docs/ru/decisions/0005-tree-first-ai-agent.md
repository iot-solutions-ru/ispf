> **Язык:** русская версия (вычитка). Канонический английский: [en/decisions/0005-tree-first-ai-agent.md](../../en/decisions/0005-tree-first-ai-agent.md).

# ADR-0005: Tree-first AI agent (FW-44)

## Статус

Принято (2026-06-22)

## Контекст

FW-40…43 дают one-shot генерацию bundle с validation gates. Разработчикам solution также нужен **agent**, который читает platform docs, инспектирует live object tree и создаёт узлы пошагово (tree-first), а не только через монолитный bundle JSON.

## Решение

1. **Tree-first agent работает на platform** (`TreeFirstAgentService`) с ограниченным ReAct loop (по умолчанию 12 шагов).
2. **Platform tools** — first-class Java handlers (не только REST wrappers для людей):
   - `search_context` — ContextPack + doc slices
   - `list_objects`, `get_object`, `create_object`, `delete_object` — семантика object tree API
   - `validate_bundle`, `dry_run_deploy`, `import_package` — gate 0004 перед deploy
3. **Протокол LLM**: assistant отвечает одним JSON-объектом `{"type":"tool",...}` или `{"type":"finish",...}`; результаты tool добавляются в conversation.
4. **Audit**: каждый run и tool invocation записываются в `ai_tool_audit`.
5. **Admin-only** REST: session API (`POST/GET/DELETE /api/v1/ai/agent/sessions`, `POST .../messages`); legacy `POST /api/v1/ai/agent/run` остаётся для one-shot use.
6. **Persistent sessions** per actor в PostgreSQL (`agent_sessions`, `agent_turns`); turn history воспроизводится в LLM; TTL `ispf.ai.agent-session-ttl-hours` (по умолчанию 24). `AgentRunState` хранится в `run_state_json`, чтобы gate validate → import охватывали несколько user messages и server restarts.
7. **MCP adapter** — будущий тонкий слой над тем же tool registry (не в этом ADR).

## Последствия

- Bundle-first Generate в AI Studio сохраняется; agent — путь tree-first.
- Agent соблюдает ACL (`ObjectAccessService`) как REST controllers.
- Tool `import_package` следует использовать только после validate/dry-run OK (принудительно в tool + prompt + session-scoped `AgentRunState`).
- Server restart сохраняет DB-backed context до TTL; UI восстанавливает chat list из `localStorage` и по возможности refetch turns.

## Рассмотренные альтернативы

| Альтернатива | Отклонена, потому что |
|-------------|------------------|
| Только MCP, без in-server agent | Studio и CI нуждаются в server-side orchestration |
| Только native OpenAI tool_calls | Ломает Ollama/custom providers; JSON ReAct переносим |
| Auto-import без gates | Нарушает 0004 |

## Поправка (2026-07-18) — Gradle-модуль

По [0048](../../en/decisions/0048-server-modularization-seams.md) Wave 3 исходники агента `com.ispf.server.ai.*` перенесены в модуль `packages/ispf-ai-agent` (пакеты без смены имён). Пути REST `/api/v1/ai/**` стабильны.
