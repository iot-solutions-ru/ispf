> **Language:** Canonical English. Russian edition: [ru/platform-evolution.md](../ru/platform-evolution.md).

# ISPF Evolution — Retrospective Backlog

Chronological checklist of **what was delivered** over the platform's lifetime. Items marked done are completed work — this is not a plan, but a **retrospective** of evolution.

**Baseline:** `main` → `0.9.41`.  
**Sources:** git history, [roadmap.md](roadmap.md), release tags.

---

## How to read this document

| Symbol | Meaning |
|--------|---------|
| `[x]` | Implemented and in baseline `main` |
| `[~]` | Partial / in progress (see [roadmap.md](roadmap.md)) |
| `[ ]` | Planned, not yet closed |

Evolution runs **top to bottom** — from foundation to the current state.

---

## Version milestones

| Version | Meaning |
|--------|-------|
| **v0.1.x** | First runnable baseline: object tree, drivers, dashboards |
| **v0.2.0** | Phase 5 closure — declarative chains, models, bundle convergence |
| **v0.3.0** | Production readiness: federation, driver maturity, PF-09/11 |
| **v0.5.x** | Federation bind overlay, outbound tunnel |
| **v0.6.0** | Persistent binding state (`@bindingState`) |
| **v0.7.x** | AI Development Layer, tree-first agent, MCP, licensed drivers |
| **v0.8.0** | `@bindingRules` / BindingRuleEngine instead of legacy `bindingExpression` |
| **v0.8.27+** | Visual groups, mini-TEC, operator alarm bar |
| **v0.9.x** | Scale and observability: Redis, NATS, ClickHouse, automation pipeline, spreadsheet |
| **0.9.41** | Current baseline |

---

## 1. Genesis — core and first model

- [x] **Initial commit** — ISPF monorepo: `ispf-core`, `ispf-server`, `ispf-expression`, driver SPI
- [x] **Object tree** — `root.platform.*` hierarchy, REST API, JPA persistence
- [x] **DataRecord + DataSchema** — typed variables on objects
- [x] **Google CEL** (`ispf-expression`) — computed bindings and alert rules
- [x] **DeviceDriver SPI** — first protocol drivers (Modbus, SNMP, …)
- [x] **SNMP driver** + sequence correlator (automation patterns)
- [x] **Dashboard builder** — first HMI widget set
- [x] **BPMN workflow engine** — processes, user tasks, work queue
- [x] **Web Console (React + Vite)** — Explorer, model and dashboard editors
- [x] **WebSocket live updates** — push variable changes to the UI
- [x] **Full documentation** — architecture, API, object model, getting started
- [x] **License** — MIT → Apache 2.0 → later AGPL-3.0 (see §10)

---

## 2. Application platform — REQ-PF

Turning point: solutions live **on the platform** via bundle deploy, not in industry-specific Java server code.

- [x] **REQ-PF specification** — [roadmap.md § Part A](roadmap.md), ADR [0001](decisions/0001-app-platform-boundary.md)
- [x] **Sprint A** — script runtime: `selectMany`, `setVar`, `when`/`if`, transactions; app schema isolation (PF-02)
- [x] **Sprint B** — bundle metadata, BFF wire profile `anima-operator-v1`, `cancel_workflows` (PF-06, PF-10)
- [x] **Sprint C** — manifest-driven operator shell via generic `POST /bff/invoke`
- [x] **Sprint C P2** — SQL bindings, function rollback, virtual simulator profiles (PF-08, PF-09)
- [x] **Sprint D** — bundle rollback, operator manifest API, terminal parity tests
- [x] **Application deploy API** — `POST /applications/{appId}/deploy`: migrations, functions, objects, dashboards, workflows, models
- [x] **Platform scheduler** — cron via `platform_schedules`, not `@Scheduled` in Java (PF-05)
- [x] **Script functions** — JSON steps + SQL, no custom `FunctionHandler` in `main` (PF-01)
- [x] **Reference apps** — `examples/demo-app`, `warehouse-app` (dogfooding gate [0002](decisions/0002-dogfooding-gate.md))

---

## 3. Admin UX and operator layer

- [x] **Platform auth** — local token + admin/operator roles
- [x] **Security in the tree** — `root.platform.security`: users, roles
- [x] **Model editor** — blueprint variables/events/functions/bindings
- [x] **Application reports** — first reporting layer at the app level
- [x] **Device driver admin UI** — configure drivers from Explorer
- [x] **Object tree UX** — persist expansion/selection across tabs and refresh
- [x] **Tree icons** — visual semantics for node types
- [x] **Operator Apps** — `root.platform.operator-apps`, autostart per user
- [x] **Operator HMI shell** — manifest screens, dashboard navigation, skip admin on login
- [x] **Explorer create actions** — create child nodes from context menu

---

## 4. Drivers and integrations

- [x] **Wave 1** — HTTP, ICMP, SSH, CoAP, SNMP v3
- [x] **Waves 2–4** — SCADA, IT, integration modules
- [x] **REQ-PF-14 closure** — **58 driverId** in catalog (Modbus, OPC UA, MQTT, JDBC, BACnet, …)
- [x] **Driver maturity labels** — production / beta / stub
- [x] **DEVICE driver provisioning** — deploy driver config from bundle
- [x] **Widget stylesJson** — dashboard widget styling
- [x] **SNMP optional OIDs** + rate variables via platform bindings

---

## 5. Historian, automation, system types

- [x] **Variable historian** — samples, CSV/JSON export, aggregations
- [x] **Historian widgets** — history charts on dashboards
- [x] **Historian stages 6–8** — retention, batch write, dashboard integration
- [x] **Platform metrics** — `GET /api/v1/platform/metrics`, System tab in admin
- [x] **Automation → object tree** — alert rules and correlators as tree nodes (not a separate tab)
- [x] **Semantic ObjectType** — `PLATFORM`, `DEVICES`, `ALERT_RULES`, `CORRELATORS`, `APPLICATIONS`, …
- [x] **Drag-and-drop sortOrder** — sibling order at the same tree level
- [x] **BPMN signal catch** — inter-process signals
- [x] **Object protections** — protect system nodes from accidental deletion

---

## 6. Phase 0 — stabilization and CI

- [x] **GitHub Actions CI** — server build + web-console build
- [x] **Gradle test memory limits** — stable integration tests
- [x] **PF-01c** — `map` / `buildRecord` in script runtime
- [x] **PF-03** — `models[]` in bundle deploy
- [x] **Leader locks** — singleton schedulers in multi-instance mode
- [x] **WebSocket auth** — token query parameter
- [x] **Reference app #2** — warehouse-app acceptance
- [x] **System folder list panels** — Explorer for system catalogs

---

## 7. Production gates — Phase 2–4

- [x] **Keycloak / OIDC** — JWT resource server, Web Console login
- [x] **Per-object ACL** — OWNER / EDITOR / VIEWER on subtrees
- [x] **TimescaleDB hypertables** — historian + retention policies (prod)
- [x] **NATS event bus** — events between platform replicas
- [x] **Multi-tenant spike** — `root.tenant.*` namespaces
- [x] **Federation design** — peer registry, catalog sync, proxy read (objects, dashboards, history)
- [x] **React Router / deep links** — admin navigation without losing state
- [x] **Frontend vitest** — web-console unit/smoke tests

---

## 8. Platform baseline — Java 25 / Spring Boot 4

- [x] **Java 25 toolchain** + CI on Linux (`gradlew` wrapper fix)
- [x] **Spring Boot 4.0.7** migration
- [x] **Jackson 3 native** — `tools.jackson` stack
- [x] **PostgreSQL prod profile** — Flyway migrations, VPS setup scripts
- [x] **Remote deploy tooling** — bootstrap script, direct SCP staging, GitHub release checks
- [x] **Platform runtime bindings** — registry built-in transforms (`counterRate`, `scale`, `clamp`, …)
- [x] **Time-series / cross-object bindings** — rates, smoothing, remote refs without raw CEL
- [x] **Observability UI** — diagnostics, federation ops, API error polish

---

## 9. Phase 5–6 — mechanism strengthening

North star: **more declarative logic in the object tree**, less custom Java.

- [x] **Models** — `extendsModelId`, bulk upgrade API, vendor demo
- [x] **Functions** — extended script steps; declarative SQL bindings
- [x] **Events + correlators** — EVENT_CHAIN, sequenceGapSeconds, N-in-window
- [x] **Workflow serviceTask** — `fire_event`, `read_variable`, `start_workflow`, …
- [x] **Bundle convergence** — bundle = tree packaging; tree-first invoke; reconcile `objects[]`
- [x] **v0.2.0 baseline** — Phase 5 acceptance tests
- [x] **v0.3.0 baseline** — federation production, PF-09 virtual profiles, PF-11 function rollback UI
- [x] **Driver maturity** — CWMP, flexible, gps-tracker → production

---

## 10. Federation and scale

- [x] **Phase 7** — federation auth refresh, 401 retry, service account lifecycle
- [x] **Outbound NAT tunnel** — edge WebSocket → public hub, full proxy
- [x] **Federation bind (PF-13c)** — overlay on local path, same-path remote, unbind restore
- [x] **Federation secrets-key UI** + tunnel reconnect hardening
- [x] **Federation binding** — remote variable refs in CEL/platform bindings
- [x] **Phase 10** — persistent `@bindingState` (hysteresis, deadband, movingAvg, counterRate)
- [x] **Object tree performance** — index, lazy load, scroll fix for large deployments
- [x] **Scale 1000 devices** — lazy tree, runtime telemetry, indexed listeners, DB pool tuning

---

## 11. Collaboration and reports — Phase 11–14

- [x] **Multi-user collaboration** — object revision (If-Match), config audit, stale editor UI
- [x] **WS presence** + subtree leases + model merge preview
- [x] **Change-sets** — preview/apply promotion pipeline ([collaboration.md](collaboration.md))
- [x] **Reports tree-first (Phase 12)** — `report-v1` model, Report Builder, `/api/v1/reports/by-path`
- [x] **YARG export (Phase 13)** — PDF/XLSX/HTML, template upload, widget PDF button
- [x] **Platform catalogs (Phase 14)** — data-sources, schedules, bindings, migrations in the tree
- [x] **Package import** — `POST /platform/packages/import`
- [x] **Script functions on tree** — `FunctionDescriptor.sourceBody`
- [x] **Dashboard report widget** + operatorUi `reports[]`

---

## 12. Lab training and widgets — Phase 15

- [x] **Virtual driver profile `lab`** + model `virtual-lab-v1`
- [x] **Automation v2** — alert `delaySeconds`/`sustainWhileTrue`, correlator `payloadFilterExpr`, `SET_VARIABLE`, `OPEN_OPERATOR_REPORT`
- [x] **Report type `tree-variables`** — cross-device RECORD_LIST
- [x] **New widgets** — pie-chart, history-table, variable-editor, svg-widget, composite-widget
- [x] **Importable bundle** — `examples/lab-training/` + lab users/ACL bootstrap
- [x] **Docs + integration tests** — [lab-training.md](lab-training.md)

---

## 13. Platform evolution REQ-FW — Phase 16

- [x] **ADR process** — `docs/decisions/` (18 ADR)
- [x] **Gap registry** — summary in [roadmap.md § Part I](roadmap.md)
- [x] **Commercial licensing** — RSA keys, `installationId`, LicenseBuilder ([commercial-licensing.md](commercial-licensing.md))
- [x] **MES reference** — walkthrough + synthetic demo ([reference-mes-walkthrough.md](reference-mes-walkthrough.md))
- [x] **Solution public API** — boundary doc + event catalog in bundle
- [x] **Messaging contract** — event bus vs sync RPC ([messaging.md](messaging.md))
- [x] **Bundle `requires[]`** — dependency manifest for commercial bundles

---

## 14. AI Development Layer

- [x] **LlmProvider SPI** — OpenAI-compatible, local/VPS Qwen
- [x] **ContextPack** — platform knowledge briefing for LLM
- [x] **ToolRegistry** — validate/deploy tools
- [x] **AI Studio UI** — Cursor-like chat, persistent sessions
- [x] **Tree-first agent (FW-44)** — multi-turn sessions, live steps, cancel
- [x] **Agent tools FW-45–48** — knowledge, invoke/search, discovery, automation (alert/correlator/bindings)
- [x] **MCP adapter (ADR 0006)** — agent tools over MCP protocol
- [x] **Licensed driver JAR packs (FW-50)** — pilot pack, [licensed-driver-packs.md](licensed-driver-packs.md)
- [x] **Mobile explorer** + roadmap backend (v0.7.7–0.7.13)

---

## 15. Schema cleanup and HMI polish — Phase 17–19

- [x] **v0.8.0** — `@bindingRules` / BindingRuleEngine; Flyway drop legacy `binding_expr`
- [x] **Dashboard session context** — sub-dashboard, expanded widget palette, fullscreen editor
- [x] **Visual groups (ADR 0012)** — typed model catalogs (ADR 0011)
- [x] **MapLibre** — Leaflet replacement; platform SQL object editors
- [x] **mini-TEC reference** — operator HMI, SLD widget, platform bootstrap ([reference-mini-tec-walkthrough.md](reference-mini-tec-walkthrough.md))
- [x] **YARG + lab reports** — agent report tools
- [x] **Operator alarm bar** (v0.8.27)
- [x] **Web Console i18n (Phase 19)** — en/ru/de/zh, LocaleSwitcher, `npm run i18n:check` ([0013](decisions/0013-web-console-i18n.md))
- [x] **Platform schedules UI** + system catalog i18n

---

## 16. Scale, observability, event journal — v0.9.x

- [x] **Performance hardening (0.9.2–0.9.3)** — server + web-console; optional Redis cache
- [x] **Ordered object-change event bus** — dual-lane automation pipeline ([0014](decisions/0014-automation-pipeline-evolution.md))
- [x] **Automation indexes + async journal** — throughput ~22 events/s prod baseline (0.9.9)
- [x] **Prometheus gauges** — automation pipeline metrics (0.9.6)
- [x] **NATS JetStream fan-out** — optional replica fan-out (0.9.7)
- [x] **Redis correlator sliding windows** — window store abstraction (0.9.8)
- [x] **OpenTelemetry** — OTLP metrics (0.9.9) + tracing handlers (0.9.10)
- [x] **Elastic worker pool** — object-change bus (0.9.11)
- [x] **System runtime settings UI** — ISPF env vars in admin (0.9.12)
- [x] **TimescaleDB event journal hypertable** — P3a (0.9.18, [0015](decisions/0015-event-history-timescale.md))
- [x] **ClickHouse event journal SPI** — P3b high-throughput (0.9.19+, [0016](decisions/0016-clickhouse-event-journal.md))
- [x] **MQTT meter-bus ingest** — historian path, coalesce sweeps
- [x] **AGPL-3.0** — platform license; all drivers as optional packs ([0016-agpl](decisions/0016-agpl-dual-licensing.md))
- [x] **Load testing tooling** — [load-testing.md](load-testing.md), VPS reports
- [x] **Demostand profiles** — throughput / idle / edge ([demostands.md](demostands.md), [ADR-0033](decisions/0033-prod-idle-demostand-tuning.md))

---

## 17. Spreadsheet, audit, code audit sprint — BL

- [x] **Spreadsheet widget** — XLSX import, multi-sheet, ISPF formula engine, Yandex import
- [x] **Binding/function invoke audit** — unified journal UI
- [x] **Sprint BL-A** — correlator actions UI, workflow refs, schema editor, event catalog viewer
- [x] **Sprint BL-B** — change sets UI, edit leases, chart trim, history range
- [x] **Binding catalog + autocomplete** — 18 platform functions (BL-09)
- [x] **Network-graph widget** — Cytoscape layout (BL-10)
- [x] **System integration toggles** — Redis/NATS/ClickHouse/AI/MCP (BL-13, BL-14)
- [x] **Journal export/diff** — CSV/JSON export, before/after diff (BL-15, BL-16)
- [x] **Binding rule activators** — onEvent, periodicMs editor + runtime (BL-18)
- [x] **Alert/correlator catalog list** — Explorer list view (BL-17)
- [x] **Compile-on-save Java functions** — object tree nodes
- [x] **Driver writes** — Modbus, S7, OPC UA, BACnet, IEC104, DNP3, DLMS + runtime write API
- [x] **Chart widgets** — range, candlestick, bubble, radar
- [x] **Wave D tail** — notifications, federation sync, backup, driver polish
- [x] **Responsive operator shell** — mobile sidebar drawer
- [x] **Operator AI copilot** — scoped reports, memory, interactive clarifications
- [x] **MES demos** — defect demo, OGP events reference

---

## 18. Current state (baseline 0.9.72)

### Still in progress / ops

- [x] **Playwright e2e smoke** — mocked CI baseline (BL-50, BL-77 live testids)
- [x] **Driver stub promotion BL-26** — six STUB→BETA loopback tests
- [x] **ClickHouse variable history** — backend write/query (BL-40); prod: `ISPF_VARIABLE_HISTORY_STORE=clickhouse` **on request**
- [x] **Haystack/Brick semantic layer** — BL-56…62 Done
- [x] **Admin mobile shell** — BL-72
- [x] **Report calendar TZ** — BL-73
- [x] **AI agent rate limits** — BL-75
- [~] **Driver observedAt full matrix** — BL-74 pilots; remaining drivers demand-driven
- [~] **Playwright live staging** — workflow + secrets; expand coverage on demand

Detailed current plan: [roadmap.md](roadmap.md).

---

## 19. Principles that did not change

These decisions held throughout the evolution:

- [x] **North star** — business logic in object-tree mechanisms, not industry-specific Java ([architecture.md](architecture.md))
- [x] **Dogfooding gate** — every REQ-PF originates from app-team needs ([0002](decisions/0002-dogfooding-gate.md))
- [x] **Tree-first** — alert rules, correlators, reports, schedules, security — tree nodes
- [x] **Bundle = packaging** — deploy delivers configuration into the platform, not a separate runtime
- [x] **Generic platform / industry solution** — separation of `main` vs `examples/*`

---

## Related documents

| Document | Purpose |
|----------|---------|
| [roadmap.md](roadmap.md) | Phases 0–20, status by topic |
| [roadmap.md](roadmap.md) | Unified roadmap: REQ-PF/FW, BL-01…139, phases, sprints |
| [docs/decisions/](decisions/readme.md) | ADR — key architectural forks |
| [product.md](product.md) | Product overview for customers |

---

*Updated on major releases. Last sync with `main`: version **0.9.72**.*
