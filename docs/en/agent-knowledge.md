> **Language:** Canonical English. Russian edition: [ru/agent-knowledge.md](../ru/agent-knowledge.md).

# AGENT_KNOWLEDGE — ISPF internal agent reference

> **Status:** Internal — Agent routing map. Hub: [doc-status.md](doc-status.md).

Reference document for the **tree-first agent**, AI Studio, and MCP clients. It describes **all approaches to building applications/solutions** and provides a **documentation map** for the platform.

**How to read this:** `search_context(query=..., topic=...)` returns full slices from ContextPack. This file is a **router**: what to choose and where to go next.

See also [application-principles](application-principles.md) (canonical P1-P10 set), [ai-development](ai-development.md), [0001-app-platform-boundary](decisions/0001-app-platform-boundary.md), [0005-tree-first-ai-agent](decisions/0005-tree-first-ai-agent.md), [0051-poka-yoke-constraints-over-guards](decisions/0051-poka-yoke-constraints-over-guards.md).

---

## Target approach

Canonical statement and expanded rules: [application-principles](application-principles.md).

1. **Business logic lives in object-tree mechanisms**, not in Java in `ispf-server` and not in platform React code.
2. **One runtime:** invoke, workflow, alerts, dashboards, and bindings go through the **object tree** and platform API.
3. The **application record** (`applications` table) is a **registry + isolated SQL schema**, not a parallel engine.
4. **Bundle deploy** delivers declarative config into the tree; after deploy, everything is addressed via tree paths.
5. **The agent does not write Java/React to `main`:** only validated JSON artifacts (bundle, layout, rules, models) and platform tool calls.

Forbidden: platform Flyway for app tables, hardcoded BFF routes, domain Java in server runtime.

---

## Application creation approaches (AUTHOR / SHIP variants)

**Canonical selection is [application-principles P7](application-principles.md):** four layers — **AUTHOR → SHAPE → SHIP → PROMOTE** — not five peer “ways to build an app.”

| Layer | Mechanism | This page |
|-------|-----------|-----------|
| AUTHOR | Admin UI or Agent | Rows **A**, **B**, **E** (draft), **G** |
| SHAPE | Blueprint | [blueprints](blueprints.md); bundle `models[]` |
| SHIP | Bundle | Rows **C**, **D**, **E** (import), **F**, **H** |
| PROMOTE | Change set | [collaboration](collaboration.md) § change-sets — not a greenfield bootstrap path |

A–H below are **tooling detail** under AUTHOR/SHIP. Resolve the layer in P7 first, then pick a row.

| # | Approach | Layer | When to use | Delivery | Operator UI |
|---|----------|-------|-------------|----------|-------------|
| **A** | **Tree-first (agent tools / Explorer)** | AUTHOR | Demo, SNMP, lab, fast POC, interactive setup with a user | `create_object`, `set_variable`, `configure_driver`, dashboard tools | `configure_operator_ui` |
| **B** | **Admin Console (manual assembly)** | AUTHOR | Engineer-driven setup without a bundle, iterative HMI work | UI: Models, Dashboard Builder, Inspector | Operator Apps panel |
| **C** | **Bundle deploy (manifest)** | SHIP | Production solution, CI/CD, repeatable release | `POST .../applications/{id}/deploy` or `import_package` | `operatorUi` in manifest |
| **D** | **Step-by-step REST API** | SHIP | Automation without ZIP, staged integration | register -> migrate -> functions -> deploy sections | `PUT operator-apps/.../ui` |
| **E** | **AI Studio (generate -> validate -> import)** | AUTHOR→SHIP | Draft manifest from prompt | `validate_bundle` -> `dry_run_deploy` -> `import_package` | from generated `operatorUi` |
| **F** | **Reference example** | SHIP | Training, MES/lab baseline | `get_example_bundle` -> adapt -> import | from example manifest |
| **G** | **Platform HMI only** | AUTHOR | Monitoring only, no app schema | dashboards + binding rules on tree | built-in `platform` operator app |
| **H** | **Commercial bundle** | SHIP | Licensed solution | signed bundle + license gate | same as C |

### Decision tree (short — mirrors P7)

```
Need isolated app SQL and/or repeatable release?
  |- YES -> SHIP: C/D/E/F/H (+ migrations[] when SQL)
  `- NO  -> AUTHOR: A/B/G (tree-only; SHAPE via blueprints for typed objects)

Typed object structure (variables/events/functions)?
  `- SHAPE -> blueprint apply / models[] — not hand-duplicated each time

Promote / review already-authored ops?
  `- PROMOTE -> change set preview -> apply (not greenfield)

Interactive AI Studio session without SQL/CI?
  `- AUTHOR A preferred; if later shipping a bundle: gates before import
```

---

## SIF — Specification Intake Framework

Universal intake for **any** assignment (full spec, short prompt, follow-up). The pump station is a reference fixture, not a special case.

### Pipeline (complex assignments)

1. **Classify** -> `assignmentType`; **decompose implicit phrases** -> `specBrief` (entities, FR-* with `sourcePhrase`)
2. **Discover** -> recipes, profiles, `list_objects`, `get_automation_schema`
3. **Scope** -> `intent_scope` maps FR to layers; `assumptions[]` for inferred details
4. **Gap matrix** -> FR -> capability (`full` / `out_of_scope`)
5. **Questions** -> <=3 per turn; user may answer multiple in one chat turn
6. **Plan** -> `plan.sections[]` incrementally (<=2 per turn) -> **SYNTHESIS** enriches sections -> approve when analytical gate is OK

UI: `executiveSummary`, FR table in `specBrief`, `gapMatrix`, and `planCompletenessGaps` list before "Approve complete plan."

**Fast path:** `monitoring_lab`, `explore_readonly`, `follow_up` — no 5-turn torture.

### Domain adapters

| Adapter | Playbook | Template |
|---------|----------|----------|
| `industrial_oil_gas` | `virtualPumpStation()` + abbrev lexicon | `scada-facility-overview` |
| `snmp_lab` | SNMP playbooks | `snmp-host-monitoring` |
| `mes_terminal` | `mesReferenceLifecycle()` | — |
| `_default` | `projectBlueprintGuide()` | `monitoring-overview` |

### Guards and preflight

- **Approval:** "Yes, start", "Approved", primary suggestion -> execute without re-plan
- **Preflight:** before mutation, emit hint + `suggestedDiscovery` if parent/path is not grounded
- **Path casing:** case-insensitive match with hint "Use exact path: ..."
- **Finish:** block on ERROR in turn, empty mimic, dashboard without widgets, chart without history
- **Judge:** pre-finish verdict `approve | rework | gap_required | user_moderation_required`

### Reference fixtures (tests)

- Pump station technical spec -> `industrial_facility`
- "SNMP localhost monitoring" -> `monitoring_lab` fast path
- "Deploy MES demo" -> `application_bundle`

See `AgentPlaybooks.specIntakeGuide()` and `SpecIntakeScenarioTest`.

---

## A. Tree-first (agent tools)

**Core idea:** assemble a solution **on the live tree** without ZIP deploy. Fits requests like "create SNMP localhost and dashboard", virtual lab setup, or hub alerts.

**Typical sequence:**

1. `list_object_models` -> `create_object` (DEVICE / DASHBOARD / CUSTOM / WORKFLOW / ...)
2. `configure_driver` + `driver_control start` (for DEVICE)
3. `configure_variable_history` (for chart/sparkline)
4. `create_variable` / binding rules (`set_variable` + CEL) on CUSTOM hub
5. `configure_alert` / `configure_correlator`
6. `create_object DASHBOARD` -> `set_dashboard_layout template=...` or `add_dashboard_widget`
7. Platform rules (ADR-0019): binding rules on DASHBOARD with `target.kind=context`, `onContextChange`
8. `configure_operator_ui` -> default dashboard + menu
9. `list_variables` -> `finish` with UI paths

**Playbooks in system prompt:** SNMP, virtual cluster, Modbus, MES, reports, widgets, SCADA mimic — see `AgentPlaybooks.*`.

### Project blueprint (8 layers)

A complete tree-first project has **8 layers** (see `get_automation_schema topic=projectBlueprint`):

| # | Layer | Path | Tools |
|---|------|------|-------|
| 1 | Hub (ABSOLUTE) | `root.platform.instances.{project}` | `ensure_absolute_instance`, binding rules, PlatformRef |
| 2 | Devices | `root.platform.devices.{project}/*` | `instantiate_instance_type`, `apply_relative_model`, `create_virtual_device` |
| 3 | Dashboard | `root.platform.dashboards.{project}-*` | `set_dashboard_layout`, `add_dashboard_widget` |
| 4 | SCADA | `root.platform.mimics.{project}-*` | `save_mimic_diagram`, `get_mimic_diagram` |
| 5 | Alerts | `root.platform.alert-rules.{project}-*` | `configure_alert` |
| 6 | Correlators | `root.platform.correlators.{project}-*` | `configure_correlator` |
| 7 | Workflows | `root.platform.workflows.{project}-*` | `save_workflow_bpmn` |
| 8 | Reports | `root.platform.reports.{project}-*` | `configure_report` |

**Three model kinds:** `get_automation_schema topic=instanceTypes` -> RELATIVE (mixin on existing object), INSTANCE (new object), ABSOLUTE (singleton hub).

**Recipes catalog (1410):** `search_platform_recipes`, `get_automation_schema topic=recipes|projects|recipe/{id}`. Includes **500** ready industry projects (`project-{industry}-{archetype}`). Full index: [agent-recipes](agent-recipes.md).

**Finish guard:** the agent cannot `finish` without `list_variables` on DEVICE, `configure_alert` for monitoring intent, and `get_mimic_diagram elementCount>0` for SCADA intent.

**Do not use:** `set_variable name=widgets` on dashboard; layout must be in variable `layout`.
**SCADA:** use `save_mimic_diagram` / `add_mimic_elements`, not `set_variable name=diagram`.

### Full agent tools catalog (admin, ~85 tools)

| Area | Tools |
|------|-------|
| Discovery | `search_context`, `list_drivers`, `get_driver_help`, `list_examples`, `get_example_bundle`, `get_widget_catalog`, `list_applications` |
| Object tree | `list_objects`, `get_object`, `create_object`, `delete_object`, `search_objects`, `search_by_haystack_tags`, `list_object_models` |
| Variables | `list_variables`, `describe_variables`, `set_variable`, `create_variable`, `configure_variable_history` |
| Drivers | `configure_driver`, `driver_control` |
| Bindings | `create_binding_rule`, `list_binding_rules`, `configure_platform_context_rule` |
| Dashboards | `get_dashboard_layout`, `set_dashboard_layout`, `add_dashboard_widget` |
| SCADA | `list_mimic_symbols`, `save_mimic_diagram`, `add_mimic_elements`, `get_mimic_diagram` |
| Reports | `list_reports`, `get_report_schema`, `run_report`, `configure_report` |
| Automation | `configure_alert`, `configure_correlator`, `list_automation`, `get_automation_schema` |
| Workflows | `get_workflow`, `save_workflow_bpmn`, `run_workflow`, `update_workflow_status`, `list_workflow_instances`, `signal_workflow_instance`, `cancel_workflow_instance` |
| Applications | `register_application`, `application_data_*`, `deploy_app_binding`, `deploy_app_function`, `validate_bundle`, `dry_run_deploy`, `import_package`, `export_application_bundle`, `rollback_application_deploy`, `pull_application_from_tree` |
| Functions/events | `list_functions`, `get_function`, `invoke_bff`, `invoke_tree_function`, `fire_event`, `list_events`, `list_event_catalog`, `get_event_schema` |
| Tree functions | `get_function_template`, `deploy_tree_function` (script **or java**), `invoke_tree_function`; app BFF: `deploy_app_function` (script) |
| Operator HMI | `configure_operator_ui` |
| Platform | `list_platform_schedules`, `configure_platform_schedule`, `resolve_timezone`, `export_haystack` |
| Models | `list_relative_models`, `list_instance_types`, `list_absolute_models`, `get_object_model`, `apply_relative_model`, `instantiate_instance_type`, `ensure_absolute_instance`, `create_virtual_device` |
| Recipes | `search_platform_recipes`, `get_automation_schema topic=recipes|projects|recipe/{id}|projectBlueprint|instanceTypes` |

Docs: [dashboards](dashboards.md), [drivers](drivers.md), [bindings](bindings.md), [platform-logic](platform-logic.md), [automation](automation.md).

---

## B. Admin Console (manual assembly)

**Core idea:** an engineer builds the solution in Web Console without agent automation and without a bundle.

| Task | UI |
|------|----|
| Device model | Explorer -> Models -> Model Editor |
| Device | Explorer -> Devices -> + Object, Inspector -> Driver |
| HMI | Dashboard Builder (Editor -> widgets / Rules) |
| Bindings / CEL | Inspector -> Bindings |
| BPMN | Workflow Builder (+ cancel/signal for active instance) |
| Operator shell | `root.platform.operator-apps` -> Operator Apps Panel |
| Deploy app (bundle) | `APPLICATION` -> Inspector -> **Deploy** -> Bundle + history + rollback |
| App lifecycle (REST D) | `APPLICATION` -> Deploy -> **Application lifecycle** (migrate/seed/status, bindings, reports, function deploy) |
| Platform schedules (DB) | **System -> App schedules** (`GET/POST /api/v1/schedules`) |
| Semantic export | **System -> Semantic export** (Haystack JSON, Brick JSON-LD/Turtle) |
| Federation bind | Inspector -> Federation; peers -> FederationPeersPanel |
| Timezone (device) | Inspector DEVICE -> resolved TZ badge (`GET /platform/timezone/resolve`) |
| User timezone | Header -> TimezoneSwitcher (`PATCH /auth/me/timezone`) |

Docs: [web-console](web-console.md), [operator-guide](operator-guide.md).

---

## Web Console <-> Platform API (prod **0.9.60**, June 2026)

Observed **~100% parity** for admin/operator scenarios. Detailed registry: [roadmap.md § Part I](roadmap.md).

### Application platform (`/api/v1/applications/{appId}/...`)

| API | UI |
|-----|----|
| `POST /applications` | CreateObjectDialog (+ APPLICATION) |
| `POST .../deploy`, rollback, history | ApplicationBundlePanel, ApplicationDeployPanel |
| `GET .../export`, validate, pull-from-tree | ApplicationBundlePanel |
| `POST .../data/migrate`, `.../data/seed`, `GET .../data/status` | ApplicationLifecyclePanel |
| `GET/POST .../bindings/*` | ApplicationLifecyclePanel |
| `GET/POST .../reports/deploy`, run/export | ApplicationLifecyclePanel + operator manifest (legacy path) |
| `POST .../functions/deploy`, versions, rollback | ApplicationLifecyclePanel + ApplicationDeployPanel |
| `GET .../events` | ApplicationDeployPanel (event catalog) |
| `GET .../operator-ui`, `.../hmi-ui` | useOperatorAppsRegistry (fallback) |

### System admin

| API | UI |
|-----|----|
| `GET/POST /api/v1/schedules` | System -> App schedules |
| `GET .../platform/haystack/export`, `.../brick/export` | System -> Semantic export |
| `GET/POST .../platform/backup/*` | System -> Metrics -> Platform backup |
| Change sets, runtime settings, journals | SystemView tabs |
| `GET /ai/models`, `/ai/provider` | AI Studio -> Settings |

### Runtime / automation

| API | UI |
|-----|----|
| `POST /workflows/instances/{id}/cancel\|signal` | WorkflowBuilder -> Instance panel |
| `POST /federation/proxy/.../functions/invoke` | InvokeFunctionDialog (federated bind) |
| Federated variable write | Inspector Save -> `PUT /objects/.../variables` (server proxy) |
| `POST /bff/invoke` | Operator manifest screens |
| Haystack tag search | HaystackBindDialog (dashboard), HaystackMetadataPanel (device) |

### Intentionally without admin UI (API / MCP / ops)

| API | Used by |
|-----|---------|
| `POST /api/v1/ai/mcp` | External MCP clients (Cursor, SDK) |
| `GET /api/v1/platform/installation-id` | Diagnostics / scripts |
| Step-by-step REST D without bundle | Agent, CI, curl — mirrors UI Application lifecycle |

**Prod:** ${ISPF_BASE_URL:-https://ispf.example.invalid} — `0.9.60`, `ISPF_BOOTSTRAP_FIXTURES_ENABLED=false` (demo fixtures only after `vps-factory-reset.sh --fixtures`).

---

## C. Bundle deploy (declarative manifest)

**Core idea:** one JSON/ZIP manifest defines the whole solution. This is the production **target approach**.

**Manifest sections** (see ContextPack `bundleManifest.fields`):

| Section | Purpose |
|--------|---------|
| `objects[]` | Tree nodes (reconcile on redeploy) |
| `models[]` | Blueprints |
| `dashboards[]` | layout JSON for DASHBOARD paths |
| `workflows[]` | BPMN + triggers |
| `migrations[]` | SQL in app schema (not platform Flyway) |
| `functions[]` | Script functions -> tree `{appId}.functions.*` |
| `bindings[]` | SQL bindings app schema -> variables |
| `reports[]` | SQL reports |
| `schedules[]` | Periodic invoke_function |
| `alertRules[]` / `correlators[]` | Automation nodes |
| `events[]` | Event catalog |
| `operatorUi` | Operator HMI menu (preferred) |
| `operatorManifest` | **Legacy** - deprecated |

**API:**

- `POST /api/v1/applications/{appId}/deploy` - monolithic JSON
- `POST /api/v1/platform/packages/import` - ZIP (agent: `import_package`)
- Gates: `validate_bundle` -> `dry_run_deploy` -> import

**After deploy:** invoke via `POST /bff/invoke` or `objects/by-path/functions/invoke` by tree path; SQL via `sqlBinding('appId','var')`.

Docs: [applications](applications.md), [solution-developer-public-api](solution-developer-public-api.md), [solution-developer-guide](solution-developer-guide.md).

---

## D. Step-by-step REST API

**Core idea:** same capabilities as bundle deploy, but delivered **in parts** for scripts and phased integration.

```text
1. POST /applications          - register appId, schemaName, tablePrefix
2. POST .../data/migrate       - SQL migrations
3. POST .../data/seed          - demo seed profiles
4. GET  .../data/status        - applied migration version
5. POST .../functions/deploy   - script functions
6. POST .../bindings/deploy    - SQL -> variable sync (+ refresh)
7. POST .../reports/deploy     - reports
8. POST .../deploy             - full bundle (or only missing sections)
9. PUT  /operator-apps/{id}/ui - operator menu
10. GET/POST /api/v1/schedules - platform_schedules (separate from tree SCHEDULE objects)
```

**Web Console (0.9.62):** steps 2-7 are available in Inspector -> APPLICATION -> Deploy -> **Application lifecycle**; agent tools include `register_application`, `application_data_migrate`, `deploy_app_binding`, `deploy_app_function`, ...

Docs: [applications](applications.md), [api](api.md).

---

## E. AI Studio (generate -> validate -> import)

**Core idea:** LLM generates manifest JSON; platform gates verify before DB writes.

1. AI Studio -> "Bundle package" or agent tool chain
2. `validate_bundle` / `dry_run_deploy`
3. `import_package` (agent) or UI "Publish"

**Policy:** `generationPolicy` in ContextPack controls allowed/forbidden artifacts.

Docs: [ai-development](ai-development.md), [0004-ai-artifact-generation-gates](decisions/0004-ai-artifact-generation-gates.md).

---

## F. Reference examples

| Example | appId | Purpose |
|---------|-------|---------|
| `examples/mes-reference/` | mes-reference | MES orders, BFF functions, workflows |
| `examples/lab-training/` | lab-training | Virtual lab, PF-15 exercises |
| `examples/demo-app/` | demo-app | Minimal bundle |
| `examples/warehouse-app/` | warehouse-app | Warehouse pattern |
| `examples/mini-tec/` | — | Bootstrap demo (fixtures) |
| Bootstrap `tank-farm-demo` | tank-farm-demo | Anonymized tank-farm SCADA mimic (fixtures) |
| Bootstrap `pipeline-scada` | pipeline-scada | RD-029 screen forms (15 mimics, fixtures) |

Agent tools: `list_examples`, `get_example_bundle(appId)`.

Walkthroughs: [reference-mes-walkthrough](reference-mes-walkthrough.md), [lab-training](lab-training.md), [reference-mini-tec-walkthrough](reference-mini-tec-walkthrough.md).

### Bootstrap SCADA demos (fixtures, approach G)

With `ispf.bootstrap.fixtures-enabled=true`, the server creates ready-to-use mimics and HMI screens. **Do not use real company names** in demo text or paths.

| Demo | appId | Devices | Mimic | Dashboard |
|------|-------|---------|-------|-----------|
| Tank farm | `tank-farm-demo` | `root.platform.devices.tank-farm-demo.*` | `root.platform.mimics.tank-farm-demo` | `root.platform.dashboards.tank-farm-hmi` |
| SDKU RD-029 | `pipeline-scada` | `root.platform.devices.pipeline-scada.*` | `root.platform.mimics.pipeline-rp` (+ 14 forms `pipeline-*`) | `root.platform.dashboards.pipeline-scada-hmi` |
| Mini-TEC SLD | — | mini-tec devices | `root.platform.mimics.mini-tec-single-line` | `root.platform.dashboards.mini-tec-single-line` |

**Code and re-export:**

| Demo | TypeScript | Export |
|------|------------|--------|
| tank-farm | `apps/web-console/src/scada/templates/buildTankFarmMimic.ts` | `npx tsx src/scada/templates/exportTankFarmMimic.ts` |
| pipeline-scada | `apps/web-console/src/scada/templates/pipeline-scada/` | `npx tsx src/scada/templates/pipeline-scada/exportPipelineScadaMimics.ts` |

Java bootstrap: `TankFarmPlatformBootstrap`, `PipelineScadaPlatformBootstrap`. Playbook: `AgentPlaybooks.scadaMimicGuide()`.

Operator URL: `?mode=operator&app=tank-farm-demo&dashboard=root.platform.dashboards.tank-farm-hmi`.

**Note:** path `root.platform.mimics.tank-farm-demo` can be overwritten by the RP diagram when `pipeline-scada` is bootstrapped at the same time (deprecated alias). For full SDKU, use `pipeline-rp` + `pipeline-scada-hmi`.

---

## G. Platform HMI without application schema

**Core idea:** monitoring **without** separate appId/SQL, using only the platform tree.

- Devices + drivers + binding rules + dashboards
- Demo rules on `snmp-host-monitoring` (Platform rules, `@dashboardContext`)
- Operator mode: `?mode=operator&app=platform`

Not required: app registration, migrations, bundle functions — if there are no app-specific tables.

---

## H. Commercial bundle

Signed manifest + `license` block. Deploy through the same import flow with RSA verification.

Docs: [commercial-licensing](commercial-licensing.md), [0003-commercial-bundle-licensing](decisions/0003-commercial-bundle-licensing.md).

---

## Where to express logic (do not duplicate)

| Task | Mechanism | Doc |
|------|-----------|-----|
| Variable computation | CEL / platform bindings / binding rules | [bindings](bindings.md) |
| Dashboard UI behavior (show/hide, mode) | Platform Rule -> `@dashboardContext` | [platform-logic](platform-logic.md), [dashboards](dashboards.md) |
| Threshold -> event | ALERT node + CEL | [automation](automation.md) |
| Event pattern -> workflow | Correlator | [automation](automation.md) |
| Process with operator tasks | BPMN WORKFLOW | [workflows](workflows.md) |
| CRUD over app SQL schema | Script function (steps) | [applications](applications.md), [object-functions](object-functions.md) |
| SQL -> variable polling | `sqlBinding` / `bindings[]` | [applications](applications.md) |
| Device telemetry | Driver + point mappings | [drivers](drivers.md) |
| HMI table | Dashboard widget `object-table` + `selectionKey` | [dashboards](dashboards.md), [widgets](widgets.md) |
| Mimic / P&ID | `MIMIC` object + `scada-mimic` widget | [scada](scada.md) |
| Legacy mini-DSL in widget | **Deprecated** -> Platform rules | [platform-logic](platform-logic.md) § legacy |

---

## Operator UI source order (priority)

1. `GET /operator-apps/{appId}/ui` - table + tree `root.platform.operator-apps`
2. `operatorUi` in bundle deploy
3. Auto-generated from `dashboards[]` in bundle
4. Legacy `public/operator-apps/{appId}.ui.json` (dev only)

URL: `?mode=operator&app={appId}&dashboard={path}`.

---

## Documentation map (full index)

Use `search_context` with `topic` or keywords from this table.

### Product and solution engineering

| Doc | topic / keywords | Content |
|-----|------------------|---------|
| [product](product.md) | product | Product overview, scenarios |
| [application-principles](application-principles.md) | application-principles | P1-P10 set, target approach, anti-patterns |
| [solution-developer-guide](solution-developer-guide.md) | solution, applications | Solution lifecycle, 6 steps |
| [solution-developer-public-api](solution-developer-public-api.md) | public-api, bundle | Stable manifest contract |
| [applications](applications.md) | applications, bff, bundle | REQ-PF: deploy, functions, SQL, schedules |
| [glossary](glossary.md) | glossary | Terms |

### Object tree and logic

| Doc | topic | Content |
|-----|-------|---------|
| [architecture](architecture.md) | architecture | Principles, layers |
| [object-model](object-model.md) | object-model, features | Tree, types, API |
| [blueprints](blueprints.md) | models | Blueprints, templateId |
| [object-functions](object-functions.md) | functions | script/java handlers, invoke |
| [bindings](bindings.md) | bindings, cel | CEL, platform bindings |
| [platform-logic](platform-logic.md) | platform-logic, rules | Platform Rule, dashboard context |
| [variable-history](variable-history.md) | historian | Time-series |

### HMI and operator

| Doc | topic | Content |
|-----|-------|---------|
| [dashboards](dashboards.md) | dashboards | Layout, selectionKey, Rules tab |
| [scada](scada.md) | scada, mimic | Mimic diagrams, MIMIC, bindings, editor (align, resize, snap) |
| [scada-mimic](scada-mimic.md) | scada-mimic | diagramJson v2, mimic REST API |
| [widgets](widgets.md) | widgets | Widget catalog, JSON fields |
| [spreadsheet-widget](spreadsheet-widget.md) | spreadsheet | ISPF(), sheet config |
| [operator-guide](operator-guide.md) | operator | HMI, work queue |
| [web-console](web-console.md) | web-console | Admin UI |

### Automation and integration

| Doc | topic | Content |
|-----|-------|---------|
| [automation](automation.md) | automation, workflows | Alerts, correlators, events |
| [workflows](workflows.md) | workflows | BPMN, ISPF extensions |
| [messaging](messaging.md) | messaging, features | NATS, WS, events |
| [federation](federation.md) | federation | Remote peers |
| [drivers](drivers.md) | drivers | 58 drivers, config |
| [reports](reports.md) | reports | SQL reports, export |

### AI, deploy, ops

| Doc | topic | Content |
|-----|-------|---------|
| [ai-development](ai-development.md) | ai, agent | Agent, ContextPack, MCP |
| **[agent-knowledge](agent-knowledge.md)** (this file) | agent-knowledge | App approaches, index |
| [api](api.md) | api | REST endpoints |
| [deployment](deployment.md) | deployment | Docker, environment |
| [security](security.md) | security | RBAC, Keycloak |
| [testing](testing.md) | testing | Tests |
| [load-testing](load-testing.md) | loadtest | Throughput baselines |
| [observability](observability.md) | observability | Prometheus, diagnostics API, metrics probe |
| [cluster](cluster.md) | cluster | Multi-replica, ADR-0029 live sync, diagnostics fan-out |

### Ops and platform runtime (agent use during load/prod triage)

| Doc | When |
|-----|------|
| [observability](observability.md) | CPU spike, queue backlog, Prometheus/OTLP, **Load diagnostics** UI |
| [cluster](cluster.md) | Multiple replicas, RAM desync, driver locks, `cluster/diagnostics` |
| [load-testing](load-testing.md) | Baselines, probe device, load-test scripts |

**Load diagnostics (admin):** `GET /api/v1/platform/cluster/diagnostics` -> all replicas; expand -> threads, drivers (`pressureScore`), jobs. Metrics probe: `PUT /api/v1/platform/diagnostics/metrics-probe` -> sync to `root.platform.devices.platform-metrics-probe` (enable only during tests).

### Reference walkthroughs

| Doc | topic |
|-----|-------|
| [lab-training](lab-training.md) | lab, training |
| [reference-mes-walkthrough](reference-mes-walkthrough.md) | mes |
| [reference-mes-defect-walkthrough](reference-mes-defect-walkthrough.md) | mes, defect |
| [reference-mes-ogp-events-walkthrough](reference-mes-ogp-events-walkthrough.md) | mes, ogp |
| [reference-mini-tec-walkthrough](reference-mini-tec-walkthrough.md) | mini-tec |

### ADR (architectural decisions)

| ADR | Topic |
|-----|-------|
| [0001-app-platform-boundary](decisions/0001-app-platform-boundary.md) | Platform vs solution |
| [0004-ai-artifact-generation-gates](decisions/0004-ai-artifact-generation-gates.md) | AI validation gates |
| [0005-tree-first-ai-agent](decisions/0005-tree-first-ai-agent.md) | Tree-first agent |
| [0006-mcp-agent-tool-adapter](decisions/0006-mcp-agent-tool-adapter.md) | MCP |
| [0010-binding-rules-only](decisions/0010-binding-rules-only.md) | Binding rules only |
| [0019-platform-rule-unification](decisions/0019-platform-rule-unification.md) | Platform Rule / dashboard |
| [0020-time-and-timezones](decisions/0020-time-and-timezones.md) | Time and timezones (UTC storage, user/device TZ) |
| [decisions/readme.md](decisions/readme.md) | Full ADR list |

### Platform evolution (for context, not generation)

| Doc | Purpose |
|-----|---------|
| [platform-evolution](platform-evolution.md) | Version history |
| [roadmap](roadmap.md) | Product roadmap |
| [roadmap](roadmap.md) | REQ-PF/FW status, BL, sprints |

---

## `search_context` recommended topics

| topic | When |
|-------|------|
| `application-principles` | Target approach, P1-P10, P7 creation stack |
| `poka-yoke` | ADR-0051: constraints over guards; schemas before native FC |
| `agent-knowledge` | AUTHOR/SHIP variants A-H under P7, docs map |
| `applications` | Bundle, BFF, migrations, functions |
| `public-api` | Manifest contract |
| `solution` | Solution developer lifecycle |
| `dashboards` | Widgets, layout, platform rules |
| `scada` | Mimic diagrams, MIMIC objects, `scada-mimic` widget |
| `platform-logic` | Context, visibility, CEL rules |
| `bindings` | CEL, `counterRate`, `read`/`call`/`fire`, PlatformRef |
| `drivers` | SNMP, Modbus, virtual, MQTT |
| `workflows` | BPMN, instance cancel/signal |
| `automation` | Alerts, correlators |
| `semantic` | Haystack/Brick export, device metadata |
| `timezones` | User TZ, device TZ resolve (ADR-0020) |
| `web-console` | Admin UI, System tabs, Application lifecycle |
| `observability` | Metrics, diagnostics, probe device, Prometheus |
| `cluster` | Multi-replica, live sync, cluster health/diagnostics |
| `object-model` | Tree types, variables |
| `ai` | Agent tools, ContextPack |
| `all` | Broad search across docChunks |

---

## Agent checklists

### "Create application / solution" (with SQL)

1. Confirm appId and whether operator UI is required
2. `search_context topic=application-principles` + `topic=agent-knowledge`; use `get_example_bundle` if similar to MES/lab
3. Register app (or include in bundle) -> migrations -> functions -> objects/dashboards
4. `validate_bundle` -> `dry_run_deploy` -> `import_package`
5. Run `configure_operator_ui` if not defined in manifest
6. `finish` with `?mode=operator&app=...` and dashboard paths

### "Create monitoring / SNMP / dashboard" (without app schema)

1. Tree-first playbook (SNMP / virtual cluster)
2. Driver + dashboard template
3. Add platform rules when needed (detail mode, widget visibility)
4. `configure_operator_ui` for platform app

### "Add automation"

1. `get_automation_schema`
2. CUSTOM hub + `create_variable` or alert on DEVICE
3. `configure_alert` / `configure_correlator`
4. Optional: WORKFLOW + `operatorAppId`

### "Create / update mimic diagram" ([scada](scada.md))

1. `get_automation_schema topic=scada` or `list_mimic_symbols`
2. `create_object` `type=MIMIC` under `root.platform.mimics`
3. **`save_mimic_diagram`** with non-empty `elements[]` (tank, valve, label, pipe, ...) - mandatory
4. `get_mimic_diagram` - verify `elementCount > 0`
5. `list_variables` on devices -> bindings in symbols
6. DASHBOARD + `add_dashboard_widget type=scada-mimic mimicPath=...`
7. **Anonymization:** no real companies, personal names, or geo labels in demos

### "Do not break platform" (see [application-principles](application-principles.md) P2, P10)

- Do not invent REST paths - use tools only
- Import bundle only after validate + dry_run OK in the same run
- Do not use platform Flyway for app tables
- Prefer `operatorUi` over legacy `operatorManifest`

---

## ContextPack and MCP resources

- Build: `python tools/ai-pack/build.py` -> `ai/context-pack.json`
- MCP: `contextpack://doc-chunks`, `contextpack://feature-index`, `contextpack://example-summaries`
- Briefing: `PlatformBriefingService` - drivers, examples, live tree snapshot

---

*Update this file when new approaches are added (ADR, REQ-PF), when Web Console UI<->API coverage expands, and when docChunks are rebuilt (`python tools/ai-pack/build.py`).*

**Last knowledge update:** 2026-06-30 - UI<->API parity ~100% (Application lifecycle, platform schedules, semantic export, workflow instance control, federation proxy invoke, device TZ resolve); prod **0.9.60**.
