# ADR-0060: Solution authoring constraints (AI-first)

## Status

**Accepted** (2026-09-21) — Extends [0051](0051-poka-yoke-constraints-over-guards.md).

## Context

Logic-bearing objects must be SINGLETON or INSTANCE, never `ObjectType.DEVICE`. The rule lived only in prompts; reference bundles hosted BFF functions on DEVICE hubs.

## Decision

| Wave | Deliverable |
|------|-------------|
| **W1** | `LOGIC_HOST_DEVICE` canon warning; `bundle.schema.json`; SINGLETON hubs in examples |
| **W2** | Vocabulary + `{code,path,hint,docRef}` tool errors |
| **W3** | Parametrized SHAPE (`sqlBindings` / `alertRules` with `${self.*}`) |
| **W4** | Solution-as-repo CLI (`ispf pack/validate/diff/deploy`) |
| **W5** | Native LLM function calling |
| **W6** | `tests[]` + `test_function` / judge on real results |

### W1 rules

1. `functions[].objectPath` → DEVICE = WARNING `LOGIC_HOST_DEVICE`
2. SINGLETON blueprint with `targetObjectType=DEVICE` = WARNING `LOGIC_HOST_DEVICE`
3. Unknown host = WARNING `LOGIC_HOST_UNKNOWN`
4. `bindings[]` on DEVICE remain allowed
5. Canonical hub: SINGLETON blueprint → `root.platform.singleton-blueprints.{name}`
6. Schema at `schema/bundle.schema.json`; MCP `contextpack://bundle-schema`
7. `examples/` bundles carry no `LOGIC_HOST_DEVICE` (`ExamplesBundleValidationTest`) — they are agent reference material

> **Amendment (2026-10-06):** canon issues are reported, not enforced. `LOGIC_HOST_DEVICE` was an ERROR and is now a WARNING: on its own it no longer turns validate/deploy into `status: ERROR`, so the agent judge does not send a working solution back to REWORK. Functions on any object remain a platform capability; the canon tells the author where logic belongs.

## Related

- [application-principles](../application-principles.md) § Logic objects vs DEVICE
- [0051-poka-yoke-constraints-over-guards](0051-poka-yoke-constraints-over-guards.md)
