# ADR-0060: Solution authoring constraints (AI-first)

## Status

**Accepted** (2026-09-21) — Extends [0051](0051-poka-yoke-constraints-over-guards.md).  
W1 canon is **WARNING on authoring validate/deploy**; **hard ERROR for `examples/`** reference material.

## Context

Logic-bearing objects must be SINGLETON or INSTANCE, never `ObjectType.DEVICE`. The rule lived only in prompts; reference bundles hosted BFF functions on DEVICE hubs.

## Decision

| Wave | Deliverable |
|------|-------------|
| **W1** | `LOGIC_HOST_DEVICE` canon; `bundle.schema.json`; SINGLETON hubs in examples |
| **W2** | Vocabulary + `{code,path,hint,docRef}` tool errors |
| **W3** | Parametrized SHAPE (`sqlBindings` / `alertRules` with `${self.*}`) |
| **W4** | Solution-as-repo CLI (`ispf pack/validate/diff/deploy`) |
| **W5** | Native LLM function calling |
| **W6** | `tests[]` + `test_function` / judge on real results |

### W1 rules

1. `functions[].objectPath` → DEVICE = WARNING `LOGIC_HOST_DEVICE` (authoring validate/deploy)
2. SINGLETON blueprint with `targetObjectType=DEVICE` = WARNING `LOGIC_HOST_DEVICE`
3. Unknown host = WARNING `LOGIC_HOST_UNKNOWN`
4. `bindings[]` on DEVICE remain allowed
5. Canonical hub: SINGLETON blueprint → `root.platform.singleton-blueprints.{name}`
6. Schema at `schema/bundle.schema.json`; MCP `contextpack://bundle-schema`
7. `examples/` bundles carry **no** `LOGIC_HOST_DEVICE` — enforced as hard failure by `ExamplesBundleValidationTest` (agent reference material)

> **Amendment (2026-10-06):** canon issues are reported, not enforced on general validate/deploy. `LOGIC_HOST_DEVICE` was an ERROR and is now a WARNING so the agent judge does not send a working solution back to REWORK. Functions on any object remain a platform capability; the canon tells the author where logic belongs.

### Honesty — Done / Partial / Gap (tension with 0051)

| Mode | Behavior | Status |
|------|----------|--------|
| Authoring validate / deploy | `LOGIC_HOST_DEVICE` → **WARNING** (does not alone set `status: ERROR`) | **Done** |
| `examples/` CI (`ExamplesBundleValidationTest`) | `LOGIC_HOST_DEVICE` → **hard fail** | **Done** |
| `ispf validate --strict` / API `?strict=true` | Ship-canon codes → **ERROR** | **Wave 1A** ([adr-tension-resolution](../adr-tension-resolution.md)) |
| Marketplace / catalog ship gate | Same as `--strict` | **Wave 1A** |
| Tool schema forbids DEVICE as function host | Prefer create on SINGLETON/INSTANCE | **Wave 1A** |

**0051 tension resolution:** canon stays hard in docs; **authoring** warns; **ship** errors. Tracked under [adr-tension-resolution](../adr-tension-resolution.md) Wave 1A.

## Related

- [application-principles](../application-principles.md) § Logic objects vs DEVICE
- [0051-poka-yoke-constraints-over-guards](0051-poka-yoke-constraints-over-guards.md)
- [0004-ai-artifact-generation-gates](0004-ai-artifact-generation-gates.md)
