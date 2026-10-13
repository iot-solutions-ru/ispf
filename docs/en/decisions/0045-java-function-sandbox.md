# ADR-0045: Java function sandbox (phase 1)

## Status

**Accepted** (2026-07-16) — phase **1** only: kill-switch + denylist. **Not** process isolation.

## Context

Object Java functions compile with `javax.tools` and run **in-process** via `ObjectJavaFunction`. Security was a source-string regex denylist only — enough to block obvious RCE primitives, not a JVM sandbox. Multi-tenant / marketplace / SaaS hosts need a clear honesty bar and a hardening roadmap.

Authorship model: only admins create/edit Java function source; other roles invoke already-deployed functions. Treat authors as trusted on single-tenant prod; shared hosts must keep the kill-switch off or move to a later isolation phase.

## Decision

### 1. Kill-switch (Done)

- Property: `ispf.function.java.enabled` (`ISPF_FUNCTION_JAVA_ENABLED`)
- Default: `true` (local/test/dev)
- `application-prod.yml`: default `false`
- When disabled: no compile-on-save, no startup warm-up, `JavaFunctionHandler.supports` returns false, invoke/compile throw a clear error
- When enabled under the `prod` profile: startup logs a WARN (explicit opt-in on a production host)

### 2. Stronger denylist (phase 1 — Done, residual risk)

Extend `JavaFunctionSecurity` patterns (ProcessHandle, URLClassLoader, MethodHandles/VarHandle, jdk.internal, Thread start, JNI/naming/Robot, etc.). Still **not** AST verification or bytecode policy.

### 3. Out of scope until a named tender (Gap)

| Hardening | Status |
|-----------|--------|
| Separate process / isolate / container per function | **Gap** |
| SecurityManager / module layer policy | **Gap** |
| CPU / memory / wall-time quotas | **Gap** |
| Marketplace-safe untrusted author model | **Gap** — do not enable Java functions for untrusted packs |

### Honesty

| Claim | Status |
|-------|--------|
| Prod default off + WARN on opt-in | **Done** |
| Regex denylist phase 1 | **Done** (bypassable — trusted authors only) |
| SaaS / multi-tenant sandbox | **Gap** — keep `ISPF_FUNCTION_JAVA_ENABLED=false` |
| CEL / expression / object-query functions | Unaffected |

Do **not** claim “sandboxed Java marketplace functions” from phase 1.

## Consequences

- Prod profiles refuse new Java function deploy unless operators explicitly set `ISPF_FUNCTION_JAVA_ENABLED=true` on trusted nodes.
- Existing CEL / expression / object-query functions are unaffected.
- Risks: denylist remains bypassable with creative source; treat Java functions as trusted-author-only even when enabled.
- Follow-up ADR or amend when process isolation ships (fitness: ArchUnit/isolation test + quota evidence).

## Related

- [`JavaFunctionSecurity`](../../../packages/ispf-server/src/main/java/com/ispf/server/function/java/JavaFunctionSecurity.java)
- [`JavaFunctionRuntimeService`](../../../packages/ispf-server/src/main/java/com/ispf/server/function/java/JavaFunctionRuntimeService.java)
- [object-functions](../object-functions.md)
- [0051-poka-yoke-constraints-over-guards](0051-poka-yoke-constraints-over-guards.md) — prefer constraints over soft guards
