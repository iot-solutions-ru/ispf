> **Language:** Canonical English. Russian edition: [ru/lab-training.md](../ru/lab-training.md).

# Lab Training — 18 exercises

> **Status:** Lab — Training sample packs. Hub: [doc-status.md](doc-status.md).

The **Lab Training** package demonstrates Ignition-style Virtual Device, automation, reports, and ISPF dashboards. All objects import from `examples/lab-training/bundle.json`.

## Quick start

1. Start server and Web Console (profile `local`).
2. Import bundle into **Application** `lab-training` (`packageId` = `appId`):

```http
POST /api/v1/platform/packages/import?packageId=lab-training
Content-Type: application/json

<contents of examples/lab-training/bundle.json>
```

Legacy API equivalent:

```http
POST /api/v1/applications/lab-training/deploy
Content-Type: application/json

<same bundle.json>
```

On import platform:

| Step | Result |
|------|--------|
| Registration | entry in application registry (`applications`) |
| Application in tree | `root.platform.applications.lab-training` (model `application-v1`) |
| Data source | `root.platform.data-sources.lab-training` |
| Operator HMI | `root.platform.operator-apps.lab-training` (from bundle `operatorUi` section) |
| Bundle contents | dashboards, reports, alert-rules, correlators → `root.platform.*` catalogs |

Optionally **first** register empty application:

```http
POST /api/v1/applications
Content-Type: application/json

{
  "appId": "lab-training",
  "displayName": "Lab Training",
  "schemaName": "app_lab_training"
}
```

Then import with same `packageId` applies bundle to existing Application.

3. Open operator app: `?mode=operator&app=lab-training`
4. Accounts (created on server start):

| User | Password | Role |
|------|----------|------|
| `lab-user-a` | `lab-user-a` | operator |
| `lab-user-b` | `lab-user-b` | operator |

Devices created by bootstrap: `root.platform.devices.lab-userA-01`, `root.platform.devices.lab-userB-01` (model `virtual-lab-v1`, driver profile `lab`).

---

## Tree objects

| Exercise | Object / dashboard |
|----------|-------------------|
| — | Application `root.platform.applications.lab-training`, operator app `root.platform.operator-apps.lab-training` |
| 1 | Users + ACL on `lab-userB-01` |
| 2–4 | Alert rules + correlators under `root.platform.alert-rules.*`, `root.platform.correlators.*` |
| 5–18 | Dashboards `root.platform.dashboards.lab-*` |
| 11 | Report `root.platform.reports.lab-all-devices-table` |

---

## Exercises

### 1. Two users and userA access to userB device

**Goal:** multi-user collaboration with per-object ACL.

**Implementation:**
- Users `lab-user-a`, `lab-user-b` — `LabSecurityBootstrap`
- ACL on `root.platform.devices.lab-userB-01`: owner `lab-user-b`, editor `lab-user-a`
- ACL on `root.platform.devices.lab-userA-01`: owner `lab-user-a`, editor `lab-user-b`

**Verify:** log in as `lab-user-a`, open Variable editor (`lab-variable-editor`) — editing variables on device B is allowed.

---

### 2. Alarm: Event1 → ON, Event2 (Int>20) → OFF

**Goal:** latch via correlator + variable `alarmLatched`.

**Objects:**
- Correlator `root.platform.correlators.lab-event1-latch-on` → `SET_VARIABLE alarmLatched=true` on event1
- Correlator `root.platform.correlators.lab-event2-unlatch` → `SET_VARIABLE alarmLatched=false` with `payloadFilterExpr: payload["int"] > 20`

**Verify:** dashboard `lab-event-gen` or `lab-fan-composite` (Alarm latched indicator). Invoke `fireEvent1`, then `fireEvent2` with int=25.

---

### 3. Alarm 10 s after sum(Int+Float) ∈ [50, 100]

**Goal:** alert rule with delay and sustain.

**Object:** `root.platform.alert-rules.lab-sum-range-sustained-alert`
- `watchVariable`: `sumIntFloat`
- `conditionExpr`: `self.sumIntFloat["value"] >= 50 && self.sumIntFloat["value"] <= 100`
- `delaySeconds`: 10, `sustainWhileTrue`: true

**Verify:** in Variable editor set `intValue`+`floatValue` so sum is 75, wait 10 s — event `labSumRangeAlarm`.

---

### 4. Alarm: sum(table.Int) > 100 + corrective report

**Goal:** alert + correlator `OPEN_OPERATOR_REPORT`.

**Objects:**
- Alert `root.platform.alert-rules.lab-table-sum-threshold` on `tableIntSum`
- Correlator `root.platform.correlators.lab-open-corrective-report` → report `root.platform.reports.lab-table-corrective`

**Verify:** add rows to `table` via Form grid until `tableIntSum` > 100.

---

### 5. Event2 filter: Int>10 OR String contains "abc"

**Goal:** `payloadFilterExpr` in `event-feed` widget.

**Dashboard:** `root.platform.dashboards.lab-event-gen` — lower feed "Event 2 log (filtered)" with expression `int > 10 || string contains abc`.

**Verify:** events with int≤10 and without "abc" in string do not appear in second feed.

---

### 6. Form Grid Layout

**Dashboard:** `root.platform.dashboards.lab-form-grid`

**Documentation:** layout example in [dashboards](dashboards.md).

---

### 7. Calculator function → calculate()

**Model:** function `calculate(inputA, inputB)` on `virtual-lab-v1`.

**Verify:** invoke via API or Calculator dashboard (exercise 9).

---

### 8. Relative model: sum Sine + Sawtooth

**Model:** `virtual-lab-waves-sum-v1` (RELATIVE) with binding `sumWaves = sineWave + sawtoothWave`.

**Verify:** dashboard `lab-virtual-overview` — Sum waves widget.

---

### 9. Calculator grid widget

**Dashboard:** `root.platform.dashboards.lab-calculator` — `spreadsheet` widget in **configured** mode (template with labels and formulas in layout).

**Try (operator):**

1. Open **Calculator** dashboard in lab operator app.
2. In cells **A2** and **B2** change numbers (Tab — move between fields).
3. Confirm **C2** (Sum) and **D2** (A×110%) recalculated.
4. Refresh page (**F5**) — A2/B2 values should persist (saved to `sheetValues` on `lab-userA-01`).

**Free mode (new widgets default):** any cell — value or `=formula`; formula bar, F2, Ctrl+Z, CSV export. Details: [spreadsheet-widget](spreadsheet-widget.md).

**Verify:** change A2 → Sum and A×110% recalc; F5 → values in `sheetValues`.

---

### 10. Query all variables + edit

**Dashboard:** `root.platform.dashboards.lab-variable-editor` — `variable-editor` widgets for both lab devices.

---

### 11. Relative report: table of all virtual devices

**Report:** `root.platform.reports.lab-all-devices-table` (type `tree-variables`, pattern `root.platform.devices.lab-*`, variable `table`).

**Verify:** `POST /api/v1/reports/by-path/run?path=root.platform.reports.lab-all-devices-table`

---

### 12. Per-second chart Sine + Sawtooth

**Dashboard:** `root.platform.dashboards.lab-charts` — `refreshIntervalMs: 1000`, charts with `historyRange: live`.

**Driver:** profile `lab`, `pollIntervalMs` in runtime config.

---

### 13. Pie Chart from table

**Dashboard:** `root.platform.dashboards.lab-pie` — `pie-chart` widget, source `table`, fields `string` / `int`.

---

### 14. Event generation form + dual log

**Dashboard:** `root.platform.dashboards.lab-event-gen` — two `fireEvent1`/`fireEvent2` forms, two `event-feed` with event name filter.

---

### 15. Button opens another widget (modal)

**Dashboard:** `root.platform.dashboards.lab-modal` — `dashboard-link` with `openMode: modal` → `lab-charts`.

---

### 16. Sine history 5 min: table + average

**Dashboard:** `root.platform.dashboards.lab-history` — `history-table` widget on `sineWave`.

---

### 17. SVG button + fan (composite)

**Dashboard:** `root.platform.dashboards.lab-fan-composite` — `composite-widget` with `svg-widget` (`/lab-assets/button.svg` toggle `fanRunning`, `/lab-assets/fan.svg`).

---

### 18. Dashboard virtual devices

**Dashboard:** `root.platform.dashboards.lab-virtual-overview` — chart/value, event-feed, report table.

---

## Virtual Lab Device

| Variable | Type | Description |
|----------|------|-------------|
| `sineWave` | DOUBLE | `amplitude * sin(2π t / periodSec)` |
| `sawtoothWave` | DOUBLE | sawtooth signal |
| `intValue`, `floatValue` | INTEGER / DOUBLE | config / manual write |
| `table` | RECORD_LIST | rows `{int, string}` |
| `sumWaves`, `sumIntFloat`, `tableIntSum` | bindings | computed |
| `alarmLatched`, `fanRunning` | BOOLEAN | for automation / HMI |

**Events:** `event1`, `event2` (payload `{int, string}`).

**Functions:** `calculate`, `fireEvent1`, `fireEvent2`, `appendTableRow`.

---

## Tests

| Test | Purpose |
|------|---------|
| `VirtualLabProfileTest` | driver profile `lab` |
| `LabAutomationTest` | delay alert, correlator payload, SET_VARIABLE |
| `TreeVariablesReportTest` | report type `tree-variables` |
| `LabTrainingBundleTest` | import bundle + key paths |
| `LabSecurityBootstrapTest` | users + ACL |
| `payloadFilter.test.ts` | event-feed OR/AND filter |

Run: `./gradlew test` (server), `npm test` in `apps/web-console`.

---

## Related documents

- [dashboards](dashboards.md) — widgets and Grid Layout
- [automation](automation.md) — alert rules and correlators
- [reports](reports.md) — tree-first reports
- [security](security.md) — ACL API
