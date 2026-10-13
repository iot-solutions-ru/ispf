# ADR-0019: Unified Platform Rule model (dashboard + bindings)

## Status

**Accepted** (2026-06-28) — phases **0–2 Done**; phase **3** (legacy mini-DSL removal) **Partial / open**.

## Context

The platform accumulated several **incompatible ways** to express logic:

| Mechanism | Where |
|-----------|-------|
| Binding rules (`@bindingRules`) | objects, CEL |
| Alert rules | ALERT, CEL |
| Dashboard session (`selection`, `params`) | web-console, ad-hoc keys |
| `showWhenJson` | function-form fields |
| `payloadFilterExpr` | event-feed widget, mini-DSL |
| Spreadsheet `ISPF()` | cells |

Operators and engineers cannot remember ten DSLs. AI can; humans cannot.

ADR [0010-binding-rules-only](0010-binding-rules-only.md) already fixed **binding rules** as the sole variable computation mechanism. We must **extend the same model** to dashboard UI logic and events without parallel systems.

**Industry bar:** leaders converge on one expression language for compute + UI conditions (vs widget-local scripting sprawl). ISPF’s differentiator is **tree-first Platform Rules** (CEL + BindingRule) shared by bindings, dashboards, and agents — not a second “UI script” runtime.

## Decision

1. **Platform Rule = BindingRule** with extended `target` and activator `onContextChange`. One form: **when → if (CEL) → then (effect)**.

2. **Three effect kinds** (`target.kind`):

   | `kind` | Purpose |
   |--------|---------|
   | `variable` | write to an object variable (`variableName`, `field`) |
   | `context` | write to `@dashboardContext` on `DASHBOARD` (`path`, dot-notation) |
   | `event` | publish platform event (`eventName`, optional payload from `expression`) |

   Backward compatibility: if `kind` is absent → `variable`.

3. **Dashboard context** — reserved variable `@dashboardContext` (JSON) on `DASHBOARD`; web-console session is a **cache**; source of truth is server + WebSocket.

4. **Activator `onContextChange`** — rules on `DASHBOARD` recalculate on PUT `@dashboardContext`.

5. **Widgets contain no logic** — only layout and data addresses. Show/hide and mode switches are **rules** writing `context.widgets.*` / `context.params.*` ([anti-patterns](../anti-patterns.md)).

6. **One editor** — Bindings / Rules tab (including Dashboard Builder).

7. **Legacy migration** (phase 3): prefer CEL Platform Rules for new work; do not add `behaviorJson` on widgets. `showWhenJson` and client mini-DSL `payloadFilterExpr` remain for compatibility until removed.

### Honesty — Done / Partial / Gap

| Phase | Content | Status |
|-------|---------|--------|
| 0 | ADR + [platform-logic](../platform-logic.md) | **Done** |
| 1 | `@dashboardContext`, `target.kind`, `onContextChange`, engine, WS | **Done** |
| 2 | Rules tab in Dashboard Builder, demos | **Done** |
| 3 | Deprecate / remove legacy mini-DSL (`showWhenJson`, non-CEL `payloadFilterExpr`) | **Partial** — still accepted at runtime; new authoring must prefer CEL rules |

Do **not** claim “zero widget DSLs in the product” until phase 3 closes. Do claim “one canonical rule model for new solutions.”

## Consequences

- One language (CEL), one engine, one documentation set ([bindings](../bindings.md), [platform-logic](../platform-logic.md)).
- Dashboard context is durable, multi-client via WS; events in journal.
- Fitness: reject unknown `target.kind`; examples and agent prompts teach rules, not `behaviorJson`.
- Risks: UI latency (optimistic session + WS reconcile); CEL harder than JSON predicates — templates + AI mitigate.

## Related

- [0010-binding-rules-only](0010-binding-rules-only.md)
- [0040-unified-computations-ui](0040-unified-computations-ui.md)
- [bindings](../bindings.md), [dashboards](../dashboards.md), [platform-logic](../platform-logic.md)
- [anti-patterns](../anti-patterns.md) — no heavy logic in widgets
