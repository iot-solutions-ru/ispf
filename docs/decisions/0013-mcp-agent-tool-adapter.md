# ADR-0013: MCP adapter over platform agent tools

## Status

**Accepted** (2026-06-21)

## Context

[ADR-0012](0012-tree-first-ai-agent.md) delivers an in-server tree-first agent (`TreeFirstAgentService`) with a bounded ReAct loop and Java tool handlers in `PlatformAgentToolRegistry`. External clients (Cursor IDE, CI pipelines, custom assistants) increasingly speak the [Model Context Protocol (MCP)](https://modelcontextprotocol.io/) for tool discovery and invocation.

We need a thin bridge so MCP clients reuse the **same** ACL-aware platform tools without duplicating business logic or bypassing ADR-0011 validation gates.

## Decision

1. Add an optional **MCP server module** (`com.ispf.server.ai.mcp`) that exposes `PlatformAgentToolRegistry` as MCP tools.
2. **No second tool implementation** — MCP `tools/list` maps registry `name()` + `description()`; `tools/call` delegates to `PlatformAgentTool.execute(arguments, context)`.
3. **Auth**: MCP connections require the same admin credentials as `/api/v1/ai/agent/**` (Bearer token or configured API key header). Actor identity flows into `AgentContext`.
4. **Session model**: MCP clients may either:
   - use **stateless** single tool calls (no chat history), or
   - pass `sessionId` in tool arguments to attach to persistent DB sessions ([`agent_sessions`](../packages/ispf-server/src/main/resources/db/migration/V38__agent_sessions.sql)).
5. **Transport**: start with **stdio** MCP for local dev and **SSE/HTTP** for remote hub; not bundled into core JAR by default (profile `mcp`).
6. **Out of scope for v1**: MCP resources/prompts for ContextPack (only tools); auto-import without validate/dry-run gates.

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

Estimated effort: **1 sprint** (stdio + HTTP SSE, auth, integration test with mock MCP client).

## Consequences

- Cursor and other MCP hosts can operate the platform without custom REST glue.
- Tool behaviour stays identical to Web Console agent and audit (`ai_tool_audit` entries tagged `source=mcp`).
- Additional dependency (MCP SDK) isolated behind `mcp` profile — Apache core default unchanged.
- Future: expose ContextPack slices as MCP `resources` — **Done** (Phase 17.3, `McpResourceAdapter`, URIs `contextpack://<slice>`).

## Alternatives considered

| Alternative | Rejected because |
|-------------|------------------|
| MCP-only, remove in-server agent | Studio and CI need server orchestration (ADR-0012) |
| Duplicate tools as REST wrappers for MCP | Two implementations diverge; ACL bugs |
| Full MCP resources for live tree | Higher scope; ContextPack + sessions sufficient for v1 |

## Related

- [AI_DEVELOPMENT.md](../AI_DEVELOPMENT.md)
- [ADR-0012](0012-tree-first-ai-agent.md)
- `PlatformAgentToolRegistry.java`
