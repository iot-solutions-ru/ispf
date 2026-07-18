# ADR-0005: Tree-first AI agent (FW-44)

## Status

Accepted (2026-06-22)

## Context

FW-40…43 provide one-shot bundle generation with validation gates. Solution developers also need an **agent** that reads platform docs, inspects the live object tree, and creates nodes step-by-step (tree-first), not only via monolithic bundle JSON.

## Decision

1. **Tree-first agent runs on the platform** (`TreeFirstAgentService`) with a bounded ReAct loop (default 12 steps).
2. **Platform tools** are first-class Java handlers (not only REST wrappers for humans):
   - `search_context` — ContextPack + doc slices
   - `list_objects`, `get_object`, `create_object`, `delete_object` — object tree API semantics
   - `validate_bundle`, `dry_run_deploy`, `import_package` — 0004 gates before deploy
3. **LLM protocol**: assistant replies with a single JSON object `{"type":"tool",...}` or `{"type":"finish",...}`; tool results are appended to the conversation.
4. **Audit**: each run and tool invocation recorded in `ai_tool_audit`.
5. **Admin-only** REST: session API (`POST/GET/DELETE /api/v1/ai/agent/sessions`, `POST .../messages`); legacy `POST /api/v1/ai/agent/run` remains for one-shot use.
6. **Persistent sessions** per actor in PostgreSQL (`agent_sessions`, `agent_turns`); turn history replayed to the LLM; TTL `ispf.ai.agent-session-ttl-hours` (default 24). `AgentRunState` stored in `run_state_json` so validate → import gates span multiple user messages and server restarts.
7. **MCP adapter** is a future thin layer over the same tool registry (not in this ADR).

## Consequences

- Bundle-first Generate in AI Studio remains; agent is the tree-first path.
- Agent respects ACL (`ObjectAccessService`) like REST controllers.
- `import_package` tool should only be used after validate/dry-run OK (enforced in tool + prompt + session-scoped `AgentRunState`).
- Server restart keeps DB-backed context until TTL; UI restores chat list from `localStorage` and refetches turns when possible.

## Alternatives considered

| Alternative | Rejected because |
|-------------|------------------|
| MCP-only, no in-server agent | Studio and CI need server-side orchestration |
| Native OpenAI tool_calls only | Breaks Ollama/custom providers; JSON ReAct is portable |
| Auto-import without gates | Violates 0004 |

## Amendment (2026-07-18) — Gradle module

Per [0048](0048-server-modularization-seams.md) Wave 3, agent/tool sources under `com.ispf.server.ai.*` live in Gradle module `packages/ispf-ai-agent` (package names unchanged). REST paths `/api/v1/ai/**` remain stable; the server app soft-wires the module onto the runtime classpath and component-scans `com.ispf.server`.
