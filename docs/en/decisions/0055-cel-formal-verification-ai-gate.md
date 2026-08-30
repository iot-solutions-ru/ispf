# ADR-0055: CEL formal verification as AI condition gate

## Status

Accepted (2026-08-30)

## Context

ISPF is positioned as an **AI-first** OT platform (ADR-0004/0005). LLM tools already
create alert conditions, binding conditions, and analytics CEL. Compile-only validation
catches syntax errors but not logical dead ends (`temp > 100 && temp < 50`) or tautologies
(`true || …`) that would spam operators or never fire.

CEL-Java 0.14 ships `dev.cel:verifier` (Z3 via `z3-turnkey`) with satisfiability,
validity, and equivalence proofs.

## Decision

1. Add `dev.cel:verifier:0.14.0` to `ispf-expression`.
2. Expose `ExpressionFormalVerifier` / `FormalVerificationReport` for boolean CEL with
   ISPF roots (`self`/`parent`/`context`/`input` as `dyn`).
3. **Hard gate** on AI apply paths for boolean conditions:
   - `configure_alert.conditionExpr`
   - `create_binding_rule.condition`
   - `POST /api/v1/expressions/validate` (reactive)
   - analytics/catalog validate when expression is pure CEL boolean (no historian helpers)
4. Reject **unsatisfiable** and **always-true** conditions (`status=failed`).
5. Historian helpers (`avg`/`live`/…) are **skipped** for SMT until modeled — expanded
   literals would only reflect the current sample, not the expression template.

## Consequences

- AI agents receive structured `verification` findings and cannot apply dead/tautology alerts.
- Extra ~2s soft timeout per verify; Z3 natives come from `tools.aqua:z3-turnkey`.
- Equivalence checks and policy aggregate remain follow-ups (refactor safety, multi-hit packs).

## Alternatives considered

| Alternative | Rejected because |
|-------------|------------------|
| Soft warnings only | AI would still deploy broken alerts |
| Verify expanded historian CEL | Misleading — proves current samples, not the template |
| Wait for typed `self.*` schemas | DYN already catches contradictions useful for AI MVP |
