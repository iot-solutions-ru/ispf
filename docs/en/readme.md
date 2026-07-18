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

Demo path after boot: `devices.demo-sensor-01` → alert rule → `dashboards.demo-sensor` → operator mode ([JAR](http://localhost:8080?mode=operator) / [Vite](http://localhost:5173?mode=operator)).

---

## Choose your path

| I am… | Read next |
|-------|-----------|
| **Trying the product** | [Getting started](getting-started.md) → [Operator guide](operator-guide.md) |
| **Building a solution / bundle** | [Solution developer guide](solution-developer-guide.md) · [Applications](applications.md) · [Application principles](application-principles.md) |
| **Wiring OT / drivers** | [Drivers](drivers.md) · [Driver DDK](driver-ddk.md) · [Field pilot](field-pilot-playbook.md) |
| **Building HMI / SCADA** | [Dashboards](dashboards.md) · [SCADA](scada.md) · [Widgets](widgets.md) |
| **Writing expressions / CEL** | [Expression language](expression-language.md) · [Bindings](bindings.md) |
| **Automating alarms / workflows** | [Automation](automation.md) · [Workflows](workflows.md) · [OT Automation tutorials](ot-automation-excellence-tutorials.md) |
| **Using AI Studio / agent** | [AI development](ai-development.md) · [AI agent](ai-agent.md) |
| **Deploying / operating** | [Deployment](deployment.md) · [Security](security.md) · [Observability](observability.md) |
| **Contributing to the core** | [Getting started — Contribute](getting-started.md#contribute-local-dev--qa) · [Testing](testing.md) · [ADR index](decisions/readme.md) |

**Status vs commercial platforms:** [Competitive scorecard](competitive-scorecard.md) (code-verified). Long backlog: [Roadmap](roadmap.md).

**Doc status tags:** [doc-status.md](doc-status.md) — Stable · Beta · Draft · Charter · Lab · Internal.

---

## Full catalog

<details>
<summary><strong>Product</strong></summary>

| Document | Status | Description |
|----------|--------|-------------|
| [Product overview](product.md) | Stable | Capabilities, scenarios, doc map |
| [Operator guide](operator-guide.md) | Stable | HMI, work queue, events |
| [Solution developer guide](solution-developer-guide.md) | Stable | Deploy, operator UI, bundles |
| [Application principles](application-principles.md) | Stable | P1–P10 target approach |
| [Public API](solution-developer-public-api.md) | Stable | Stable platform ↔ bundle boundary |
| [Glossary](glossary.md) | Stable | Terms and definitions |
| [Web Console](web-console.md) | Stable | Explorer, System, AI Studio |

</details>

<details>
<summary><strong>Platform</strong></summary>

| Document | Status | Description |
|----------|--------|-------------|
| [Getting started](getting-started.md) | Stable | Try ISPF + contributor QA |
| [Architecture](architecture.md) | Stable | Vision, layers, extensibility |
| [Object model](object-model.md) | Stable | Tree, variables, events, functions |
| [Bindings](bindings.md) | Stable | CEL and platform bindings |
| [Expression language](expression-language.md) | Stable | Full CEL / bindings / historian function reference |
| [Platform logic](platform-logic.md) | Beta | Rules; `@dashboardContext` readiness varies |
| [Blueprints](blueprints.md) | Stable | Models / templates |
| [Variable history](variable-history.md) | Stable | Time-series, retention |
| [API](api.md) | Stable | Endpoints reference |
| [Applications](applications.md) | Stable | Bundles, BFF, scheduler |
| [Reports](reports.md) | Stable | SQL reports, CSV export |
| [Roadmap](roadmap.md) | Charter | Phases and backlog |
| [Competitive scorecard](competitive-scorecard.md) | Stable | Code-verified readiness |
| [ADR index](decisions/readme.md) | Stable | Architecture decision records |
| [Doc status tags](doc-status.md) | Stable | Status vocabulary |

</details>

<details>
<summary><strong>SCADA / HMI</strong></summary>

| Document | Status | Description |
|----------|--------|-------------|
| [SCADA overview](scada.md) | Stable | Mimics, symbols, bindings |
| [SCADA mimic reference](scada-mimic.md) | Stable | `diagramJson`, REST API |
| [Symbol library](scada-symbol-library.md) | Stable | P&ID pack (218 symbols) |
| [Widgets catalog](widgets.md) | Stable | All widget types |
| [Dashboards](dashboards.md) | Stable | Layout 84×8, `selectionKey` |
| [HMI quality gates](hmi-quality-gates.md) | Lab | Lighthouse, axe, FPS |
| [Spreadsheet widget](spreadsheet-widget.md) | Stable | Formulas and bindings |
| [Operator apps](operator-apps.md) | Stable | Operator shell configuration |

</details>

<details>
<summary><strong>OT / drivers / historian</strong></summary>

| Document | Status | Description |
|----------|--------|-------------|
| [Drivers catalog](drivers.md) | Beta | Packs; maturity honesty vs PRODUCTION matrix |
| [Driver DDK](driver-ddk.md) | Stable | Custom driver SDK |
| [Driver promotion](driver-promotion.md) | Stable | PRODUCTION + ready-for-field |
| [Field pilot playbook](field-pilot-playbook.md) | Lab | OT validation runbooks |
| [Historian tiers](historian-tiers.md) | Beta | JDBC, ClickHouse, dual-write |
| [ClickHouse prod playbook](clickhouse-prod-playbook.md) | Lab | Production rollout |
| [Cluster](cluster.md) | Beta | Multi-replica HA (capability vs demostand) |
| [Cluster chaos / soak runbook](cluster-chaos-soak-runbook.md) | Lab | Wave 6 evidence: kill-owner SLO, config sync, live-var lag |
| [Messaging](messaging.md) | Stable | NATS / MQTT notes |

</details>

<details>
<summary><strong>Analytics</strong></summary>

| Document | Status | Description |
|----------|--------|-------------|
| [Historian cookbook](analytics-historian-cookbook.md) | Stable | Recipes, binding rules, rollups |
| [Formulas and packs](analytics-formulas-and-packs.md) | Stable | Expression packs, deploy |
| [Analytics roadmap](analytics-platform-roadmap.md) | Charter | BL-200…210 charter |
| [Tag catalog API](analytics-tag-catalog.md) | Stable | Deployed analytics tags |
| [0038-analytics-platform-architecture](decisions/0038-analytics-platform-architecture.md) | Stable | Architecture ADR |
| [0042-analytics-function-catalog](decisions/0042-analytics-function-catalog.md) | Stable | Function catalog |

</details>

<details>
<summary><strong>AI / automation / MES</strong></summary>

| Document | Status | Description |
|----------|--------|-------------|
| [AI development](ai-development.md) | Beta | ContextPack, Studio; BL-178 open |
| [AI agent](ai-agent.md) | Beta | Agent API; ≥95% gate not met |
| [Agent knowledge](agent-knowledge.md) | Internal | Agent routing map |
| [Agent regression](agent-regression.md) | Lab | Scenario CI gates |
| [Automation](automation.md) | Stable | Alerts, correlators |
| [Workflows](workflows.md) | Beta | BPMN subset (not full 2.0) |
| [OT Automation tutorials](ot-automation-excellence-tutorials.md) | Beta | ADR-0049 hands-on (journal, AI-BPMN, MCP, analytics AI) |
| [MES platform reference](reference-mes-platform.md) | Beta | Marketplace MES; smoke ≠ plant |
| [MES walkthrough](reference-mes-walkthrough.md) | Lab | End-to-end MES path |

</details>

<details>
<summary><strong>Operations</strong> (deploy, labs, CI — heavier runbooks)</summary>

| Document | Status | Description |
|----------|--------|-------------|
| [Deployment](deployment.md) | Stable | Docker, env vars |
| [Demostand profiles](demostands.md) | Lab | Prod, lab, edge topologies |
| [Air-gap deployment](air-gap-deployment.md) | Stable | Offline installs |
| [Federation](federation.md) | Beta | Hub / edge (maturity caveats) |
| [Security](security.md) | Stable | RBAC, MFA |
| [Observability](observability.md) | Stable | Metrics, diagnostics |
| [Testing](testing.md) | Stable | Unit, integration |
| [Load testing](load-testing.md) | Lab | Throughput baselines |
| [Release dogfood](release-dogfood.md) | Internal | Release checklist |
| [Lab training](lab-training.md) | Lab | Training sample packs |

</details>

<details>
<summary><strong>Ecosystem & legal</strong></summary>

| Document | Status | Description |
|----------|--------|-------------|
| [Marketplace](marketplace.md) | Draft | Partial BL-183; not full GA |
| [Symbol marketplace](symbol-marketplace.md) | Stable | Local install + scada API (BL-185) |
| [Partner program](partner-program.md) | Draft | Design; in-server API stub |
| [Certification](certification.md) | Draft | Training paths / exams |
| [License](license.md) | Stable | **AGPL v3** + dual-license |
| [Commercial licensing](commercial-licensing.md) | Stable | Enterprise terms |
| [License compliance](license-compliance.md) | Stable | Obligations checklist |
| [Plugins](plugins.md) | Stable | Core vs packs vs bundles |
| [Documentation audit](documentation-audit.md) | Internal | Structure, naming, link audit |
| [Full docs audit 2026-07-16](documentation-full-audit-2026-07-16.md) | Internal | Content honesty pass |
| [Russian software registry](russian-software-registry.md) | Internal | Rights-holder / RU market |

</details>

---

## Quick links (local)

| | |
| --- | --- |
| API | http://localhost:8080/api/v1 |
| Health | http://localhost:8080/actuator/health |
| Admin (all-in-one JAR) | http://localhost:8080 |
| Operator HMI (all-in-one JAR) | http://localhost:8080?mode=operator |
| Admin (Vite dev) | http://localhost:5173 |
| Operator HMI (Vite dev) | http://localhost:5173?mode=operator |
