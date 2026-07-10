> **Language:** Canonical English. Russian edition: [ru/operator-guide.md](../ru/operator-guide.md).

# Operator Guide

Guide for users with the **operator** role — equipment monitoring, work queue tasks, events, and reports.

Product overview: [product](product.md). UI technical details: [web-console](web-console.md).

---

## Sign In

1. Open the Web Console: `http://<host>:5173`
2. Sign in with an operator account (demo: `operator` / `operator`).
3. After sign-in, **Operator HMI** opens — full-screen mode without the object tree or editors.

If you have the admin role but need operator mode:

```
http://<host>:5173?mode=operator
```

For a specific application:

```
http://<host>:5173?mode=operator&app=platform
```

### Application auto-start

An administrator can configure **auto-start** for your account: after sign-in, the configured operator app opens immediately (for example, `platform` or an industry application).

---

## Operator HMI Layout

```
┌─────────────────────────────────────────────────────────────┐
│  [App title]                              [Logout]          │
├───────────────────────────────────────┬─────────────────────┤
│                                       │  Work Queue         │
│         Dashboard (read-only)         │  ─────────────      │
│         Widgets: values,              │  Event Journal      │
│         charts, tables                │                     │
│                                       │                     │
└───────────────────────────────────────┴─────────────────────┘
```

### What operators can do

| Action | Available |
|--------|:---------:|
| View dashboards and values | ✓ |
| Live data updates (WebSocket) | ✓ |
| Invoke functions (dashboard buttons) | ✓ |
| Work queue: claim / complete | ✓ |
| Event journal | ✓ |
| Edit layout, objects, workflow | ✗ |
| User management | ✗ |
| Application deploy | ✗ |

---

## Working with Dashboards

### Navigating between screens

If the application has multiple dashboards, they appear as **tabs** at the top of the screen. Switch tabs to move between screens (for example, “Overview”, “Details”, “Reports”).

A **dashboard-link** widget on a screen can open another dashboard — in the current tab or in a modal.

### On-screen widgets

| Widget | What it shows | Operator actions |
|--------|---------------|------------------|
| **value** / **indicator** | Current variable value | View only |
| **chart** / **sparkline** | Trend graph | View only |
| **gauge** | Scale | View only |
| **object-table** | Table of objects | Click row → selection for other widgets |
| **card-grid** | Object cards | Click → selection |
| **function-button** | Action button | Click → invoke object function |
| **work-queue** | List of BPMN tasks | Claim / Complete |
| **event-log** | Event journal | View, filter |
| **spreadsheet** | A1 grid with formulas | Enter values and formulas (if `editable`) |
| **text** / **image** | Static content | — |

Full reference for all widgets (what each setting does, examples): **[widgets](widgets.md)**.

On screens with a device table (`object-table` widget with `selectionKey`), clicking a row updates linked widgets — charts and indicators show data for the **selected** device.

### Spreadsheet widget

The **spreadsheet** widget is a table with Excel-style cell addressing (A1, B2, …). Values and formulas recalculate on screen without server round-trips (except live **binding** cells).

Detailed instructions: [spreadsheet-widget](spreadsheet-widget.md).

**Summary:**

| Element | Purpose |
|---------|---------|
| Formula bar | Selected cell address on the left; value or formula for editing on the right |
| Grid | Displayed results; in **free** mode — any cell is editable (except binding cells) |
| Toolbar | In **free** mode: undo/redo, copy/paste, CSV export |

**Selection and input (free mode):**

1. **Click** a cell — select it; contents appear in the formula bar.
2. **Double-click** or **F2** — edit directly in the cell.
3. **Formula bar** — enter a number (`10`), text, or formula (`=A1+B2`); **Enter** — save.
4. **Tab** / **Shift+Tab** — next / previous cell; **arrow keys** — move in the grid.

**Keyboard shortcuts (free mode, when focus is on the grid):**

| Keys | Action |
|------|--------|
| F2 | Edit cell |
| Enter | Open editing / after entry — move down |
| Esc | Cancel editing |
| Ctrl+Z / Ctrl+Y | Undo / redo |
| Ctrl+C / Ctrl+V | Copy / paste cell |

**Persistence:** entered values and formulas are saved automatically (dashboard session or object variable — as configured by the developer). After **F5**, data remains if the widget is bound to a variable (`persistMode: variable`).

**Lab example:** dashboard `root.platform.dashboards.lab-calculator` — template calculator (**configured** mode): only cells A2 and B2 are editable; sums recalculate automatically.

### Dynamic object selection

On screens with a device table (`object-table` widget with `selectionKey`), clicking a row updates linked widgets — charts and indicators show data for the **selected** device.

---

## Work Queue

The Work Queue holds BPMN **user tasks** assigned to operators.

### Typical flow

1. A workflow starts (manually by an administrator or triggered by an event).
2. The process reaches a user task → the task appears in the Work Queue.
3. Operator clicks **Claim** — the task is assigned to them.
4. Operator performs the action (for example, confirms on the dashboard).
5. Clicks **Complete** — the workflow continues.

### Where to look

- **Sidebar** on the right in Operator HMI — Work Queue panel.
- **work-queue** widget on a dashboard — embedded queue on the screen.

### If a task is unavailable

- The task is already **claimed** by another operator — wait for completion or contact an administrator.
- Workflow status is `STOPPED` — no new tasks are created.

---

## Event Journal

The **Event Journal** sidebar shows recent platform events:

- Sensor threshold exceeded
- Alert rule fired
- Manual event fire
- System notifications

Levels: `DEBUG`, `INFO`, `WARNING`, `ERROR`, `CRITICAL`.

An **event-log** widget on a dashboard can filter events for a specific object.

---

## Reports

SQL reports from application solutions are available via BFF or built-in operator app screens. Export format — CSV.

Example (if application `demo` is configured):

- “Reports” screen in the operator app
- Or direct API call (integrators only)

Details: [reports](reports.md).

---

## Frequently Asked Questions

### Data is not updating

- Verify the API server is running (`GET /actuator/health`).
- Refresh the page — WebSocket reconnects automatically.
- Ensure the device driver is in `RUNNING` status (configured by an administrator).

### Missing dashboard or tab

Contact an administrator — dashboards and operator UI are configured in the admin console → `root.platform.operator-apps`.

### Error when invoking a function

- Insufficient permissions — operators can invoke functions, but not all functions are allowed for the operator role.
- Check the event journal and report the error text to an administrator.

### How to return to the admin console

Only users with the **admin** role can switch:

```
http://<host>:5173?mode=admin
```

---

## Operator Agent (AI Copilot)

Operator HMI includes a **built-in AI assistant** — a read-only copilot for the shift: trends, reports, event journal, task queue. It does not change platform configuration.

Details: [ai-development](ai-development.md), BL-179.

### How to open

- Chat panel in Operator HMI (assistant icon).
- Same URL as the operator app: `?mode=operator&app=<appId>`.

An administrator sets instructions and uploads documents in the **operator app** (`root.platform.operator-apps` → Agent instructions / Knowledge base).

### Language

The assistant responds **in the operator's language** (Russian / English). Application memory (`remember_app_memory`) automatically uses ru/en headers in prompts.

### Available tools (read-only)

| Tool | Purpose |
|------|---------|
| `get_operator_scope` | Allowed application path prefixes |
| `list_objects` / `get_object` / `search_objects` | Object overview within scope |
| `list_variables` / `describe_variables` | Current values and schema |
| `get_variable_history` / `get_variable_trend` | Historian and aggregates |
| `list_events` / `list_event_catalog` | Journal and event catalog |
| `list_reports` / `run_report` | Reports allowed for the application |
| `invoke_bff` / `invoke_tree_function` | Operational functions (read-only data) |
| `list_work_queue` | Open BPMN tasks |
| `list_app_memory` / `remember_app_memory` | Long-term shift memory (glossary, norms) |
| `list_app_documents` / `read_app_document` / `search_app_documents` | Uploaded SOPs/instructions |
| `get_operator_link` | Link to a dashboard or report in HMI |
| `get_dashboard_layout` / `list_automation` | View layout and automation (no writes) |

**Forbidden in operator mode:** `create_object`, `configure_driver`, `import_package`, `configure_alert`, `set_dashboard_layout`, and any mutating tools.

### Typical requests

| Operator request | What the agent does |
|------------------|---------------------|
| “What is the temperature on line 2?” | `list_variables` → answer with current value |
| “Show pressure trend for the last 24 hours” | `get_variable_trend` |
| “Run the shift report” | `list_reports` → `run_report` → brief numeric summary |
| “What is in the task queue?” | `list_work_queue` |
| “Remember: GPU-03 — line 2” | `remember_app_memory` |

### Application memory

- Shared by all operators of one `appId`.
- Types: `fact`, `glossary`, `preference`, `playbook`, `correction`.
- Phrases like “remember…” and corrections — the agent saves via `remember_app_memory`.

---

## Related Documents

- [product](product.md) — product overview
- [dashboards](dashboards.md) — layout, selectionKey, navigation
- [widgets](widgets.md) — reference for all widgets (settings and examples)
- [workflows](workflows.md) — how tasks reach the work queue
- [security](security.md) — roles and access rights
