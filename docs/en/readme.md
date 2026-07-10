# ISPF Documentation (English)

**IoT Solutions Platform Framework** — middleware for IoT, SCADA, and industrial automation.

> Canonical language: **English**. Russian mirror: [../ru/readme.md](../ru/readme.md).

## Product

| Document | Audience | Description |
|----------|----------|-------------|
| [Product overview](product.md) | All | Capabilities, scenarios, doc map |
| [Operator guide](operator-guide.md) | Operator | HMI, work queue, events |
| [Solution developer guide](solution-developer-guide.md) | App developer | Deploy, operator UI, bundles |
| [Application principles](application-principles.md) | Developer, Agent | P1–P10 target approach |
| [Public API](solution-developer-public-api.md) | App developer | Stable platform ↔ bundle boundary |
| [Glossary](glossary.md) | All | Terms and definitions |

## Platform

| Document | Description |
|----------|-------------|
| [Getting started](getting-started.md) | Install, profiles, first run |
| [Architecture](architecture.md) | Vision, layers, extensibility |
| [Object model](object-model.md) | Tree, variables, events, functions |
| [Bindings](bindings.md) | CEL and platform bindings |
| [Platform logic](platform-logic.md) | Rules, dashboard context |
| [Variable history](variable-history.md) | Time-series, retention |
| [REST API](api.md) | Endpoints reference |
| [Applications](applications.md) | Bundles, BFF, scheduler |
| [Reports](reports.md) | SQL reports, CSV export |
| [Roadmap](roadmap.md) | Single growing roadmap: Phase 0–33, BL-01…210, Phases 25–33 |
| [Competitive scorecard](competitive-scorecard.md) | Code-verified readiness matrix |
| [ADR index](decisions/readme.md) | Architecture decision records |

## SCADA / HMI

| Document | Description |
|----------|-------------|
| [SCADA overview](scada.md) | Mimics, symbols, bindings |
| [SCADA mimic reference](scada-mimic.md) | `diagramJson`, REST API |
| [Symbol library](scada-symbol-library.md) | P&ID pack (218 symbols) |
| [Widgets catalog](widgets.md) | All widget types |
| [Dashboards](dashboards.md) | Layout, `selectionKey` |
| [HMI quality gates](hmi-quality-gates.md) | Lighthouse, axe, FPS |
| [Spreadsheet widget](spreadsheet-widget.md) | Formulas and bindings |

## OT / drivers / historian

| Document | Description |
|----------|-------------|
| [Drivers catalog](drivers.md) | Built-in drivers |
| [Driver DDK](driver-ddk.md) | Custom driver SDK |
| [Driver promotion](driver-promotion.md) | PRODUCTION matrix |
| [Field pilot playbook](field-pilot-playbook.md) | OT validation runbooks |
| [Historian tiers](historian-tiers.md) | JDBC, ClickHouse, dual-write |
| [ClickHouse prod playbook](clickhouse-prod-playbook.md) | Production rollout |

## AI / automation / MES

| Document | Description |
|----------|-------------|
| [AI development](ai-development.md) | ContextPack, tools, Studio |
| [AI agent](ai-agent.md) | Agent API and metrics |
| [Agent knowledge](agent-knowledge.md) | Internal agent routing map |
| [Agent regression](agent-regression.md) | Scenario CI gates |
| [Automation](automation.md) | Alerts, correlators |
| [Workflows](workflows.md) | BPMN engine |
| [MES platform reference](reference-mes-platform.md) | ISA-95 bundles |

## Operations

| Document | Description |
|----------|-------------|
| [Deployment](deployment.md) | Docker, env vars |
| [Demostand profiles](demostands.md) | Prod, lab, edge topologies |
| [Cluster](cluster.md) | Multi-replica |
| [Federation](federation.md) | Hub / edge peers |
| [Security](security.md) | RBAC, MFA |
| [Observability](observability.md) | Metrics, diagnostics |
| [Testing](testing.md) | Unit, integration |
| [Load testing](load-testing.md) | Throughput baselines |

## Ecosystem

| Document | Description |
|----------|-------------|
| [Marketplace](marketplace.md) | Catalog and install |
| [Symbol marketplace](symbol-marketplace.md) | Symbol pack distribution |
| [Partner program](partner-program.md) | Integrator tiers |
| [Certification](certification.md) | Training paths |
| [License](license.md) | Apache 2.0 core |
| [Documentation audit](documentation-audit.md) | Structure, naming, link audit |

## Quick links

- API: `http://localhost:8080/api/v1`
- Web Console: `http://localhost:5173` (dev)
- Operator HMI: `http://localhost:5173?mode=operator`
