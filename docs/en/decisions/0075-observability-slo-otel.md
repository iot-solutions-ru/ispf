# ADR-0075: Observability SLO and OpenTelemetry taxonomy

## Status

**Accepted** (2026-10-13) — first-party metrics remain primary; OTLP is optional export with a named span taxonomy.

## Context

[observability](../observability.md) already ships System → Metrics, optional OTLP metrics/traces, and historian/analytics SLO properties. Missing is a **product claim** for end-to-end “sample → alarm → workflow” tracing and published SLO budgets.

## Decision

### 1. Layers

| Layer | Role | Status |
|-------|------|--------|
| First-party Metrics UI / probe | Default for air-gap | **Done** |
| OTLP metrics export | Optional | **Done** (off by default) |
| OTLP traces (automation observations) | Optional | **Partial** — pipeline spans exist; not full driver→WS path |
| Published SLO budgets per profile | Site M / L | **Partial** — historian lab evidence; not a full product SLO catalog |

### 2. Span taxonomy (minimal)

When tracing is enabled, prefer these names (stable):

- `ispf.driver.poll` / `ispf.driver.write`
- `ispf.variable.set`
- `ispf.binding.evaluate`
- `ispf.alert.evaluate`
- `ispf.workflow.step`
- `ispf.object-change.handler` (already used)

Golden lab path: enable OTLP → poll virtual DEVICE → binding → alert → one workflow step → collector shows linked trace. Evidence under `docs/evidence/observability/` when captured.

### 3. Hard rules

1. Do not require Prometheus/Grafana for ISPF to be “observable”.
2. Do not claim e2e SLO compliance without a dated lab journal naming the profile.

## Consequences

- Docs: [observability](../observability.md).
- Follow-up: golden-path IT or demostand script + evidence note.

## Related

- [observability](../observability.md), [0034-agent-observability-and-session-knowledge](0034-agent-observability-and-session-knowledge.md)
