# ADR-0010: Binding rules only (v0.8.0)

## Status

Accepted (2026-06-23)

## Context

A variable with `bindingExpression` did not support cross-object propagation: when device telemetry changed, hub variables with `refAt(...)` were not recalculated because `propagateBindings()` worked only within one object.

A breaking change is acceptable before 1.0.

## Decision

1. **Remove** `Variable.bindingExpression`, `BindingEvaluator` object-loop, `propagateBindings()`, REST/agent param `bindingExpression`.
2. **Single mechanism** — `BindingRule` + `BindingRuleEngine` + `@bindingRules` JSON on the object.
3. **Cross-object** — `BindingDependencyIndex` + `BindingPropagationListener` on `VARIABLE_UPDATED` (including driver telemetry).
4. **Models** — `ModelBindingRule` instead of `ModelBindingDefinition` / `defaultBinding`.
5. **Migration** — column `binding_expr` removed (`V41` / clean `V1`); dev — recreate DB.

## Consequences

- API and UI: Bindings tab, CRUD `/binding-rules`.
- Agent: `create_binding_rule` instead of binding on `create_variable`.
- Documentation: [bindings.md](../bindings.md) rewritten.
- Column `binding_expr` removed from schema (V1 + V41); dev — DB recreate instead of runtime migration.

## Related

- [bindings.md](../bindings.md)
- [object-model.md](../object-model.md)
- FW-48 / virt-cluster playbook
