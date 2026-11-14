> **Language:** Canonical English. Russian edition: [ru/glossary.md](../ru/glossary.md).

# ISPF Glossary

> **Status:** Stable — Plain-language dictionary of platform terms. Status tags: [doc-status.md](doc-status.md).

Plain-language definitions of the terms used across ISPF screens, APIs, and documentation. For a 3-minute introduction to how these pieces fit together, start with [How ISPF works](learn.md).

---

## A

**Alert rule (`ALERT`)** — a threshold or state check watching an object variable via a CEL formula (for example, `self.temperature.value > 80.0`). Supports activation delay, hysteresis/latching (`deactivateExpr`), notifications, and automatic event firing. Stored as a node under `root.platform.alert-rules`. See [Automation](automation.md).

**Application** — a packaged project deployed onto ISPF: its own isolated database schema (`app_<id>`), SQL migrations, JSON script functions, object tree nodes, dashboards, workflows, and operator navigation. Registered under `root.platform.applications`. Note: at runtime all live tags, alarms, and screens execute through the shared object tree, not in a separate server process. See [Solution developer guide](solution-developer-guide.md).

**Application bundle (`bundle.json`)** — the JSON manifest describing an entire application so it can be stored in Git, validated (`ispf validate`), diffed, and deployed to another server via `POST /api/v1/applications/{id}/deploy`.

---

## B

**BFF (`POST /api/v1/bff/invoke`)** — "Backend for Frontend" HTTP endpoint used by operator screens and custom React web apps (`ui-pack`) to invoke functions on hub objects with consistent input/output formatting and user permissions.

**Binding rule** — a calculation rule attached to an object (stored in `@bindingRules`). Automatically recalculates a target variable, updates dashboard context, or fires an event whenever input variables change, an event arrives, or a timer (`periodicMs`) ticks. See [Calculation rules (bindings)](bindings.md).

**Blueprint (template / model)** — a reusable definition of variables, events, functions, and binding rules (`BlueprintDefinition`). ISPF provides three blueprint kinds:
- **`SINGLETON`** — creates one unique controller/orchestrator object for a whole solution (for example, `station-controller`).
- **`INSTANCE`** — defines an object type (for example, `boiler-unit-v1`) from which you instantiate many identical units, each with its own variables and logic.
- **`MIXIN`** — an add-on group of variables/rules merged into existing objects (explicitly or via a CEL applicability condition).
See [Blueprints](blueprints.md).

**BPMN** — Business Process Model and Notation 2.0 XML used to draw and execute multi-step automated and human-in-the-loop processes (`WORKFLOW` nodes). ISPF runs a built-in Java engine supporting a documented subset of BPMN elements. See [BPMN workflows](workflows.md).

---

## C

**CEL (Common Expression Language)** — a fast, side-effect-free expression syntax (originally by Google) used across ISPF for formulas, alert conditions, and workflow branches: e.g. `self.pressure.value >= 4.5 && !self.maintenanceMode.value`. Full syntax and functions: [Expression language](expression-language.md).

**Change set** — a staged batch of object-tree edits that can be previewed and reviewed before applying them at once. See [Collaboration](collaboration.md).

**Claim** — an operator action in the **Work Queue** that assigns a pending BPMN user task to the current shift operator before completing it.

**Correlator (`CORRELATOR`)** — a rule under `root.platform.correlators` that watches a sequence, count, or time window of events (for example, three pump trips within 10 minutes) and triggers an action or starts a BPMN workflow. See [Automation](automation.md).

---

## D

**Dashboard (`DASHBOARD`)** — an operator HMI screen arranged on an **84×8** grid of widgets (values, trend charts, tables, gauges, SCADA mimics, buttons). Created in Dashboard Builder under `root.platform.dashboards`. See [Dashboards](dashboards.md).

**DataRecord & DataSchema** — the typed tabular value stored inside every ISPF variable. `DataSchema` declares field names and types (`DOUBLE`, `STRING`, `BOOLEAN`, `DATETIME`, …), and `DataRecord` holds one or more rows of values (for a scalar sensor tag, typically one row `{ "value": 23.5, "unit": "C" }`). See [Object model](object-model.md).

**Device (`DEVICE`)** — an object tree node representing a physical or simulated controller, PLC, meter, broker channel, or IT endpoint. A `DEVICE` runs a protocol driver and holds raw/mapped telemetry tags; application-wide logic belongs on `SINGLETON` or `INSTANCE` objects instead.

**Driver & Driver pack** — a pluggable protocol adapter (`modbus-tcp`, `opcua`, `s7`, `mqtt`, `snmp`, `iec104`, `dnp3`, `jdbc`, `virtual`, …) loaded from `data/drivers` (`ISPF_DRIVER_PACKS_DIR`) that polls or subscribes to external equipment and updates `DEVICE` variables. See [Device drivers](drivers.md).

---

## E

**Event** — a timestamped notification emitted by an object (`INFO`, `WARNING`, `ERROR`, `CRITICAL`), validated against an `EventDescriptor`, broadcast over WebSocket, and optionally persisted to `event_history`.

**Explorer & Inspector** — the two main panels of the Admin Console: **Explorer** navigates the object tree hierarchy on the left, while **Inspector** edits properties, variables, functions, events, and calculation rules of the selected node.

---

## F

**Federation** — linking remote edge ISPF servers to a central hub ISPF server so operators can monitor distributed sites from one console, including store-and-forward buffering across unstable links. See [Federation](federation.md).

**Function (object / script / Java function)** — a callable operation on an object (`POST /api/v1/objects/by-path/functions/invoke` or `/api/v1/bff/invoke`). Can be a built-in server handler (e.g. `acknowledgeAlarm`), a declarative JSON/SQL script (`selectMany`, `exec`, `return`), or a compiled `ObjectJavaFunction`. See [Object functions](object-functions.md).

---

## H

**Historian** — ISPF's time-series storage and aggregation subsystem for variables with `historyEnabled = true`. Supports single-DB PostgreSQL/TimescaleDB (**hot** tier), ClickHouse (**warm** tier for high-rate / long-horizon analytics), and Parquet object storage (**cold** archive), plus windowed rollups (`kind: "historian"`). See [Variable history](variable-history.md) and [Historian storage tiers](historian-tiers.md).

**Hosted UI pack (`ui-pack`)** — a zip archive containing a compiled standalone web frontend (typically React + Vite) and a `ui-pack.json` manifest. Installed into ISPF and served at `/apps/<appId>/` when a project needs custom operator screens beyond standard dashboard widgets. See [Custom React UI](external-ide.md).

---

## M

**MCP (Model Context Protocol)** — standard protocol endpoint (`POST /api/v1/ai/mcp`) allowing external developer IDEs (such as Cursor or VS Code) to inspect the object tree, validate bundles, and call ISPF tools with role-based permissions. See [AI Studio & MCP](ai-development.md).

**Mimic (`MIMIC` / `scada-mimic`)** — an interactive vector technological diagram (P&ID, single-line electrical schematic, station layout) built from SVG symbols bound to live variables and equipment commands. Stored under `root.platform.mimics.*` and displayed via the `scada-mimic` widget. See [SCADA mnemonics](scada.md).

---

## O

**Object tree** — the single hierarchy of all nodes on an ISPF server, addressed with dot-separated paths starting at `root.platform` (for example, `root.platform.devices.pump-01`).

**Operator app & Operator HMI** — the full-screen operator mode (`?mode=operator&app=<appId>`) showing read-only dashboards, interactive mimic controls, the event log, and the BPMN Work Queue — without administrative editors. Configured under `root.platform.operator-apps` or in `bundle.json` (`operatorUi`). See [Operator guide](operator-guide.md).

---

## P

**PlatformRef** — slash-separated reference string addressing a variable, field, function, event, or historian tag inside expressions and widget bindings:
- `@/temperature` — `temperature` on the current object (`@`)
- `root.platform.devices.pump-01/temperature/value` — `value` field on a specific object
- `@/fn/acknowledgeAlarm` — function on the current object
- `@/evt/overheat` — event on the current object

---

## S

**`selectionKey`** — a shared selection slot name on a dashboard (for example, `"device"`). Clicking a row in an `object-table` widget with `selectionKey: "device"` updates all charts, gauges, and indicators on the same screen that carry `selectionKey: "device"` to show that chosen object. See [Dashboards](dashboards.md).

---

## V

**Variable** — a named, typed data point on an object (tag, setpoint, configuration parameter, or calculated KPI) holding a `DataRecord` value, access flags (`readable` / `writable`), optional calculation rules, and optional history recording.

---

## W

**Web Console** — the built-in browser interface of ISPF (`apps/web-console/`), serving both the Administrator environment and Operator HMI.

**Work Queue** — the operator's task list in Operator HMI where active BPMN `userTask` steps appear for claiming and completion during a shift.

**Workflow (`WORKFLOW`)** — an executable BPMN process under `root.platform.workflows` combining automatic service steps (calling functions, setting variables, sending messages), conditional gateways, timers, and operator tasks.

---

## Common abbreviations

| Abbreviation | Full name | Meaning in context |
|--------------|-----------|--------------------|
| **ISPF** | IoT Solutions Platform Framework | This platform |
| **HMI** | Human-Machine Interface | Operator screens, dashboards, and controls |
| **SCADA** | Supervisory Control and Data Acquisition | Real-time telemetry monitoring, mimics, and supervisory commands |
| **MES** | Manufacturing Execution System | Shop-floor order tracking, OEE, batches, quality, and genealogy |
| **PLC / RTU** | Programmable Logic Controller / Remote Terminal Unit | Industrial field controllers polled by ISPF drivers |
| **P&ID** | Piping and Instrumentation Diagram | Technological process schematic with pumps, valves, tanks, and sensors |
| **OPC UA** | Open Platform Communications Unified Architecture | Industrial interoperability protocol (`opcua` / `opcua-server` drivers) |
| **MQTT** | Message Queuing Telemetry Transport | Lightweight publish/subscribe telemetry broker protocol |
| **SNMP** | Simple Network Management Protocol | Network and server equipment monitoring protocol |
| **RBAC** | Role-Based Access Control | Permission model based on user roles (`admin`, `operator`, custom roles) |
| **OIDC / JWT** | OpenID Connect / JSON Web Token | Single sign-on authentication (e.g., via Keycloak) and API bearer tokens |
