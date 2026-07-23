# ADR-0045: Java function sandbox (phase 1)

## Status

**Accepted** (2026-07-16)

## Context

Object Java functions compile with `javax.tools` and run **in-process** via `ObjectJavaFunction`. Security was a source-string regex denylist only — enough to block obvious RCE primitives, not a JVM sandbox. Multi-tenant / production hosts need a kill-switch and a clearer hardening roadmap.

## Decision

### 1. Kill-switch

- Property: `ispf.function.java.enabled` (`ISPF_FUNCTION_JAVA_ENABLED`)
- Default: `false` in every profile (local/test included) — enable explicitly on trusted nodes
- When disabled: no compile-on-save, no startup warm-up, `JavaFunctionHandler.supports` returns false, invoke/compile throw a clear error

### 2. Stronger denylist (phase 1)

Extend `JavaFunctionSecurity` patterns (ProcessHandle, URLClassLoader, MethodHandles/VarHandle, jdk.internal, Thread start, JNI/naming/Robot, etc.). Still **not** AST verification or bytecode policy.

### 3. Out of scope (future spike)

Process / isolate execution, SecurityManager, or AST-based allowlists. Document as follow-up; do not block local dogfooding.

## Consequences

- All profiles refuse new Java function deploy unless operators explicitly set `ISPF_FUNCTION_JAVA_ENABLED=true` on trusted nodes. Startup logs a WARN while in-process Java functions are enabled.
- Existing CEL / expression / object-query functions are unaffected.
- Risks: denylist remains bypassable with creative source; treat Java functions as trusted-author-only even when enabled.

## Related

- [`JavaFunctionSecurity`](../../../packages/ispf-server/src/main/java/com/ispf/server/function/java/JavaFunctionSecurity.java)
- [`JavaFunctionRuntimeService`](../../../packages/ispf-server/src/main/java/com/ispf/server/function/java/JavaFunctionRuntimeService.java)
- [object-functions](../object-functions.md)
