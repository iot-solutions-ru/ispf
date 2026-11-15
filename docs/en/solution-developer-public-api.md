> **Language:** Canonical English. Russian edition: [ru/solution-developer-public-api.md](../ru/solution-developer-public-api.md).

# Solution Developer Public API (`bundle.json` & REST Contract)

> **Status:** Stable — supported compatibility boundary between the ISPF platform core and application solution bundles. Status tags: [doc-status.md](doc-status.md).

This document defines which REST/WebSocket APIs and `bundle.json` sections form the stable public contract for application solutions, and which internal server mechanisms must not be modified by application projects. For a step-by-step authoring guide, see the **[Solution Developer Guide](solution-developer-guide.md)** and **[ADR-0001](decisions/0001-app-platform-boundary.md)**.

---

## 1. Supported Solution Contract

| Area | Public API / Manifest Section | Notes |
|------|-------------------------------|-------|
| Application registration | `POST /api/v1/applications` | Defines `appId`, isolated `schemaName`, and `tablePrefix` |
| Application SQL schema | `POST .../data/migrate`, `.../data/seed`, `migrations[]` | Runs in the application's isolated schema (never in platform Flyway) |
| Bundle deployment | `POST /api/v1/applications/{id}/deploy`, `POST /api/v1/platform/packages/import` | Single declarative `bundle.json` manifest |
| Backend functions | `POST .../functions/deploy`, `functions[]` | JSON step scripts (`selectMany`, `exec`, `return`, …) on `SINGLETON` / `INSTANCE` logic hubs |
| UI function invocation (BFF) | `POST /api/v1/bff/invoke`, `POST .../functions/invoke` | Standard wire profile `ispf-operator-v1` |
| Object tree | `GET / POST / PATCH /api/v1/objects/**`, `objects[]` | Nodes, variables, events, and functions |
| Blueprints | `/api/v1/singleton-blueprints`, `/api/v1/instance-types`, `/api/v1/mixin-blueprints`, `blueprints[]` | `SINGLETON`, `INSTANCE`, and `MIXIN` templates |
| Dashboards & workflows | `dashboards[]`, `workflows[]`, tree nodes | `84×8` grid layouts and supported BPMN 2.0 XML subset |
| Automation | `alertRules[]`, `correlators[]`, tree nodes | CEL threshold rules and event correlation patterns |
| Operator menu | `operatorUi` in `bundle.json`, `/api/v1/operator-apps/**` | Operator workspace tabs and default dashboard |
| Hosted React UI | `artifactKind: "ui-pack"`, `operatorUi.spaNav` / `uiPack` | Served at `/apps/<appId>/` ([External IDE guide](external-ide.md)) |
| Reports | `root.platform.reports.*`, `reports[]` | Read-only `SELECT` queries + optional XLSX/DOCX/HTML templates |
| Live WebSocket updates | `WS /ws/objects` (`subscribe`, `subscribe_events`) | Live variable values and event catalog stream ([Messaging](messaging.md)) |
| Package dependencies | `requires[]` in `bundle.json` | Declares required `appId` and `minVersion` |
| Protocol drivers | `DeviceDriver` SPI in a separate driver pack JAR | Loaded from `ISPF_DRIVER_PACKS_DIR` ([Driver DDK](driver-ddk.md)) |

---

## 2. What Solution Projects Must Not Do

| Prohibited Action | Why It Breaks Maintainability | Supported Alternative |
|-------------------|-------------------------------|-----------------------|
| Adding domain Java code to `packages/ispf-server/` | Turns the server into an un-upgradeable fork ([ADR-0001](decisions/0001-app-platform-boundary.md)) | Declarative `script` functions on logic hubs + `bundle.json` |
| Adding industry-specific pages to `apps/web-console/` | Couples the platform admin console to one customer project | Separate React/Vite SPA packaged as a `ui-pack` ([ADR-0054](decisions/0054-hosted-ui-packs.md)) |
| Putting application tables into platform Flyway migrations | Breaks schema isolation and platform upgrades | `migrations[]` inside the application's own `schemaName` |
| Creating custom REST controllers `/api/v1/<app>/...` | Bypasses unified RBAC, ACL, and audit logging | Invoke functions via `POST /api/v1/bff/invoke` |
| Attaching application logic or BFF functions to `ObjectType.DEVICE` | Taking a sensor offline breaks application logic (`LOGIC_HOST_DEVICE`) | Place orchestration and BFF functions on a `SINGLETON` or `INSTANCE` node |

---

## 3. Semantic Versioning (`version`) for Solution Bundles

The `version` field in `bundle.json` must follow strict SemVer (`MAJOR.MINOR.PATCH`):
- **MAJOR** (`1.x.x` → `2.0.0`): removing or renaming a function, event, or object path, or adding a required input field to an existing function.
- **MINOR** (`1.1.0` → `1.2.0`): adding new backward-compatible objects, dashboards, functions, or events.
- **PATCH** (`1.2.0` → `1.2.1`): bug fixes in script steps, labels, or seed data.

**Related guides:** [Application solutions (`applications.md`)](applications.md) · [Solution developer guide](solution-developer-guide.md) · [Commercial licensing](commercial-licensing.md) · [Plugins](plugins.md).
