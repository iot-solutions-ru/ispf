# ISPF Documentation (English)

**IoT Solutions Platform Framework** — self-hosted middleware for IoT, SCADA, and industrial automation.

> Canonical language: **English**. Russian mirror: [../ru/readme.md](../ru/readme.md).  
> Website: [ispf.ai](https://ispf.ai) · Repo: [Michaael/IoT-Solutions-Platform](https://github.com/Michaael/IoT-Solutions-Platform)

**License:** platform is [GNU AGPL v3](license.md) (optional commercial dual-license). Not Apache 2.0 for the core.

---

## Start here (≈15 min)

1. [Getting started — Try ISPF](getting-started.md#try-ispf-15-minutes) — local API + Web Console  
2. [Product overview](product.md) — what the platform is for  
3. [Object model](object-model.md) — the tree (devices, dashboards, rules as nodes)  
4. [License](license.md) — AGPL / Enterprise boundary  

Demo path after boot: `devices.demo-sensor-01` → alert rule → `dashboards.demo-sensor` → [operator mode](http://localhost:5173?mode=operator).

---

## Choose your path

| I am… | Read next |
|-------|-----------|
| **Trying the product** | [Getting started](getting-started.md) → [Operator guide](operator-guide.md) |
| **Building a solution / bundle** | [Solution developer guide](solution-developer-guide.md) · [Applications](applications.md) · [Application principles](application-principles.md) |
| **Wiring OT / drivers** | [Drivers](drivers.md) · [Driver DDK](driver-ddk.md) · [Field pilot](field-pilot-playbook.md) |
| **Building HMI / SCADA** | [Dashboards](dashboards.md) · [SCADA](scada.md) · [Widgets](widgets.md) |
| **Automating alarms / workflows** | [Automation](automation.md) · [Workflows](workflows.md) |
| **Using AI Studio / agent** | [AI development](ai-development.md) · [AI agent](ai-agent.md) |
| **Deploying / operating** | [Deployment](deployment.md) · [Security](security.md) · [Observability](observability.md) |
| **Contributing to the core** | [Getting started — Contribute](getting-started.md#contribute-local-dev--qa) · [Testing](testing.md) · [ADR index](decisions/readme.md) |

**Status vs commercial platforms:** [Competitive scorecard](competitive-scorecard.md) (code-verified). Long backlog: [Roadmap](roadmap.md).

---

## Full catalog

<details>
<summary><strong>Product</strong></summary>

| Document | Audience | Description |
|----------|----------|-------------|
| [Product overview](product.md) | All | Capabilities, scenarios, doc map |
| [Operator guide](operator-guide.md) | Operator | HMI, work queue, events |
| [Solution developer guide](solution-developer-guide.md) | App developer | Deploy, operator UI, bundles |
| [Application principles](application-principles.md) | Developer, Agent | P1–P10 target approach |
| [Public API](solution-developer-public-api.md) | App developer | Stable platform ↔ bundle boundary |
| [Glossary](glossary.md) | All | Terms and definitions |
| [Web Console](web-console.md) | Admin | Explorer, System, AI Studio |

</details>

<details>
<summary><strong>Platform</strong></summary>

| Document | Description |
|----------|-------------|
| [Getting started](getting-started.md) | Try ISPF + contributor QA |
| [Architecture](architecture.md) | Vision, layers, extensibility |
| [Object model](object-model.md) | Tree, variables, events, functions |
| [Bindings](bindings.md) | CEL and platform bindings |
| [Platform logic](platform-logic.md) | Rules, dashboard context |
| [Blueprints](blueprints.md) | Models / templates |
| [Variable history](variable-history.md) | Time-series, retention |
| [API](api.md) | Endpoints reference |
| [Applications](applications.md) | Bundles, BFF, scheduler |
| [Reports](reports.md) | SQL reports, CSV export |
| [Roadmap](roadmap.md) | Phases and backlog |
| [Competitive scorecard](competitive-scorecard.md) | Code-verified readiness |
| [ADR index](decisions/readme.md) | Architecture decision records |

</details>

<details>
<summary><strong>SCADA / HMI</strong></summary>

| Document | Description |
|----------|-------------|
| [SCADA overview](scada.md) | Mimics, symbols, bindings |
| [SCADA mimic reference](scada-mimic.md) | `diagramJson`, REST API |
| [Symbol library](scada-symbol-library.md) | P&ID pack (218 symbols) |
| [Widgets catalog](widgets.md) | All widget types |
| [Dashboards](dashboards.md) | Layout, `selectionKey` |
| [HMI quality gates](hmi-quality-gates.md) | Lighthouse, axe, FPS |
| [Spreadsheet widget](spreadsheet-widget.md) | Formulas and bindings |
| [Operator apps](operator-apps.md) | Operator shell configuration |

</details>

<details>
<summary><strong>OT / drivers / historian</strong></summary>

| Document | Description |
|----------|-------------|
| [Drivers catalog](drivers.md) | Built-in drivers |
| [Driver DDK](driver-ddk.md) | Custom driver SDK |
| [Driver promotion](driver-promotion.md) | PRODUCTION matrix |
| [Field pilot playbook](field-pilot-playbook.md) | OT validation runbooks |
| [Historian tiers](historian-tiers.md) | JDBC, ClickHouse, dual-write |
| [ClickHouse prod playbook](clickhouse-prod-playbook.md) | Production rollout |
| [Cluster](cluster.md) | Multi-replica HA |
| [Messaging](messaging.md) | NATS / MQTT notes |

</details>

<details>
<summary><strong>Analytics</strong></summary>

| Document | Description |
|----------|-------------|
| [Historian cookbook](analytics-historian-cookbook.md) | Recipes, binding rules, rollups |
| [Formulas and packs](analytics-formulas-and-packs.md) | Expression packs, deploy |
| [Analytics roadmap](analytics-platform-roadmap.md) | BL-200…210 charter |
| [Tag catalog API](analytics-tag-catalog.md) | Deployed analytics tags |
| [0038-analytics-platform-architecture](decisions/0038-analytics-platform-architecture.md) | Architecture ADR |
| [0042-analytics-function-catalog](decisions/0042-analytics-function-catalog.md) | Function catalog |

</details>

<details>
<summary><strong>AI / automation / MES</strong></summary>

| Document | Description |
|----------|-------------|
| [AI development](ai-development.md) | ContextPack, tools, Studio |
| [AI agent](ai-agent.md) | Agent API and metrics |
| [Agent knowledge](agent-knowledge.md) | Internal agent routing map |
| [Agent regression](agent-regression.md) | Scenario CI gates |
| [Automation](automation.md) | Alerts, correlators |
| [Workflows](workflows.md) | BPMN engine |
| [MES platform reference](reference-mes-platform.md) | ISA-95 bundles |
| [MES walkthrough](reference-mes-walkthrough.md) | End-to-end MES path |

</details>

<details>
<summary><strong>Operations</strong> (deploy, labs, CI — heavier runbooks)</summary>

| Document | Description |
|----------|-------------|
| [Deployment](deployment.md) | Docker, env vars |
| [Demostand profiles](demostands.md) | Prod, lab, edge topologies |
| [Air-gap deployment](air-gap-deployment.md) | Offline installs |
| [Federation](federation.md) | Hub / edge peers |
| [Security](security.md) | RBAC, MFA |
| [Observability](observability.md) | Metrics, diagnostics |
| [Testing](testing.md) | Unit, integration |
| [Load testing](load-testing.md) | Throughput baselines |
| [Release dogfood](release-dogfood.md) | Release checklist |
| [Lab training](lab-training.md) | Training lab packs |

</details>

<details>
<summary><strong>Ecosystem & legal</strong></summary>

| Document | Description |
|----------|-------------|
| [Marketplace](marketplace.md) | Catalog and install |
| [Symbol marketplace](symbol-marketplace.md) | Symbol pack distribution |
| [Partner program](partner-program.md) | Integrator tiers |
| [Certification](certification.md) | Training paths |
| [License](license.md) | **AGPL v3** platform + dual-license |
| [Commercial licensing](commercial-licensing.md) | Enterprise terms |
| [License compliance](license-compliance.md) | Obligations checklist |
| [Plugins](plugins.md) | Core vs packs vs bundles |
| [Documentation audit](documentation-audit.md) | Structure, naming, link audit |
| [Russian software registry](russian-software-registry.md) | RU registry process (optional market) |

</details>

---

## Quick links (local)

| | |
| --- | --- |
| API | http://localhost:8080/api/v1 |
| Health | http://localhost:8080/actuator/health |
| Admin console | http://localhost:5173 |
| Operator HMI | http://localhost:5173?mode=operator |
