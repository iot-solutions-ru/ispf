# ADR-0045: Java function sandbox (phase 1)

## Status

**Accepted** (2026-07-16)

## Context

Object Java functions compile with `javax.tools` and run **in-process** via `ObjectJavaFunction`. Security was a source-string regex denylist only — enough to block obvious RCE primitives, not a JVM sandbox. Multi-tenant / production hosts need a kill-switch and a clearer hardening roadmap.

Authorship model: only admins create/edit Java function source; other roles invoke already-deployed functions. Treat authors as trusted; the kill-switch is for production / shared hosts, not for day-to-day local dogfooding.

## Decision

### 1. Kill-switch

- Property: `ispf.function.java.enabled` (`ISPF_FUNCTION_JAVA_ENABLED`)
- Default: `true` (local/test/dev)
- `application-prod.yml`: default `false`
- When disabled: no compile-on-save, no startup warm-up, `JavaFunctionHandler.supports` returns false, invoke/compile throw a clear error
- When enabled under the `prod` profile: startup logs a WARN (explicit opt-in on a production host)

### 2. Stronger denylist (phase 1)

Extend `JavaFunctionSecurity` patterns (ProcessHandle, URLClassLoader, MethodHandles/VarHandle, jdk.internal, Thread start, JNI/naming/Robot, etc.). Still **not** AST verification or bytecode policy.

### 3. Out of scope (future spike)

Process / isolate execution, SecurityManager, or AST-based allowlists. Document as follow-up; do not block local dogfooding.

## Consequences

- Prod profiles refuse new Java function deploy unless operators explicitly set `ISPF_FUNCTION_JAVA_ENABLED=true` on trusted nodes.
- Existing CEL / expression / object-query functions are unaffected.
- Risks: denylist remains bypassable with creative source; treat Java functions as trusted-author-only even when enabled.

## Related

- [`JavaFunctionSecurity`](../../../packages/ispf-server/src/main/java/com/ispf/server/function/java/JavaFunctionSecurity.java)
- [`JavaFunctionRuntimeService`](../../../packages/ispf-server/src/main/java/com/ispf/server/function/java/JavaFunctionRuntimeService.java)
- [object-functions](../object-functions.md)
