> **Language:** Canonical English. Russian edition: [ru/glossary.md](../ru/glossary.md).

# ISPF Glossary

> **Status:** Stable — Terms and definitions. Hub: [doc-status.md](doc-status.md).

A concise dictionary of platform terms. Product overview: [product](product.md).

---

## Core principle (cross-cutting term)

**Business logic in platform mechanisms** — the ISPF architectural principle: application rules and behavior are described by declarative configuration of the **object tree** (models, variables, events, functions, workflow, alert rules, correlators), not by domain-specific Java in `ispf-server`. The platform implements generic engines; bundle deploy delivers configuration into those mechanisms. See [architecture](architecture.md).

---

## A

**Alert rule** — an automation rule: a CEL condition on an object variable. When true, an event is fired automatically. An `ALERT` node in `root.platform.alert-rules`.

**Application (deploy application)** — a registered application solution with an isolated SQL schema, JSON functions, and an optional bundle. Registered via `POST /applications`. Appears in the tree under `root.platform.applications`.

**Application function** — an application function described as a JSON script (steps `selectOne`, `update`, …). Runs in a sandbox without Java code in the core.

---

## B

**Binding** — a rule for computing variable values (`BindingRule` in `@bindingRules`). Recalculated by `BindingRuleEngine` on activators (local and cross-object changes). Expression — Google **CEL** or a platform function. See [bindings](bindings.md).

**BFF (Backend-for-Frontend)** — the `POST /bff/invoke` gateway for calling application functions from the UI. Wire profile `ispf-operator-v1` — contract for the legacy manifest.

**Blueprint** — see **Model**.

**Bundle** — a JSON application manifest (SQL migrations, functions, operator UI, reports, objects). Deployed via `POST /api/v1/applications/{id}/deploy` (`Content-Type: application/json`). Not a multipart ZIP upload.

**BPMN** — Business Process Model and Notation. Notation for describing workflow. ISPF uses BPMN 2.0 XML with namespace extensions `http://ispf.io/bpmn`.

---

## C

**CEL (Common Expression Language)** — Google's expression language. Used in bindings, alert rules, and workflow gateway conditions. Full ISPF reference (literals, operators, platform bindings, historian helpers): [expression-language](expression-language.md).

**Claim** — an operator action in the work queue: assigning a user task to oneself.

---

## D

**Dashboard** — an object of type `DASHBOARD` with layout JSON (widget grid). Created in Dashboard Builder, displayed in operator HMI.

**DataRecord** — a typed record on an object variable. Contains `DataSchema` (fields) and values.

**DeviceDriver** — SPI interface for a device driver. Implementations: mqtt, modbus, snmp, virtual, … (58 modules).

**Driver runtime** — device polling service. Start/stop via `POST /drivers/runtime/start|stop`.

---

## E

**Event** — a typed notification from an object. Has a descriptor (name, payload schema, level). Published via `POST /events/fire` or an alert rule.

**Event correlator** — a rule: event chain → workflow start. A `CORRELATOR` node in `root.platform.correlators`.

**Explorer** — the panel for viewing properties of the selected tree node in the admin console.

---

## F

**Flyway** — SQL migration tool for the platform. Application migrations **do not** use Flyway — only the `data/migrate` API.

**Function (platform function)** — an executable function on a platform object (not an application function). Defined in the object model.

---

## H

**HMI (Human-Machine Interface)** — the operator interface. In ISPF — operator HMI based on dashboards.

---

## I

**Inspector** — the object details panel: properties, variables, events, functions.

**Instance (workflow instance)** — a running BPMN process instance. Statuses: `RUNNING`, `WAITING`, `COMPLETED`, `FAILED`.

---

## L

**Layout** — JSON description of a dashboard widget grid. Stored in the `layout` variable of a `DASHBOARD` object.

**Legacy manifest** — legacy operator shell format (`operatorManifest` in JSON). Replaced by `operatorUi` + platform dashboards.

---

## M

**Blueprint (BlueprintDefinition)** — object structure template: variables, events, functions, bindings. Kinds: `MIXIN`, `SINGLETON`, `INSTANCE`. MIXIN blueprints auto-apply on create only when CEL is non-empty (*Applicability condition* / `suitabilityExpression`). Explicit apply — via `templateId` or API.

**Fixture model** — demo/lab model (`mqtt-sensor-v1`, `mqtt-gateway-v1`, …), registered when `ispf.bootstrap.fixtures-enabled=true`. Not part of the core built-in registry. See [0018-fixture-models-and-cel-applicability](decisions/0018-fixture-models-and-cel-applicability.md).

**Model engine** — the `ispf-plugin-blueprint` plugin that applies models to objects.

---

## O

**Object tree** — hierarchy of platform nodes with dot-path addressing (`root.platform.devices.sensor-01`).

**ObjectType** — node type: `DEVICE`, `DASHBOARD`, `WORKFLOW`, `ALERT`, `CORRELATOR`, `MODEL`, `APPLICATION`, `USER`, `PLATFORM`, `ALERT_RULES`, … System folders have a semantic type, not `CUSTOM`.

**Operator app** — operator UI configuration for a specific application. Stored in `operator_app_ui`, edited in `root.platform.operator-apps`.

**Operator HMI** — Web Console mode for operators: read-only dashboards, work queue, event journal.

**Operator UI** — JSON config: title, dashboard list, default dashboard. Loaded via `GET /operator-apps/{id}/ui`.

---

## P

**Platform object** — a node in the ISPF object tree (as opposed to an application record in the `applications` table).

**Plugin** — a platform extension. In the repository: `ispf-plugin-blueprint`, `ispf-plugin-workflow`. Commercial plugins live outside `main`.

---

## R

**RBAC** — Role-Based Access Control. Roles: `admin`, `operator`.

**REQ-PF** — requirements for the application platform layer. Status: [roadmap.md § Part A](roadmap.md).

---

## S

**selectionKey** — name of a dynamic object selection slot on a dashboard. A widget with `selectionKey: "device"` reads the path from `selection.device`, set by clicking a table row.

**Service task** — a BPMN element: an automatic action (LOG, SET_VARIABLE, INVOKE_FUNCTION, PUBLISH_NATS).

**SPI (Service Provider Interface)** — extension contract. Example: `DeviceDriver` for drivers.

---

## U

**User task** — a BPMN element: a task for an operator. Appears in the Work Queue until claim/complete.

---

## V

**Variable** — a named value on an object. Typed via `DataRecord`. May have a CEL binding.

**Virtual driver** — a simulator driver. Generates test data (for example, sinusoidal temperature for `demo-sensor-01`).

---

## W

**Web Console** — React application for admin + operator UI. Directory: `apps/web-console/`.

**WebSocket** — `WS /ws/objects` — live updates for variables and events.

**Widget** — a dashboard element: value, chart, object-table, spreadsheet, work-queue, … Reference: [widgets](widgets.md).

**Work Queue** — queue of BPMN user tasks for operators.

**Workflow** — an object of type `WORKFLOW` with BPMN XML. Object statuses: `DRAFT`, `ACTIVE`, `STOPPED`.

**Workflow engine** — pure Java in `ispf-plugin-workflow` (without Camunda/Flowable).

---

## Abbreviations

| Abbreviation | Expansion |
|--------------|-----------|
| ISPF | IoT Solutions Platform Framework |
| HMI | Human-Machine Interface |
| SCADA | Supervisory Control and Data Acquisition |
| MES | Manufacturing Execution System |
| OPC UA | Open Platform Communications Unified Architecture |
| SNMP | Simple Network Management Protocol |
| MQTT | Message Queuing Telemetry Transport |
| JWT | JSON Web Token |
| OIDC | OpenID Connect |
