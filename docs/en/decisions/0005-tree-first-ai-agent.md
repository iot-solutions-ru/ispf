# ADR-0005: Tree-first AI agent (FW-44)

## Status

**Accepted** (2026-06-22) — in-server tree-first agent is the live authoring path alongside bundle Generate.

## Context

FW-40…43 provide one-shot bundle generation with validation gates. Solution developers also need an **agent** that reads platform docs, inspects the live object tree, and creates nodes step-by-step (tree-first), not only via monolithic bundle JSON.

## Decision

1. **Tree-first agent runs on the platform** (`TreeFirstAgentService`) with a bounded ReAct loop.
2. **Platform tools** are first-class Java handlers in a shared registry (same surface as MCP — [0006](0006-mcp-agent-tool-adapter.md)):
   - discovery / tree CRUD
   - `validate_bundle`, `dry_run_deploy`, `import_package` — [0004](0004-ai-artifact-generation-gates.md) gates before deploy
3. **LLM protocol**: JSON ReAct (`type: tool|finish`) as portable default; native `tool_calls` optional when the provider supports them.
4. **Audit**: each run and tool invocation in `ai_tool_audit`.
5. **Admin REST**: session API under `/api/v1/ai/agent/**`; ACL via `ObjectAccessService`.
6. **Persistent sessions** per actor (`agent_sessions` / `agent_turns`); `AgentRunState` spans validate → import across messages and restarts.
7. **Sources** live in `packages/ispf-ai-agent` ([0048](0048-server-modularization-seams.md)); REST paths stable.

### Honesty

| Claim | Status |
|-------|--------|
| In-server agent + gated deploy tools | **Done** |
| MCP over same registry | **Done** ([0006](0006-mcp-agent-tool-adapter.md)) — not “future” |
| Progressive tool packs / lean prompts / history window | **Done** (ops knobs) — details in [ai-agent](../ai-agent.md), not this ADR |

**Out of this ADR:** concrete defaults for max tokens, turn window, pack IDs — runbook / `application.yml`. Changing a knob does **not** require amending this decision.

## Consequences

- Bundle-first Generate remains; agent is the tree-first path.
- `import_package` only after validate/dry-run OK (tool + prompt + `AgentRunState`).
- Alternatives rejected: MCP-only (no Studio/CI orchestration); native-tool-calls-only (breaks Ollama/custom); auto-import without gates (violates 0004).

## Related

- [0004-ai-artifact-generation-gates](0004-ai-artifact-generation-gates.md)
- [0006-mcp-agent-tool-adapter](0006-mcp-agent-tool-adapter.md)
- [0051-poka-yoke-constraints-over-guards](0051-poka-yoke-constraints-over-guards.md)
- [0048-server-modularization-seams](0048-server-modularization-seams.md)
- [ai-agent](../ai-agent.md), [ai-development](../ai-development.md)
