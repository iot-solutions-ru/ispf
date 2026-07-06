# ADR-0034: Agent observability, session knowledge, and plan depth

## Status

Accepted (2026-07-06)

## Context

The tree-first agent (ADR-0005) has grown: Ask / Plan / Execute modes, Judge, guards, 90+ tools. Operators and admins need:

- Per-step latency and token usage (patterns from Sim, Dify)
- Session-scoped document RAG for TZ/spec uploads (AnythingLLM)
- Visual turn pipeline without adopting LangGraph runtime
- Prompt version in audit for regression analysis
- Lighter default Plan mode (AutoAgent-style decompose) vs full SIF intake

We explicitly **do not** embed Dify, LangGraph, or AutoAgent as runtime dependencies.

## Decision

### FW-49 — Agent trace and audit metrics

- Extend `ai_tool_audit` with `latency_ms`, `prompt_tokens`, `completion_tokens`, `turn_id`, `step_no`, `interaction_mode`, `prompt_profile`.
- `GET /api/v1/ai/agent/sessions/{id}/trace` aggregates turn steps + audit rows.
- Web Console: `AgentTracePanel` in agent chat details.

### FW-50 — Session knowledge

- Table `agent_session_documents` (FK `agent_sessions`, CASCADE delete).
- REST upload/list/delete; tool `search_session_context` (read-only).
- Prompt injection in Ask and Plan builders (excerpt block, like operator app docs).

### FW-51 — Turn graph view

- Client-side graph from `steps_json` (list / graph tabs in `AgentRunDetails`).

### FW-52 — Agent metrics and prompt versioning

- `AGENT_PROMPT_VERSION` constant written to audit.
- `GET /api/v1/ai/agent/metrics?days=N` — aggregates for AI Studio.

### FW-53 — Plan depth LITE vs FULL

- Default Plan: `goal` + 3–7 `steps` without mandatory `specBrief` / 8 sections.
- Full SIF intake only when user requests full TZ, attachment TZ, or explicit keywords.

### Ops

- `apply-platform-update.sh` health wait: 120s → 180s (slow VPS cold start).

## Consequences

- Audit CSV gains new columns; old rows have NULL metrics.
- Session documents share operator doc limits (2 MB, text formats).
- Plan panel may show shorter plans by default; full TZ unchanged when requested.

## Alternatives considered

| Alternative | Rejected because |
|-------------|------------------|
| Embed Dify / LangGraph | Duplicates tree-first tools; license and ops burden |
| Vector DB for session RAG v1 | ILIKE/keyword sufficient for 2 MB docs per session |
| Mandatory 8-layer plan always | UX friction; Ask/Execute fixes showed planning leakage |
