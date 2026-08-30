# ADR-0055: CEL formal verification as product AI/condition gate

## Status

Accepted (2026-08-30) — **product** (not experimental MVP)

## Context

ISPF is AI-first (ADR-0004/0005/0051). LLM tools and humans both author boolean CEL for
alerts, bindings, and platform context rules. Compile-only validation misses logical
failures (dead conditions, tautologies) and cannot prove safe refactors.

CEL-Java 0.14 ships `dev.cel:verifier` (Z3 via `z3-turnkey`).

## Decision

### Product surface

1. **Library** (`ispf-expression`): `ExpressionFormalVerifier` + `FormalVerificationReport`
   with stable `codes` (`UNSATISFIABLE`, `TAUTOLOGY`, `EQUIVALENT`, …) for UI i18n / AI.
2. **Runtime service** (`ExpressionFormalVerificationService`) +
   `ispf.expression.formal-verification.*` settings (hot-reloadable via platform runtime settings):
   - `enabled` (default true)
   - `timeout-seconds` (default 2)
   - `enforce-on-apply` / `enforce-on-validate` (default true)
   - `reject-unsatisfiable` / `reject-tautology` (default true)
3. **APIs**
   - `POST /api/v1/expressions/validate` — compile + formal; returns `warnings` + `verification`
   - `POST /api/v1/expressions/verify` — formal only
   - `POST /api/v1/expressions/verify-equivalence` — prove `left ≡ right`
   - Analytics / catalog validate include `verification` (historian helpers → skipped)
4. **Enforce on apply (same bar for human REST and AI)**
   - `AlertRuleService` (`conditionExpr`, `deactivateExpr`)
   - `BindingRulesController` (rule `condition`)
   - AI: `configure_alert`, `create_binding_rule`, `configure_platform_context_rule`
5. **AI tool** `verify_cel_condition` (read-only; operator allowlist) — analyze or equivalence.
6. **Web console** surfaces formal findings on CEL validate (alerts / bindings).

### Non-goals (tracked follow-ups)

- SMT axioms for historian helpers (`avg`/`live`) — skipped until modeled
- Workflow BPMN design-time formal gate (runtime still evaluates CEL)
- CEL Policy aggregate packs

## Consequences

- Dead/tautology conditions cannot be saved or AI-applied when enforce flags are on.
- Equivalence unlocks safe AI refactors (`verify_cel_condition` + `equivalentTo`).
- Z3 natives via `tools.aqua:z3-turnkey`; soft timeout avoids hanging validate UX.
- Operators can soften or disable the gate via runtime settings without rebuild.

## Alternatives considered

| Alternative | Rejected because |
|-------------|------------------|
| Soft warnings only | AI/product still ship broken alerts |
| AI-only gate | Human REST would bypass; inconsistent product |
| Verify expanded historian CEL | Proves current samples, not the template |
| Wait for typed `self.*` schemas | DYN already catches contradictions useful in production |
