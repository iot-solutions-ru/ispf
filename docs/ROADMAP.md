# Platform Roadmap

Единый roadmap ISPF: application platform (REQ-PF), production ops и Web Console.

Статус baseline: `main`, июнь 2026.

## Phase 0 — Stabilization

| # | Тема | Статус |
|---|------|--------|
| 0.1 | GitHub Actions CI (server + web build) | Done |
| 0.2 | Gradle test memory limits | Done |
| 0.3 | PF-01c `map` / `buildRecord` | Done |
| 0.4 | PF-03 `models[]` в bundle deploy | Done |
| 0.5 | Leader lock для schedulers | Done |
| 0.6 | WebSocket auth (token query param) | Done |
| 0.7 | OperatorUi `eventJournalObjectPath` | Done |
| 0.8 | Reference app #2 (`examples/warehouse-app`) | Done |
| 0.9 | System folder list panels в Explorer | Done |

## Phase 1 — Application platform closure

| # | Тема | Статус |
|---|------|--------|
| 1.1 | Acceptance PF-01: типовой app только JSON+SQL | Done |
| 1.2 | Dogfooding warehouse + demo на одном API | Done (bundle) |
| 1.3 | PF-06 custom field labels map | Done |
| 1.4 | Bundle rollback UX в admin | Done |

## Phase 2 — Production gate

| # | Тема | Статус |
|---|------|--------|
| 2.1 | Keycloak/OIDC login в Web Console | Done |
| 2.2 | TimescaleDB hypertables + retention policies | Done |
| 2.3 | Per-object ACL (REQ новый) | Done |
| 2.4 | NATS event bus между репликами | Done |

## Phase 3 — Connectivity & HMI

| # | Тема | Статус |
|---|------|--------|
| 3.1 | Driver maturity labels (production/beta/stub) | Done |
| 3.2 | Stub-драйверы → production по demand | Done (CWMP→PRODUCTION + flexible/gps-tracker acceptance) |
| 3.3 | React Router / deep links в admin | Done |
| 3.4 | Frontend smoke tests (vitest/playwright) | Done (vitest) |
| 3.5 | Legacy operator manifest deprecation | Done (console warn) |

## Phase 4 — Scale & topology (P3+)

| # | Тема | Статус |
|---|------|--------|
| 4.1 | PF-13 federation design + spike | Done (peers + proxy read + catalog sync) |
| 4.2 | Multi-tenant tree namespaces | Done (spike: root.tenant.* + scope) |

## Phase 5 — Усиление механизмов (north star)

Следующая волна после Phase 0–4: **больше выразительности object tree**, меньше поводов для custom Java и «application layer как отдельного мира». Детали — [PLATFORM_DEVELOPER_BACKLOG.md §8.1](PLATFORM_DEVELOPER_BACKLOG.md#81-усиление-механизмов-phase-5).

| # | Механизм | Направление | Статус |
|---|----------|-------------|--------|
| 5.1 | **Модели** | Богаче bindings; наследование шаблонов; версионирование моделей | Done (`extendsModelId`, bulk upgrade API, vendor demo) |
| 5.2 | **Функции** | Больше script steps (`setVar`, `when`/`if`, …); declarative SQL bindings (PF-08); меньше поводов для custom code | Done |
| 5.3 | **События + correlators** | Сложнее паттерны (окна, цепочки, агрегации); эскалация без Java | Done (`EVENT_CHAIN`, `sequenceGapSeconds`, N-in-window demo) |
| 5.4 | **Workflow** | Больше `serviceTask` через platform primitives (`fire_event`, `read_variable`, `start_workflow`, …) | Done (BPMN properties panel + acceptance tests) |
| 5.5 | **Bundle / application layer** | Bundle = упаковка объектов дерева; tree-first invoke; reconcile `objects[]` | Done |

## Phase 6 — Post-v0.2.0 production (v0.3.0)

Волна после Phase 5: **production readiness**, закрытие хвостов REQ-PF §3, federation hardening. Целевой релиз — **v0.3.0**.

| # | Тема | Статус |
|---|------|--------|
| 6.1 | Doc sync + PF-03 deprecation | Done |
| 6.2 | Driver maturity (CWMP, flexible, gps-tracker) | Done |
| 6.3 | PF-13 federation production | Done |
| 6.4 | PF-09 virtual profiles bundle + PF-11 function rollback UI | Done |
| 6.5 | Phase 5 polish (model diff, warehouse CI, correlator cooldown) | Done |

## Phase 7 — Federation auth + outbound tunnel

| # | Тема | Статус |
|---|------|--------|
| 7.1 | Auth lifecycle (service account auto-refresh, 401 retry) | Done |
| 7.2 | Outbound WebSocket tunnel (NAT edge → public hub, full proxy) | Done |

## Phase 8 — Federation bind (REQ-PF-13c)

| # | Тема | Статус |
|---|------|--------|
| 8.1 | Bind overlay на локальный path + UI | Done |
| 8.2 | Same-path remote overlay + cycle protection | Done (v0.5.1) |
| 8.3 | Unbind restore local metadata snapshot | Done (v0.5.2) |

## Phase 10 — North Star hardening

| # | Тема | Статус |
|---|------|--------|
| 10.1 | Persistent binding state (`@bindingState`, hysteresis/deadband/movingAvg/counterRate) | Done (v0.6.0) |

## Phase 11 — Multi-user collaboration

| # | Тема | Статус |
|---|------|--------|
| 11.1 | Object revision (If-Match) + config audit + stale editor UI | Done |
| 11.2 | WS presence + subtree leases + model merge preview | Done |
| 11.3 | Subtree ownership ACL (OWNER/EDITOR/VIEWER) | Done |
| 11.4 | Change-sets + preview/apply promotion pipeline | Done |

См. [COLLABORATION.md](COLLABORATION.md).

## Phase 12 — Reports tree-first

| # | Тема | Статус |
|---|------|--------|
| 12.1 | `report-v1` model + `root.platform.reports` catalog | Done |
| 12.2 | `ReportService` + `/api/v1/reports/by-path` API | Done |
| 12.3 | Report Builder in Web Console + legacy app API facade | Done |
| 12.4 | Dashboard `report` widget + operatorUi `reports[]` | Done |

См. [REPORTS.md](REPORTS.md).

## Phase 13 — YARG server export

| # | Тема | Статус |
|---|------|--------|
| 13.1 | `com.haulmont.yarg:yarg` + `report_templates` storage | Done |
| 13.2 | `YargReportService` — PDF/XLSX/HTML from SQL + template | Done |
| 13.3 | Template upload API + Report Builder «Шаблон YARG» tab | Done |
| 13.4 | `export?format=pdf\|xlsx\|html` + widget PDF button | Done |

См. [REPORTS.md](REPORTS.md) (секция YARG templates).

## Phase 14 — Tree-first platform catalogs

| # | Тема | Статус |
|---|------|--------|
| 14.1 | `data-source-v1`, `schedule-v1`, `sql-binding-v1`, `migration-v1` models | Done |
| 14.2 | Platform catalogs: `data-sources`, `schedules`, `bindings`, `migrations` | Done |
| 14.3 | Reports: `dataSourcePath` вместо `appId`; `POST /platform/packages/import` | Done |
| 14.4 | Script functions on tree (`FunctionDescriptor.sourceBody`) | Done |
| 14.5 | Web Console: Report Builder data source picker, Package Import, hide legacy applications | Done |

Application API (`/applications/{id}/deploy`) сохранён для совместимости; runtime — только дерево объектов.

## Phase 15 — Lab Training Package

| # | Тема | Статус |
|---|------|--------|
| 15.1 | Virtual driver profile `lab` + model `virtual-lab-v1` (waves, table, events, calculate) | Done |
| 15.2 | Automation v2: alert `delaySeconds`/`sustainWhileTrue`, correlator `payloadFilterExpr`, `SET_VARIABLE`, `OPEN_OPERATOR_REPORT` | Done |
| 15.3 | Report type `tree-variables` (cross-device RECORD_LIST) | Done |
| 15.4 | Web Console widgets: `pie-chart`, `history-table`, `variable-editor`, `svg-widget`, `composite-widget`, event-feed filter | Done |
| 15.5 | Importable bundle `examples/lab-training/` + lab users/ACL bootstrap | Done |
| 15.6 | Docs [LAB_TRAINING.md](LAB_TRAINING.md), integration tests | Done |

См. [LAB_TRAINING.md](LAB_TRAINING.md), [DASHBOARDS.md](DASHBOARDS.md) (Grid Layout form example).

## Platform baseline

| # | Тема | Статус |
|---|------|--------|
| P.1 | Java 25 toolchain + CI | Done |
| P.2 | Spring Boot 4.0.7 migration | Done |
| P.3 | Jackson 3 native (`tools.jackson`) | Done |

## Связанные документы

- [PLATFORM_DEVELOPER_BACKLOG.md](PLATFORM_DEVELOPER_BACKLOG.md) — REQ-PF детали
- [APPLICATIONS.md](APPLICATIONS.md) — deploy API
- [DEPLOYMENT.md](DEPLOYMENT.md) — prod topology
- [SECURITY.md](SECURITY.md) — auth/RBAC
