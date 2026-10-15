# ADR-0072: Edge fleet rollout contract

## Status

**Accepted** (2026-10-13) — separates per-app Git ([0063](0063-application-git-lifecycle.md)), federation store-and-forward, and true fleet GitOps.

## Context

ARM edge compose ([BL-187](../demostands.md)), federation S&F / disk buffer (BL-117 / BL-145), and Application Git push→PR (0063) are easy to conflate into “fleet GitOps Done”.

## Decision

### 1. Three lanes

| Lane | Meaning | Status today |
|------|---------|--------------|
| **A — Edge runtime profile** | ARM/gateway compose + validate | **Done** (lab) |
| **B — Store-and-forward** | Buffer telemetry/events toward hub when link down | **Lab Done** (code + metrics + soak journal script); **field** 30d soak still open |
| **C — Fleet rollout** | Same bundle revision to N sites with canary/rollback | **Not claimed** — 0063 is **one app ↔ one Git repo**, not N-site GitOps |

### 2. Hard rules

1. Do not call Application Git “fleet GitOps”.
2. S&F Done ≠ customer-plant FIELD ([field-pilot-playbook](../field-pilot-playbook.md)).
3. Fleet C requires: inventory of sites, signed bundle, staged canary, rollback note — as a future ADR amendment or ops product, not silent scope creep.

## Consequences

- [federation](../federation.md), [demostands](../demostands.md), [0063](0063-application-git-lifecycle.md).
- Follow-up: BL-145 **lab** soak journal shipped (`deploy/tools/agent-store-forward-soak.sh`); dated **field** 30d host run still open; optional fleet controller remains unscheduled.

## Related

- [0063-application-git-lifecycle](0063-application-git-lifecycle.md), [federation](../federation.md)
