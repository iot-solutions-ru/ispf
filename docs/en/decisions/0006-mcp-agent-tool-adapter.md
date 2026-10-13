# ADR-0006: MCP adapter over platform agent tools

## Status

**Accepted** (2026-06-21) — MCP bridge **shipped**; this record is the contract, not a sprint estimate.

> **License note:** Platform core is **AGPL v3** (+ optional commercial dual-license). Historical Apache wording is superseded — see [license.md](../license.md).

## Context

[0005-tree-first-ai-agent](0005-tree-first-ai-agent.md) delivers an in-server tree-first agent (`TreeFirstAgentService`) with a bounded ReAct loop and Java tool handlers in `PlatformAgentToolRegistry`. External clients (Cursor IDE, CI pipelines, custom assistants) speak the [Model Context Protocol (MCP)](https://modelcontextprotocol.io/) for tool discovery and invocation.

We need a thin bridge so MCP clients reuse the **same** ACL-aware platform tools without duplicating business logic or bypassing [0004](0004-ai-artifact-generation-gates.md) validation gates.

## Decision

1. Optional **MCP server module** (`com.ispf.server.ai.mcp`) exposes `PlatformAgentToolRegistry` as MCP tools.
2. **No second tool implementation** — MCP `tools/list` maps registry `name()` + `description()`; `tools/call` delegates to `PlatformAgentTool.execute(arguments, context)`.
3. **Auth**: same admin credentials as `/api/v1/ai/agent/**` (Bearer or configured API key). Actor identity flows into `AgentContext`.
4. **Session model**: MCP clients may use **stateless** single tool calls, or pass `sessionId` to attach to persistent DB sessions (`agent_sessions`).
5. **Transport**: **stdio** (local) and **SSE/HTTP** (remote hub); profile `mcp`.
6. **MCP resources:** ContextPack slices as MCP `resources` — **Done** (`McpResourceAdapter`, URIs `contextpack://<slice>`). MCP prompts remain out of scope. Auto-import without validate/dry-run stays forbidden ([0004](0004-ai-artifact-generation-gates.md)).
7. **Tool schemas:** `tools/list` publishes per-tool `inputSchema` from the registry ([0051](0051-poka-yoke-constraints-over-guards.md) Wave 1); no `GENERIC_INPUT_SCHEMA` stub.

### Honesty — Done / Partial / Gap

| Slice | Status |
|-------|--------|
| Tool bridge = registry (no duplicate handlers) | **Done** |
| Real `inputSchema` on `tools/list` | **Done** ([0051](0051-poka-yoke-constraints-over-guards.md)) |
| ContextPack MCP resources | **Done** |
| Operator allowlist on MCP | **Done** (`OperatorAgentToolAllowlist`) |
| Live object tree as MCP resources | **Gap** (out of scope) |
| MCP prompts library | **Gap** (out of scope) |

### Operator scope (BL-109)

When MCP `AgentContext` uses `AgentProfile.OPERATOR` (operator copilot / scoped app), tool execution is restricted to `OperatorAgentToolAllowlist` — read-only catalog, reports, historian, and app documents. Mutating platform tools (`create_object`, `configure_driver`, `import_package`, …) return `Tool not allowed in operator mode`. Admin MCP sessions use the full catalog.

### Tool surface (initial)

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

### Implementation sketch

```text
McpServer (profile mcp)
  └── McpToolAdapter
        └── PlatformAgentToolRegistry
              └── existing Java handlers + ObjectAccessService
```

## Consequences

- Cursor and other MCP hosts can operate the platform without custom REST glue.
- Tool behaviour stays identical to Web Console agent and audit (`ai_tool_audit` entries tagged `source=mcp`).
- Additional dependency (MCP SDK) isolated behind `mcp` profile — AGPL core default unchanged.
- ContextPack slices are MCP `resources` (`McpResourceAdapter`); live tree as MCP resources remains out of scope.

## Alternatives considered

| Alternative | Rejected because |
|-------------|------------------|
| MCP-only, remove in-server agent | Studio and CI need server orchestration (0005) |
| Duplicate tools as REST wrappers for MCP | Two implementations diverge; ACL bugs |
| Full MCP resources for live tree | Higher scope; ContextPack + sessions sufficient |

## Related

- [ai-development](../ai-development.md)
- [0004-ai-artifact-generation-gates](0004-ai-artifact-generation-gates.md)
- [0005-tree-first-ai-agent](0005-tree-first-ai-agent.md)
- [0051-poka-yoke-constraints-over-guards](0051-poka-yoke-constraints-over-guards.md) — schemas + allowlists
- `PlatformAgentToolRegistry.java`, `McpResourceAdapter.java`
