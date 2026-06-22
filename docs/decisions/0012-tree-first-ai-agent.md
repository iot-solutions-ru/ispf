# ADR-0012: Tree-first AI agent (FW-44)

## Status

Accepted (2026-06-22)

## Context

FW-40…43 provide one-shot bundle generation with validation gates. Solution developers also need an **agent** that reads platform docs, inspects the live object tree, and creates nodes step-by-step (tree-first), not only via monolithic bundle JSON.

## Decision

1. **Tree-first agent runs on the platform** (`TreeFirstAgentService`) with a bounded ReAct loop (default 12 steps).
2. **Platform tools** are first-class Java handlers (not only REST wrappers for humans):
   - `search_context` — ContextPack + doc slices
   - `list_objects`, `get_object`, `create_object` — object tree API semantics
   - `validate_bundle`, `dry_run_deploy`, `import_package` — ADR-0011 gates before deploy
3. **LLM protocol**: assistant replies with a single JSON object `{"type":"tool",...}` or `{"type":"finish",...}`; tool results are appended to the conversation.
4. **Audit**: each run and tool invocation recorded in `ai_tool_audit`.
5. **Admin-only** REST: session API (`POST/GET/DELETE /api/v1/ai/agent/sessions`, `POST .../messages`); legacy `POST /api/v1/ai/agent/run` remains for one-shot use.
6. **In-memory sessions** per actor: turn history (user text + assistant summary) replayed to the LLM; truncated to `ispf.ai.agent-max-history-turns` (default 20); TTL `ispf.ai.agent-session-ttl-hours` (default 24). `AgentRunState` lives on the session so validate → import gates span multiple user messages.
7. **MCP adapter** is a future thin layer over the same tool registry (not in this ADR).

## Consequences

- Bundle-first Generate in AI Studio remains; agent is the tree-first path.
- Agent respects ACL (`ObjectAccessService`) like REST controllers.
- `import_package` tool should only be used after validate/dry-run OK (enforced in tool + prompt + session-scoped `AgentRunState`).
- Server restart or session TTL drops in-memory context; UI restores chat list from `localStorage` and refetches turns when possible.

## Alternatives considered

| Alternative | Rejected because |
|-------------|------------------|
| MCP-only, no in-server agent | Studio and CI need server-side orchestration |
| Native OpenAI tool_calls only | Breaks Ollama/custom providers; JSON ReAct is portable |
| Auto-import without gates | Violates ADR-0011 |
