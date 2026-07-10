# Architecture Decision Records (ADR)

English canonical ADRs for ISPF. Russian mirror: [../../ru/decisions/readme.md](../../ru/decisions/readme.md).

## Writing style

ADRs are engineering records, not marketing copy. Prefer:

| Use | Avoid |
|-----|--------|
| Status, Context, Decision, Consequences, Related | **Positive** / **Negative / risks** subsections |
| Concrete APIs, env vars, package names | «moat», «target approach», «best-in-class», «PI-like», «user request» |
| «Goal:» or problem statement in Context | «Goal:» |
| `Risks:` bullet list under Consequences | Hype scores (10/10) inside ADRs |

Run [`tools/docs-audit/strip-neuro-slang.py`](../../../tools/docs-audit/strip-neuro-slang.py) after bulk edits to catch regressions.

## Index (selected)

| ID | Title |
|----|-------|
| [0001](0001-app-platform-boundary.md) | App vs platform boundary |
| [0002](0002-dogfooding-gate.md) | Dogfooding gate |
| [0005](0005-tree-first-ai-agent.md) | Tree-first AI agent |
| [0020](0020-time-and-timezones.md) | Time and timezones |
| [0022](0022-driver-production-matrix.md) | Driver production matrix |
| [0027](0027-event-journal-ingress-fast-path.md) | Event journal ingress |
| [0037](0037-relational-core-portability.md) | Relational core portability |
| [0038](0038-analytics-platform-architecture.md) | Analytics platform (AF-capable) |
| [0039](0039-unified-alarm-architecture.md) | Alert rule evolution (same `alert-rule-v1`) |
| [0040](0040-unified-computations-ui.md) | Unified Computations tab |
| [0041](0041-multi-tag-historian-computations.md) | Historian binding rules (multi-tag) |
| [0042](0042-analytics-function-catalog.md) | Analytics function catalog |

Full list: all `NNNN-*.md` files in this directory.
