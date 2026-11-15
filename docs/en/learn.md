> **Language:** Canonical English. Russian edition: [ru/learn.md](../ru/learn.md).

# How ISPF works — engineer's starting guide

> **Status:** Stable — Learning hub. Status tags: [doc-status.md](doc-status.md).

This page explains how ISPF is put together in plain engineering terms, defines the handful of platform concepts you will see across the docs, and gives a step-by-step path from your first local run to shipping a complete project.

---

## 1. The mental model in 3 minutes (on one concrete example)

Suppose you are automating a **pumping station** with two Modbus pressure/temperature sensors, an automatic high-temperature alarm, a shift operator screen, and an archive of readings.

In many setups, you stitch together separate tools: a protocol gateway, a time-series database, an alarm engine, a dashboard tool, and a backend service. In ISPF, all of those pieces live as **nodes in one hierarchical object tree** (`root.platform.*`) on one server:

```text
root.platform
├── devices
│   ├── pump-01                     (DEVICE — polls Modbus registers: pressure, temperature)
│   └── pump-02                     (DEVICE — polls Modbus registers: pressure, temperature)
├── singleton-blueprints
│   └── station-controller          (SINGLETON — calculates total flow, station status, SQL logs)
├── alert-rules
│   └── pump-01-overheat            (ALERT — fires an event when temperature > 80 °C)
├── workflows
│   └── overheat-response           (WORKFLOW — creates an operator task & logs escalation)
└── dashboards
    └── station-overview            (DASHBOARD — trends, mimic diagram, and acknowledge button)
```

How data flows at runtime:

1. **Driver polls the equipment:** `pump-01` has type `DEVICE` and runs the Modbus driver. Every polling cycle it writes fresh values into typed **variables** on `pump-01` (for example, `temperature = 82.4 °C`).
2. **History is recorded automatically:** if `historyEnabled = true` on `temperature`, ISPF writes samples to the time-series store (H2 locally; PostgreSQL/TimescaleDB and optionally ClickHouse in production).
3. **Calculations update:** `station-controller` has **calculation rules** (*binding rules*) that read `pump-01/temperature` and `pump-02/temperature` and compute station-wide KPIs.
4. **Alarms and workflows react:** `pump-01-overheat` evaluates a formula (`self.temperature.value > 80.0`), records an alarm event, and optionally triggers a BPMN workflow (`overheat-response`) that puts a task into the operator's queue.
5. **Operators see live screens:** WebSocket pushes variable and event updates to the browser. Operators open `http://<server>:8080?mode=operator` and see only the dashboards and SCADA mnemonics — without the admin tree or configuration editors.

---

## 2. Six terms worth knowing up front

Before reading deeper guides, here is what ISPF's core terms mean in everyday engineering language:

| Term in ISPF | What it actually means |
|--------------|------------------------|
| **Object tree** (`root.platform.*`) | Hierarchical address space of everything on the server. Paths use dots: `root.platform.devices.pump-01`. Every node can hold variables, events, functions, and calculation rules. |
| **`DEVICE` vs logic objects (`SINGLETON` / `INSTANCE`)** | A `DEVICE` node represents physical or simulated equipment (PLC, sensor, broker connection) and runs a protocol driver. **Never put plant-wide calculations or application SQL logic on a `DEVICE`.** Instead, use a **`SINGLETON`** node when there is one central controller/hub for the whole application, or an **`INSTANCE`** template when you have many identical units (for example, 20 boiler houses or 100 retail stores) that each carry their own logic. |
| **Blueprint (template)** | A reusable template defining variables, events, functions, and rules so you do not recreate tags by hand. Three kinds: `SINGLETON` (one unique hub object), `INSTANCE` (type stamped out many times), and `MIXIN` (add-on set of variables attached to existing objects). |
| **Binding rule & CEL** | A formula attached to an object that recalculates a variable when an input variable changes, when an event fires, or on a timer. Formulas use **CEL** (Google's Common Expression Language, e.g. `self.temperature.value > 80.0`) or built-in signal functions (`scale`, `clamp`, `movingAvg`, `counterRate`). |
| **Application bundle (`bundle.json`)** | A JSON package containing your project's object templates, SQL table migrations, backend functions, dashboards, and workflows. You keep it in Git, validate it (`ispf validate`), and deploy it to test/production servers instead of copying nodes by hand. |
| **Built-in Dashboard vs UI pack** | ISPF includes a built-in drag-and-drop Dashboard & SCADA mimic editor for standard HMI screens. When a product needs a custom web application (maps, 3D, custom forms/tables), you build a regular React SPA in your IDE, zip the static build as a **`ui-pack`**, and ISPF serves it at `/apps/<appId>/` while your SPA calls backend functions via `POST /api/v1/bff/invoke`. |

Full dictionary: [Glossary](glossary.md).

---

## Path A — Your first hour (install and explore)

Goal: run ISPF locally and trace how a sensor value reaches an alarm and an operator screen.

| Step | Time | Action | Guide |
|------|------|--------|-------|
| 1 | 15 min | Start ISPF locally (portable zip, Docker, or source) and sign in (`admin` / `admin` — demo only; **change before network exposure**) | [Getting started — Try ISPF](getting-started.md#try-ispf-15-minutes) · [Security](security.md) |
| 2 | 10 min | Start the built-in `demo-sensor-01` driver and watch `temperature` and `alarmActive` change | [Getting started § First steps](getting-started.md) |
| 3 | 10 min | Open `?mode=operator` to see what a shift operator sees vs an administrator | [Operator guide](operator-guide.md) |
| 4 | 15 min | Inspect how nodes, variables (`DataRecord`), events, and functions are structured | [Object model](object-model.md) |
| 5 | 10 min | Review the 7 common design mistakes before building your own project | [Common design mistakes](anti-patterns.md) |

---

## Path B — Your first project (one engineering day)

Goal: connect a device, compute a derived value, build an operator screen, add an alarm, and package the solution for deployment.

| Step | Goal | Guide |
|------|------|-------|
| 1 | Create a logic hub (`SINGLETON` or `INSTANCE`) and a child `DEVICE` node | [Application architecture rules](application-principles.md) · [Blueprints](blueprints.md) |
| 2 | Configure a protocol driver (`virtual`, `modbus-tcp`, `mqtt`, `opcua`, `snmp`) and verify `RUNNING` status | [Device drivers](drivers.md) |
| 3 | Add a calculation rule (binding) or an object function on the hub | [Calculation rules (bindings)](bindings.md) · [Expression language](expression-language.md) |
| 4 | Build a dashboard or SCADA mimic diagram bound to your variables | [Dashboards](dashboards.md) · [SCADA mnemonics](scada.md) · [Widgets](widgets.md) |
| 5 | Add a threshold alert rule and (optionally) a BPMN workflow for operator tasks | [Automation](automation.md) · [BPMN workflows](workflows.md) |
| 6 | Package, validate, and deploy as a `bundle.json` (or sync via Application Git) | [Solution developer guide](solution-developer-guide.md) · [Applications — Git](applications.md#application-git-adr-0063) |

> **Need to run a 30–60 minute live product demo for a customer instead?** Follow the [Customer demostand playbook](customer-demo-playbook.md).

---

## Path C — Reading tracks by role

| Your role | Recommended sequence | Certification |
|-----------|----------------------|---------------|
| **Shift operator / dispatcher** | Path A → [Operator guide](operator-guide.md) → [SCADA](scada.md) | — |
| **Automation / solution engineer** | Path A → Path B → Curriculum below → [Custom React UI](external-ide.md) (if building custom web screens) | [Certification — Solution developer](certification.md#solution-developer-track) |
| **OT / PLC / driver engineer** | Path A → [Device drivers](drivers.md) → [Driver DDK](driver-ddk.md) → [Field pilot playbook](field-pilot-playbook.md) | Admin L2 modules |
| **DevOps / system administrator** | [Deployment](deployment.md) → [Security](security.md) → [Historian tiers](historian-tiers.md) → [Observability](observability.md) | [Certification — Platform admin](certification.md#platform-admin-track) |
| **Engineer using AI Studio / Cursor MCP** | Path A → [AI Studio & MCP](ai-development.md) → [Custom React UI](external-ide.md) → [Automation tutorials](ot-automation-excellence-tutorials.md) | Solution developer L3 |

---

## Curriculum — deep track (text)

Work through these 12 modules in order when onboarding new engineers to a project team. Each module pairs one reference page with a concrete hands-on exercise:

| # | Module | What you learn | Hands-on practice | Guide |
|---|--------|----------------|-------------------|-------|
| 1 | Platform overview | Why telemetry, HMI, alarms, and workflows share one object tree | Sketch your site's hierarchy under `root.platform` | [Product](product.md) · [Architecture](architecture.md) |
| 2 | Object model | Paths, `ObjectType`, typed `DataRecord` variables, events, functions | Create paths for one station hub and two `DEVICE` nodes | [Object model](object-model.md) |
| 3 | Equipment drivers | Driver packs, point mappings, polling intervals, error diagnostics | Bring a simulator, Modbus, or MQTT device to `RUNNING` | [Device drivers](drivers.md) |
| 4 | Templates & logic separation | Why `DEVICE` is I/O only; when to choose `SINGLETON`, `INSTANCE`, or `MIXIN` | Create a template and instantiate two identical units | [Blueprints](blueprints.md) · [Application rules](application-principles.md) |
| 5 | Calculations & formulas | Reactive vs periodic rules, execution order, cycle protection, CEL | Compute a scaled or aggregated variable on the hub | [Bindings](bindings.md#execution) · [Expression language](expression-language.md) |
| 6 | Historian & rollups | Recording time series, retention, hot/warm/cold storage, hourly/shift rollups | Enable history on a tag, plot a trend, and configure a rollup | [Variable history](variable-history.md) · [Historian cookbook](analytics-historian-cookbook.md) |
| 7 | Alarms & workflows | Threshold alerts, hysteresis (`deactivateExpr`), correlators, BPMN tasks | Trigger an overheat alarm that creates an operator task | [Automation](automation.md) · [BPMN workflows](workflows.md) |
| 8 | Dashboards & SCADA | 84×8 layout grid, `selectionKey` master-detail tables, P&ID mnemonics | Build an overview screen with a device table and detail chart | [Dashboards](dashboards.md) · [Widgets](widgets.md) · [SCADA](scada.md) |
| 9 | Security & roles | `admin` vs `operator`, Keycloak/OIDC, per-object and per-variable permissions | Verify operator login cannot edit tree nodes or layouts | [Security](security.md) · [Operator guide](operator-guide.md) |
| 10 | Packaging & custom UI | SQL migrations, script functions, `bundle.json` validation, optional React `ui-pack` | Validate and deploy a bundle without validation warnings | [Solution developer guide](solution-developer-guide.md) · [Custom React UI](external-ide.md) |
| 11 | Production operations | Docker/Linux deployment, backups, Prometheus metrics, health checks | Inspect `/actuator/health` and the System metrics tab | [Deployment](deployment.md) · [Observability](observability.md) |
| 12 | Engineering hygiene | Naming conventions, short expressions, avoiding event-journal floods | Audit your project against the 7 common mistakes | [Common design mistakes](anti-patterns.md) |

Ready-to-explore reference examples in `examples/`: [Lab training](lab-training.md) · [Mini-CHP station](reference-mini-tec-walkthrough.md) · [Building HVAC](reference-building-hvac-walkthrough.md) · [MES walkthrough](reference-mes-walkthrough.md).

---

## Quick topic lookup

| Question | Page |
|----------|------|
| How do I install and start ISPF? | [Getting started](getting-started.md) · [Deployment](deployment.md) |
| The console, login, or demo sensor failed | [Troubleshooting](troubleshooting.md) |
| What does a specific term mean? | [Glossary](glossary.md) |
| Which functions are available in formulas? | [Expression language](expression-language.md) |
| What mistakes should I avoid? | [Common design mistakes](anti-patterns.md) |
| How do I diagnose a slow or failing node? | [Observability](observability.md) · [Device drivers](drivers.md) |
| Where is the full list of all ~130 docs? | [Documentation index](readme.md) |
| What do we not claim yet? | [Known limitations](known-limitations.md) |
