# ADR-0002: Dogfooding gate for platform changes

## Status

Accepted (2026-06-19; formalized 2026-06-22)

## Context

Application team needs often lead to industry-specific code in the platform repo. A mandatory filter is required: extend the object tree mechanism, do not add bespoke Java.

## Decision

Platform evolution follows **dogfooding** with a gate before every PR to `main`:

```text
App team need → REQ-PF (generic) → PR to platform → bundle deploy → smoke
```

### Generalization gate (all three must be "yes")

| # | Question | If "no" |
|---|----------|---------|
| 1 | Can the need be expressed through the **object tree mechanism** (or a generalized REQ-PF) without industry-specific names in Java? | Keep it in solution declarative configuration |
| 2 | Does the app team use only **deploy REST**, without forking the server? | Extend the API |
| 3 | Is there a **second** scenario on the same API? | Reframe the abstraction |

Criterion for a new REQ-PF: *can the need be expressed through an existing or generalized object tree mechanism?* If yes — extend the mechanism; if no — gate 0002 (separate ADR or decline).

## Consequences

- Platform developer backlog is the single REQ-PF registry.
- Reference bundles (`warehouse-app`, `lab-training`, `mes-reference`) prove dogfooding; they are not server code.

## Related

- [roadmap.md](../roadmap.md)
- [0001](0001-app-platform-boundary.md)
