# ADR-0051: Poka-yoke — constraints over guards

## Status

**Accepted** (2026-07-19) — Doctrine ADR for agent/platform quality. Implementation lands incrementally; this record is the review rule and demount plan for scaffolding in `packages/ispf-ai-agent`.

### Wave progress

| Wave | Notes |
|------|--------|
| **1 — Tool inputSchema** | **Landed** — `AgentToolInputSchemas` catalog for all platform tools; `PlatformAgentTool.inputSchema()`; validate-before-execute; MCP `tools/list` publishes real schemas (no `GENERIC_INPUT_SCHEMA` stub). |
| **1b — UI locale language** | **Landed** — console sends `uiLocale` per turn; system prompt mandates response language = web-console locale (en/ru/de/zh). |

## Context

ISPF already has tree-first agents, MCP tools, bundle gates ([0004](0004-ai-artifact-generation-gates.md)), plan/approve mutate, operator allowlists, change sets, and a creation-stack canon in [application-principles P7](../application-principles.md) (AUTHOR / SHAPE / SHIP / PROMOTE).

Quality still depends heavily on **detection and repair after the model speaks**: keyword plan guards, ground-truth path checks, JSON salvage, loop nudges, finish heuristics. Approximate scaffolding size in `ispf-ai-agent` (guards + JSON protocol + action resolver, 2026-07-19):

| Component | ~LOC |
|-----------|-----:|
| `AgentPlanGuard` | 955 |
| `AgentGroundTruthGuard` | 678 |
| `AgentJsonProtocol` (incl. salvage) | 534 |
| `AgentLoopGuard` | 315 |
| `OperatorAgentTurnGuard` | 312 |
| `AgentPlatformTurnGuard` | 284 |
| `AgentCopilotFocusGuard` | 197 |
| `AgentLlmActionResolver` | 189 |
| `AgentWidgetBindingGuard` | 131 |
| `AgentMutateApprovalGuard` | 63 |
| `AgentScopeGuard` | 53 |
| **Total** | **~3.7k** |

These catch real failures today, but they teach or police models **after** free-form text. They are model-specific, expensive to maintain, and duplicate what schemas, enums, pickers, and single canonical paths would make **impossible**.

Toyota *poka-yoke*: design the process so the error cannot occur, or is immediately obvious — not “control quality by catching mistakes later.”

## Decision

Adopt **poka-yoke** as the quality doctrine for agent and configuration surfaces:

1. Prefer **prevention (level 1)** over detection (level 2) over correction (level 3).
2. Treat heuristic guards as **temporary scaffolding** around growing constraints — not load-bearing walls.
3. Every new guard in review must name the **level-1 constraint** that will replace it, or justify why the failure is not preventable (security, HITL, irreducible domain semantics).
4. Delivery path selection follows [application-principles P7](../application-principles.md): one creation stack, not five peer “ways to build an app.”

### Three protection levels (strongest first)

| Level | Name | Meaning | Platform examples |
|------:|------|---------|-------------------|
| **1** | **Prevention** | Illegal state cannot be represented or submitted | Tool `inputSchema` with enums; path picker / grounded path refs; `bundle.schema.json`; native tool calling; recipe = single path for a task class; upsert-by-path / reconcile |
| **2** | **Detection** | Failure is immediate, structured, and actionable | `{code, path, hint, docRef}`; `validate_bundle` / `dry_run`; plan/diff before apply; acceptance verdict before “done” |
| **3** | **Correction** | Failure is cheap to undo | Change sets + revision; rollback; mutate approval as HITL brake |

**Review rule:** seeing a guard that catches an error → ask which constraint would make that error impossible. Path-string guard → path from tree/search. Broken JSON salvage → native tools + schema. Wrong widget type → enum in schema. Keyword “plan first” → interaction mode + tool allowlist by phase (not NATURAL LANGUAGE keyword lists).

### What must not be demounted as “scaffolding”

These are **policy / security / HITL**, not JSON insurance:

- Operator tool allowlist and `AgentScopeGuard` (ACL scope).
- `AgentMutateApprovalGuard` and Execute-mode consent (human approval).
- Server-side ACL / `ObjectAccessService` and bundle semantic gates ([0004](0004-ai-artifact-generation-gates.md)).
- CONTROL side-effects kept out of default agent allowlists ([0049](0049-ot-automation-excellence.md)).

### Implementation sequence

| Order | Work | Level | Effect |
|------:|------|-------|--------|
| **1** | Per-tool JSON Schema on platform tools; MCP stops emitting `GENERIC_INPUT_SCHEMA` (`additionalProperties: true`) | 1 | Args validated before handler; unlocks native function calling later |
| **2** | `bundle.schema.json` (+ CI validate) for SHIP manifests | 1 | Manifest shape not guessed |
| **3** | Structured error envelope `{code, path, hint, docRef}` on tools/API | 2 | Errors teach the rule at violation time |
| **4** | Platform acceptance / verdict service (post-create checks → PASS/FAIL) | 2 | “Done” without green checks is undefined |
| **5** | Path/widget recognition UX (picker, catalog enums) for UI **and** tool args | 1 | Recognition replaces recall |
| **6** | Native function calling (providers that support it), fed by the same schemas | 1 | Demounts salvage / free-form JSON protocol |
| **7** | Demount scaffolding per inventory below as constraints land; keep agent-regression green | — | LOC of guards falls; quality does not |

Recipes remain carriers of **explicit paths + acceptance profiles**, not bit-identical replay machines ([application-principles P7](../application-principles.md), agent recipe catalog).

---

## Guard inventory → replacing constraints

LOC counts are approximate (main sources, 2026-07-19). **Demount** means: remove or shrink after the named constraint ships and regression covers the class of failure.

| Scaffolding | ~LOC | Catches today | Level now | Replacing constraint (level 1 unless noted) | Demount when | Keep? |
|-------------|-----:|---------------|-----------|-----------------------------------------------|--------------|-------|
| `AgentJsonProtocol` salvage + free-form ReAct JSON | 534 | Truncated/malformed assistant JSON; non-schema tool args | 2–3 | Native tool calling + per-tool `inputSchema`; reject invalid args before handler | Schemas live on registry/MCP **and** FC path is default for capable providers | Shrink to thin fallback for noop/legacy providers only |
| `AgentLlmActionResolver` salvage path | 189 | Same as above at resolve time | 2–3 | Same as `AgentJsonProtocol` | Same | Thin adapter |
| `AgentGroundTruthGuard` | 678 | Invented paths; mutate before `list_objects` / blueprint discovery | 2 | Path args as **refs from prior tool results** (schema: `path` must match discovered/created set) or server resolves picker ids; blueprint id enum from catalog tools | Path-ref constraint + create APIs that return bound handles | Heuristic “soft allow if exists” may remain as server truth, not prompt police |
| `AgentPlanGuard` keyword / phase matrix | 955 | Mutate during plan; skip plan on “complex” NL; approval phrases | 2 | **Mode machine**: Ask / Plan / Execute expose disjoint tool sets in the registry (not keyword lists). Plan phase = read-only allowlist by construction | Tool catalog filtered by `AgentPlanPhase` / mode | Keep phase state machine; demount NL keyword tables |
| `AgentLoopGuard` | 315 | Repeat tools; post-error nudges | 2 | Idempotent tools + structured errors with next-step `hint`; maxSteps; optional “suggestedNextTool” from server | Structured errors + loop detector kept minimal (repeat threshold only) | Tiny loop brake OK; long hint essays demount |
| `AgentWidgetBindingGuard` | 131 | Chart/sparkline without `configure_variable_history` | 2 | Schema/precondition: history widgets require `historyEnabled` evidence or server checks variable metadata | Widget schema + server precondition on `add_dashboard_widget` | Demount client-side step archaeology |
| `AgentPlatformTurnGuard` | 284 | Finish with ERROR steps; empty dashboard/mimic; missing alert | 2 | **Acceptance verdict** (level 2 product): finish blocked unless verdict PASS for task class | Verdict service wired to finish | Replace heuristics with check ids |
| `OperatorAgentTurnGuard` | 312 | Report intent → force `list_reports`/`run_report`; stop doc search loops | 2 | Operator tools: report run as first-class capability with path enum from `list_reports` result; allowlist already limits surface | Capability map + schema enums for report paths | Keep scope/clarify; demount NL intent regex where capability covers it |
| `AgentCopilotFocusGuard` | 197 | Finish ignores live `clientFocus` | 2 | Inject focus as **bound context** (not optional prose); schema forbids asking for path/expression already in focus | Focus bound into system/tool context | Shrink to assert focus consumed |
| `AgentMutateApprovalGuard` | 63 | Mutate without plan approval | 1–2 HITL | Explicit approve / Execute mode (already) | — | **Keep** (policy) |
| `AgentScopeGuard` | 53 | Path outside operator app | 1 security | Scope allowlist (already); path picker limited to scope | — | **Keep** (security) |

### Prevention backlog (concrete)

1. **Tool schemas** — each `PlatformAgentTool` declares JSON Schema; `McpToolAdapter.listTools` publishes it; execute validates.
2. **`bundle.schema.json`** — canonical manifest schema; CI + `validate_bundle` share one source of truth.
3. **Error envelope** — guards and tools converge on `{code, path, hint, docRef}` (docRef → P7 / playbook / API doc).
4. **Acceptance verdict** — post-create/post-turn checks (objects exist, quality GOOD, dashboard widgets, alert on synthetic) → PASS/FAIL/PARTIAL; finish and “done” require PASS for the task class.
5. **P7 enforcement in agent routing** — path selection cites layer first; recipes bind to SHIP/AUTHOR defaults (no peer blueprint/change-set bootstrap).

### Success metrics

- Scaffolding LOC in the table above **falls** as items demount; agent-regression / live suite rates **do not**.
- MCP tool descriptors expose real `inputSchema` (not `additionalProperties: true` stubs) for ≥95% of platform tools.
- New PRs that add a `*Guard` without a named replacing constraint are rejected in review.

## Consequences

- AI quality work prioritizes schemas, pickers, verdicts, and P7 path canon over new keyword heuristics.
- Native function calling is gated on schemas; do not ship FC against stub schemas.
- Some detection remains forever (domain preconditions, HITL, ACL); the doctrine forbids **growing** NL/keyword scaffolding as the default fix.
- Docs: [application-principles P7](../application-principles.md) is the creation-stack canon this ADR assumes.

### Risks

- Demounting guards before constraints land will regress agent suites — always constraint first, then delete scaffolding in the same or follow-up PR with regression proof.
- Over-strict schemas can block valid advanced args — use `additionalProperties` sparingly and version schemas.
- Acceptance verdict scope creep — start with a small check catalog per task class (lab device, dashboard, bundle import), not a universal oracle.

## Related

- [application-principles](../application-principles.md) — P7 creation stack; P10 validate before mutate
- [0004-ai-artifact-generation-gates](0004-ai-artifact-generation-gates.md) — validate / dry-run / import
- [0005-tree-first-ai-agent](0005-tree-first-ai-agent.md) — tree-first agent
- [0006-mcp-agent-tool-adapter](0006-mcp-agent-tool-adapter.md) — MCP over tool registry
- [0034-agent-observability-and-session-knowledge](0034-agent-observability-and-session-knowledge.md) — audit / metrics
- [0049-ot-automation-excellence](0049-ot-automation-excellence.md) — workflow tool contracts, CONTROL out of default allowlist
- [ai-development](../ai-development.md) — agent tools and Studio
- [agent-knowledge](../agent-knowledge.md) — AUTHOR/SHIP variants under P7
- [collaboration](../collaboration.md) — change sets (correction level)
- [agent-regression](../agent-regression.md) — proof when demounting scaffolding
