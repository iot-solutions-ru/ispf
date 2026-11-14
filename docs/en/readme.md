# ISPF Documentation

**ISPF (IoT Solutions Platform Framework)** is a self-hosted server platform for industrial automation, SCADA, IoT monitoring, and shop-floor (MES) applications. It connects field equipment and IT data sources, calculates derived tags, tracks alarms, stores time-series history, serves operator screens, and runs multi-step workflows in a single system.

> Russian edition: **[../ru/readme.md](../ru/readme.md)**  
> Website: [ispf.ai](https://ispf.ai) · Live demo: [demo.iot-solutions.ru](https://demo.iot-solutions.ru/) · Repository: [iot-solutions-ru/ispf](https://github.com/iot-solutions-ru/ispf) · Wiki: [ispf/wiki](https://github.com/iot-solutions-ru/ispf/wiki)

**License:** GNU AGPL v3 with an optional commercial license ([license.md](license.md)).

---

## Start here (first 30 minutes)

If you are opening ISPF for the first time, read these four pages in order:

1. **[How ISPF works & learning guide](learn.md)** — plain-language mental model, core concepts explained on one concrete example, and step-by-step learning tracks.
2. **[Getting started](getting-started.md#try-ispf-15-minutes)** — start ISPF locally in 15 minutes (portable archive, Docker, or source) and click through the built-in sensor, alarm, and dashboard.
3. **[Object model](object-model.md)** — how devices, variables, events, functions, alarms, and screens are organized into one object tree (`root.platform.*`).
4. **[Common design mistakes](anti-patterns.md)** — practical engineering rules that prevent performance bottlenecks and unmaintainable projects.

---

## Guides by engineering task

| What you want to do | Where to read |
|---------------------|---------------|
| **Understand what ISPF does and who uses it** | [Product overview](product.md) · [Glossary](glossary.md) |
| **Operate screens, alarms, and shift tasks** | [Operator guide](operator-guide.md) · [Web Console](web-console.md) |
| **Connect PLCs, sensors, brokers, or databases** | [Device drivers](drivers.md) · [Custom driver SDK (DDK)](driver-ddk.md) · [Field pilot runbook](field-pilot-playbook.md) |
| **Write formulas and cross-object calculations** | [Calculation rules (bindings)](bindings.md) · [Expression language (CEL & functions)](expression-language.md) |
| **Build operator dashboards and P&ID mnemonics** | [Dashboards](dashboards.md) · [SCADA mnemonics](scada.md) · [Widgets catalog](widgets.md) |
| **Configure alarms and BPMN workflows** | [Alarms, events, and correlators](automation.md) · [BPMN workflows](workflows.md) |
| **Store trends and compute shift/hourly aggregates** | [Variable history](variable-history.md) · [Historian storage tiers](historian-tiers.md) · [Historian cookbook](analytics-historian-cookbook.md) |
| **Build and package a complete application** | [Solution developer guide](solution-developer-guide.md) · [Application architecture rules](application-principles.md) · [Applications API](applications.md) |
| **Build a custom React web UI for operators** | [Custom React UI (external IDE & UI packs)](external-ide.md) |
| **Deploy to a server, cluster, or offline site** | [Deployment](deployment.md) · [Air-gap (offline) install](air-gap-deployment.md) · [Security & RBAC](security.md) · [Observability](observability.md) |
| **Contribute to the platform core** | [Local dev & QA](getting-started.md#contribute-local-dev--qa) · [Testing](testing.md) · [Architecture](architecture.md) · [ADRs](decisions/readme.md) |

Document maturity labels (`Stable`, `Beta`, `Lab`, `Charter`, `Internal`) are explained in [doc-status.md](doc-status.md). Release notes: [CHANGELOG.md](../../CHANGELOG.md).

---

## Full documentation catalog

<details>
<summary><strong>Overview, roles, and architecture</strong></summary>

| Document | Status | Contents |
|----------|--------|----------|
| [Learning guide](learn.md) | Stable | Mental model, first-hour walkthrough, first-project plan, curriculum |
| [Getting started](getting-started.md) | Stable | Local installation, first login, demo sensor, contributor QA |
| [Product overview](product.md) | Stable | Capabilities, typical scenarios, user roles |
| [Architecture](architecture.md) | Stable | Server components, runtime layers, package layout |
| [Object model](object-model.md) | Stable | Object tree paths, typed variables (`DataRecord`), events, functions |
| [Blueprints (templates)](blueprints.md) | Stable | Reusable object templates (`SINGLETON`, `INSTANCE`, `MIXIN`) |
| [Glossary](glossary.md) | Stable | Plain-language definitions of platform terms |
| [Web Console](web-console.md) | Stable | Admin console, Explorer, Inspector, and Operator mode |

</details>

<details>
<summary><strong>Building applications and solutions</strong></summary>

| Document | Status | Contents |
|----------|--------|----------|
| [Solution developer guide](solution-developer-guide.md) | Stable | Registering an app, SQL tables, script functions, deploying `bundle.json` |
| [Application architecture rules](application-principles.md) | Stable | How to structure devices, logic objects, screens, SQL, and deployments |
| [Common design mistakes](anti-patterns.md) | Stable | Typical pitfalls in SCADA/IoT projects and how ISPF prevents them |
| [Custom React UI (external IDE)](external-ide.md) | Stable | Developing standalone React SPAs (`ui-pack`) against ISPF `POST /api/v1/bff/invoke` |
| [Applications API](applications.md) | Stable | Reference for bundle deploy, SQL migrations, BFF gateway, schedules, Git sync |
| [Object functions](object-functions.md) | Stable | Built-in, JSON script, and Java functions on tree objects |
| [Public bundle contract](solution-developer-public-api.md) | Stable | Supported fields and compatibility boundaries for `bundle.json` |
| [Reports](reports.md) | Stable | SQL reports and CSV/document export |

</details>

<details>
<summary><strong>Calculations, alarms, and workflows</strong></summary>

| Document | Status | Contents |
|----------|--------|----------|
| [Calculation rules (bindings)](bindings.md) | Stable | Reactive and periodic variable rules, execution order, cross-object reads/writes |
| [Expression language](expression-language.md) | Stable | Google CEL syntax, built-in signal processing functions, historian functions |
| [Platform rules](platform-logic.md) | Beta | Unified `WHEN → IF → THEN` rule model and dashboard context |
| [Alarms, events, and correlators](automation.md) | Stable | Threshold alerts, hysteresis/latching, event journal, event correlation |
| [BPMN workflows](workflows.md) | Beta | Supported BPMN 2.0 elements, operator tasks, service tasks, timers |
| [Automation tutorials](ot-automation-excellence-tutorials.md) | Beta | Step-by-step exercises for workflows, MCP tools, and analytics |

</details>

<details>
<summary><strong>Dashboards, SCADA mnemonics, and operator UI</strong></summary>

| Document | Status | Contents |
|----------|--------|----------|
| [Operator guide](operator-guide.md) | Stable | Working in Operator mode: screens, alarms, work queue, spreadsheets |
| [Dashboards](dashboards.md) | Stable | 84×8 layout grid, static paths (`objectPath`), dynamic row selection (`selectionKey`) |
| [Widgets catalog](widgets.md) | Stable | Configuration reference for all dashboard widgets |
| [SCADA overview](scada.md) | Stable | Interactive P&ID / single-line mnemonics, symbol bindings, equipment commands |
| [SCADA mimic format](scada-mimic.md) | Stable | `diagramJson` structure and REST endpoints |
| [Symbol library](scada-symbol-library.md) | Stable | Built-in ISA / P&ID symbol catalog (218 SVG symbols) |
| [Spreadsheet widget](spreadsheet-widget.md) | Stable | Excel-style cells, formulas, and live tag bindings |
| [Operator apps](operator-apps.md) | Stable | Navigation menus and startup screens for operators |
| [HMI quality gates](hmi-quality-gates.md) | Lab | Accessibility, rendering performance, and Lighthouse checks |

</details>

<details>
<summary><strong>Equipment drivers, historian, and analytics</strong></summary>

| Document | Status | Contents |
|----------|--------|----------|
| [Device drivers](drivers.md) | Beta | Protocol catalog (Modbus, OPC UA, S7, MQTT, SNMP, IEC-104, DNP3, …) and readiness levels |
| [Driver DDK](driver-ddk.md) | Stable | Writing and testing a custom protocol driver in Java |
| [Driver promotion](driver-promotion.md) | Stable | Criteria for moving a driver from lab tests to field readiness |
| [Driver interop lab](driver-interop-lab.md) | Lab | Docker testbed with protocol emulators and brokers |
| [Field pilot playbook](field-pilot-playbook.md) | Lab | Checklist for testing drivers on real plant equipment |
| [Variable history](variable-history.md) | Stable | Recording time-series samples, retention, export, and aggregation API |
| [Historian storage tiers](historian-tiers.md) | Beta | PostgreSQL/Timescale (hot), ClickHouse (warm), and Parquet archive (cold) |
| [ClickHouse production guide](clickhouse-prod-playbook.md) | Lab | Deploying and verifying ClickHouse for high-rate telemetry |
| [Historian cookbook](analytics-historian-cookbook.md) | Stable | Practical recipes: rolling averages, OEE, multi-tag calculations, rollups |
| [Formulas and analytics packs](analytics-formulas-and-packs.md) | Stable | Reusable formula templates and Java extension packs |
| [Analytics tag catalog](analytics-tag-catalog.md) | Stable | Querying deployed calculation tags via REST |
| [Messaging (MQTT & NATS)](messaging.md) | Stable | Broker configuration and message routing |

</details>

<details>
<summary><strong>MES, digital twins, and AI assistant</strong></summary>

| Document | Status | Contents |
|----------|--------|----------|
| [MES operator guide](mes.md) | Stable | Production orders, batches, genealogy, and shift screens |
| [MES reference solution](reference-mes-platform.md) | Beta | Structure of the `mes-platform` reference bundle |
| [MES end-to-end walkthrough](reference-mes-walkthrough.md) | Lab | Running a complete work-order scenario |
| [Manufacturing patterns](manufacturing-patterns.md) | Charter | Designing traceability, BoM, quality checks, and ERP exchange as bundles |
| [AI Studio & MCP](ai-development.md) | Beta | Using the built-in AI assistant and connecting external IDEs via MCP |
| [AI agent API](ai-agent.md) | Beta | REST endpoints, approval flow, and execution modes for the AI agent |
| [Agent routing reference](agent-knowledge.md) | Internal | Internal reference used by the AI ContextPack |

</details>

<details>
<summary><strong>Deployment, security, cluster, and operations</strong></summary>

| Document | Status | Contents |
|----------|--------|----------|
| [Deployment](deployment.md) | Stable | Docker Compose, environment variables, Spring profiles, database setup |
| [Air-gap deployment](air-gap-deployment.md) | Stable | Installing and updating ISPF without internet access |
| [Security & access control](security.md) | Stable | Authentication, OAuth2/Keycloak, TOTP MFA, roles, object/variable permissions |
| [Multi-tenancy](multi-tenant.md) | Stable | Isolating multiple organizations or sites |
| [Observability](observability.md) | Stable | Health checks, Prometheus metrics, logs, and diagnostics |
| [Cluster (HA)](cluster.md) | Beta | Multi-node active-active setup, driver ownership, failover |
| [Federation](federation.md) | Beta | Connecting edge ISPF instances to a central hub |
| [Release & upgrade](release-upgrade.md) | Stable | Upgrading servers and checking bundle compatibility |
| [Testing](testing.md) | Stable | Running unit, integration, and fast pre-PR checks |
| [Load testing](load-testing.md) | Lab | Telemetry ingestion and historian throughput benchmarks |
| [Demostand profiles](demostands.md) | Lab | Standard configurations for demo and lab servers |
| [Customer demo guide](customer-demo-playbook.md) | Lab | Script for a 30–60 minute live product demonstration |

</details>

<details>
<summary><strong>Licensing, marketplace, and project records</strong></summary>

| Document | Status | Contents |
|----------|--------|----------|
| [License](license.md) | Stable | GNU AGPL v3 and commercial dual-licensing overview |
| [Commercial licensing](commercial-licensing.md) | Stable | Enterprise license terms and signed artifacts |
| [License compliance](license-compliance.md) | Stable | Third-party dependencies and compliance checks |
| [Plugins & extension boundaries](plugins.md) | Stable | How core, driver packs, and application bundles relate |
| [Marketplace](marketplace.md) | Stable | Installing and publishing application bundles and UI packs |
| [Symbol marketplace](symbol-marketplace.md) | Stable | Installing additional SVG symbol libraries for SCADA |
| [Certification](certification.md) | Stable | Training tracks and exam topics for engineers and administrators |
| [Competitive scorecard](competitive-scorecard.md) | Stable | Code-verified comparison with commercial SCADA/IIoT/MES platforms |
| [Roadmap](roadmap.md) | Charter | Engineering backlog and historical phases |
| [Architecture decisions (ADRs)](decisions/readme.md) | Stable | Index of Architecture Decision Records |
| [Doc status tags](doc-status.md) | Stable | Definitions of document maturity tags |

</details>

---

## Default local URLs

| Interface | URL |
|-----------|-----|
| REST API | `http://localhost:8080/api/v1` |
| Health check | `http://localhost:8080/actuator/health` |
| Admin Console (portable / JAR / Docker) | `http://localhost:8080` |
| Operator screens (portable / JAR / Docker) | `http://localhost:8080?mode=operator` |
| Admin Console (Vite frontend dev server) | `http://localhost:5173` |
| Operator screens (Vite frontend dev server) | `http://localhost:5173?mode=operator` |
