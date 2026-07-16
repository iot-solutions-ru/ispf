> **Язык:** русская версия (вычитка). Канонический английский: [en/decisions/0006-mcp-agent-tool-adapter.md](../../en/decisions/0006-mcp-agent-tool-adapter.md).

# ADR-0006: MCP adapter поверх platform agent tools

## Статус

**Принято** (2026-06-21)

> **License note:** Platform core is **AGPL v3** (+ optional commercial dual-license). Historical Apache wording in this ADR is superseded — see [license.md](../license.md).

## Контекст

[0005-tree-first-ai-agent](0005-tree-first-ai-agent.md) реализует in-server tree-first agent (`TreeFirstAgentService`) с ограниченным ReAct loop и Java tool handlers в `PlatformAgentToolRegistry`. Внешние клиенты (Cursor IDE, CI pipelines, custom assistants) всё чаще используют [Model Context Protocol (MCP)](https://modelcontextprotocol.io/) для tool discovery и invocation.

Нужен тонкий мост, чтобы MCP clients переиспользовали **те же** ACL-aware platform tools без дублирования бизнес-логики и без обхода validation gates 0004.

## Решение

1. Добавить optional **MCP server module** (`com.ispf.server.ai.mcp`), который экспонирует `PlatformAgentToolRegistry` как MCP tools.
2. **Без второй реализации tools** — MCP `tools/list` мапит registry `name()` + `description()`; `tools/call` делегирует в `PlatformAgentTool.execute(arguments, context)`.
3. **Auth**: MCP connections требуют те же admin credentials, что и `/api/v1/ai/agent/**` (Bearer token или настроенный API key header). Actor identity попадает в `AgentContext`.
4. **Session model**: MCP clients могут:
   - использовать **stateless** single tool calls (без chat history), или
   - передать `sessionId` в tool arguments для привязки к persistent DB sessions ([`agent_sessions`](../../../packages/ispf-server/src/main/resources/db/migration/postgresql/V1__baseline.sql) (section formerly `V38__agent_sessions`)).
5. **Transport**: начать с **stdio** MCP для local dev и **SSE/HTTP** для remote hub; по умолчанию не в core JAR (profile `mcp`).
6. **Вне scope v1**: MCP resources/prompts для ContextPack (только tools); auto-import без validate/dry-run gates.

## Operator scope (BL-109)

Когда MCP `AgentContext` использует `AgentProfile.OPERATOR` (operator copilot / scoped app), выполнение tool ограничено `OperatorAgentToolAllowlist` — read-only catalog, reports, historian и app documents. Mutating platform tools (`create_object`, `configure_driver`, `import_package`, …) возвращают `Tool not allowed in operator mode`. Admin MCP sessions используют полный catalog.

## Tool surface (initial)

| MCP tool | Platform tool |
|----------|---------------|
| `search_context` | `search_context` |
| `list_objects` | `list_objects` |
| `get_object` | `get_object` |
| `create_object` | `create_object` |
| `delete_object` | `delete_object` |
| `configure_driver` | `configure_driver` |
| `driver_control` | `driver_control` |
| `get_dashboard_layout` | `get_dashboard_layout` |
| `set_dashboard_layout` | `set_dashboard_layout` |
| `add_dashboard_widget` | `add_dashboard_widget` |
| `validate_bundle` | `validate_bundle` |
| `dry_run_deploy` | `dry_run_deploy` |
| `import_package` | `import_package` (gated by session `AgentRunState`) |

## Implementation sketch

```text
McpServer (profile mcp)
  └── McpToolAdapter
        └── PlatformAgentToolRegistry
              └── existing Java handlers + ObjectAccessService
```

Оценка: **1 sprint** (stdio + HTTP SSE, auth, integration test с mock MCP client).

## Последствия

- Cursor и другие MCP hosts могут управлять platform без custom REST glue.
- Поведение tools идентично Web Console agent и audit (`ai_tool_audit` entries tagged `source=mcp`).
- Дополнительная зависимость (MCP SDK) изолирована за profile `mcp` — AGPL core default без изменений.
- Будущее: ContextPack slices как MCP `resources` — **Done** (Phase 17.3, `McpResourceAdapter`, URIs `contextpack://<slice>`).

## Рассмотренные альтернативы

| Альтернатива | Отклонена, потому что |
|-------------|------------------|
| Только MCP, убрать in-server agent | Studio и CI нуждаются в server orchestration (0005) |
| Дублировать tools как REST wrappers для MCP | Две реализации расходятся; баги ACL |
| Полные MCP resources для live tree | Больший scope; ContextPack + sessions достаточны для v1 |

## Связанные материалы

- [ai-development](../ai-development.md)
- [0005-tree-first-ai-agent](0005-tree-first-ai-agent.md)
- `PlatformAgentToolRegistry.java`
