# ADR-0060: Solution authoring constraints (AI-first)

## Status

**Accepted** (2026-09-21) — Extends [0051](0051-poka-yoke-constraints-over-guards.md).  
W1 canon is **WARNING on authoring validate/deploy**; **ERROR on ship paths** (CLI `--strict`, marketplace catalog, agent final acceptance). `examples/` stay a hard fail.

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

> **Amendment (2026-10-06):** on **authoring** validate/deploy, canon issues are reported, not enforced. `LOGIC_HOST_DEVICE` alone does not turn interactive `validate_bundle` or API validate/deploy without `strict` into `status: ERROR`. Functions on any object remain a platform capability; the canon tells the author where logic belongs.
>
> **Amendment (2026-10-13) — ship-default:** accepting a shipped solution is a different gate. Agent deploy-playbook validate/dry-run/import (`deploy_step_*`, `import_package`, `run_deploy_playbook`) call ship-canon promotion, so `LOGIC_HOST_DEVICE` is **ERROR** and the bundle is not applied. The judge then returns **REWORK** (`logic.host`). Interactive validate may stay WARNING.

### Honesty — Done / Partial / Gap (tension with 0051)

| Mode | Behavior | Status |
|------|----------|--------|
| Authoring validate / deploy | `LOGIC_HOST_DEVICE` → **WARNING** (does not alone set `status: ERROR`) | **Done** |
| `examples/` CI (`ExamplesBundleValidationTest`) | `LOGIC_HOST_DEVICE` → **hard fail** | **Done** |
| `ispf validate --strict` / API `?strict=true` | Ship-canon codes → **ERROR** | **Done** (Wave 1A) |
| Marketplace / catalog ship gate | Same as `--strict` (`catalogShipCanonFailures` / `findLogicHostDeviceIssues`) | **Done** (Wave 1A) |
| **ship-default** (agent final acceptance) | Playbook validate, dry-run, and import promote ship canon; judge **REWORK** on `LOGIC_HOST_DEVICE` for `APPLICATION_BUNDLE` or those ship tools | **Partial** |
| Function host tools | `hostKind` enum `SINGLETON` \| `INSTANCE` (no DEVICE default); live `ObjectType.DEVICE` → `LOGIC_HOST_DEVICE`; acceptance check `logic.host` | **Done** |

**What remains on ship-default (Partial):** unqualified `POST /api/v1/applications/{appId}/deploy` and interactive `validate_bundle` / `dry_run_deploy` still do **not** promote unless the caller passes `strict=true` or uses the agent ship tools above. That split is intentional for authoring; it is not a platform-wide default of `--strict` on every HTTP deploy.

**0051 tension resolution:** canon stays hard in docs; **authoring** warns; **ship** errors. Program: [adr-tension-resolution](../adr-tension-resolution.md).

## Related

- [application-principles](../application-principles.md) § Logic objects vs DEVICE
- [0051-poka-yoke-constraints-over-guards](0051-poka-yoke-constraints-over-guards.md)
- [0004-ai-artifact-generation-gates](0004-ai-artifact-generation-gates.md)
