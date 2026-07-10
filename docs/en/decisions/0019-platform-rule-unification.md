# ADR-0019: Unified Platform Rule model (dashboard + bindings)

## Status

Accepted (phases 0–2 implemented; phase 3 — deprecation of legacy mini-DSL)

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

ADR [0010](0010-binding-rules-only.md) already fixed **binding rules** as the sole variable computation mechanism. We must **extend the same model** to dashboard UI logic and events without parallel systems.

## Decision

1. **Platform Rule = BindingRule** with extended `target` and activator `onContextChange`. One form: **when → if (CEL) → then (effect)**.

2. **Three effect kinds** (`target.kind`):

   | `kind` | Purpose |
   |--------|---------|
   | `variable` | as today — write to an object variable (`variableName`, `field`) |
   | `context` | write to `@dashboardContext` on `DASHBOARD` object (`path`, dot-notation) |
   | `event` | publish platform event (`eventName`, optional payload from `expression`) |

   Backward compatibility: if `kind` is absent → `variable` (current JSON `{ "variableName", "field" }`).

3. **Dashboard context** — reserved variable `@dashboardContext` (JSON) on `DASHBOARD` object:

   ```json
   {
     "selection": { "device": "root.platform.devices.snmp-01" },
     "params": { "mode": "normal" },
     "widgets": { "alarm-panel": { "visible": true } },
     "updatedAt": "...",
     "updatedBy": "operator"
   }
   ```

   Web-console `DashboardSession` is a **cache** of this variable + optimistic update; source of truth is server + WebSocket.

4. **Activator `onContextChange`** in `BindingActivators` — rule on `DASHBOARD` object recalculates on PUT `@dashboardContext` (table click, form, nav).

5. **Widgets contain no logic** — only layout and data addresses (`selectionKey`, `paramKey`, `contextPathKey`). Show/hide, switch mode — **rules** writing to `context.widgets.*` or `context.params.*`.

6. **One editor** — existing Bindings tab / `BindingRulesPanel`; for dashboards — same Rules tab in Dashboard Builder.

7. **Legacy migration** (phases 3+): `showWhenJson`, `payloadFilterExpr` → CEL rules; do not add `behaviorJson` on widgets.

## Consequences

- One language (CEL), one engine, one documentation set ([BINDINGS.md](../bindings.md), [PLATFORM_LOGIC.md](../platform-logic.md)).
- Dashboard context is durable, multi-client via WS; events in journal.
- Table→details remains, but semantics = «context change», not a separate subsystem.

Risks:

- UI latency: rules on server; optimistic session + reconcile via WS required (phase 1).
- Extending `BindingRuleEngine` and deserializing `BindingTarget` — breaking-safe only with default `kind=variable`.
- CEL is harder for operators than JSON predicates — mitigated by rule templates and AI agent.

## Implementation plan

| Phase | Content |
|-------|---------|
| 0 | ADR, [PLATFORM_LOGIC.md](../platform-logic.md), update BINDINGS/DASHBOARDS |
| 1 | `@dashboardContext`, `target.kind`, `onContextChange`, engine, WS sync |
| 2 | Rules tab in Dashboard Builder, demo layout |
| 3 | Deprecate legacy mini-DSL |

## Related

- [0010-binding-rules-only.md](0010-binding-rules-only.md)
- [BINDINGS.md](../bindings.md)
- [DASHBOARDS.md](../dashboards.md)
- [PLATFORM_LOGIC.md](../platform-logic.md)
