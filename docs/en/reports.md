> **Language:** Canonical English. Russian edition: [ru/reports.md](../ru/reports.md).

# Application reports (REQ-PF-12)

Generic SQL report layer. **Tree-first (Phase 12–14):** definition on `REPORT` object in `root.platform.reports.*` (model `report-v1`); SQL schema — via **`dataSourcePath`** → `root.platform.data-sources.*`.

Legacy API `/api/v1/applications/{appId}/reports/*` is retained and delegates to the tree. Bundle import: `POST /api/v1/platform/packages/import`.

## Object tree

| Node | Type | Model |
|------|------|-------|
| `root.platform.reports` | `REPORTS` | — |
| `root.platform.reports.{reportId}` | `REPORT` | `report-v1` |

Object variables:

| Variable | Description |
|----------|-------------|
| `title` | Title |
| `dataSourcePath` | Path to `DATA_SOURCE` (`root.platform.data-sources.*`) — schema for SQL |
| `query` | SELECT / WITH |
| `parameters` | JSON array of `?` parameter names |
| `columns` | JSON array `{field, label}` |
| `defaultParameters` | JSON object of default values |
| `maxRows` | Row limit (default 1000) |
| `refreshIntervalMs` | Auto-refresh in UI (default 30000) |
| `templateFormat` | YARG template format: `xlsx`, `docx`, `html` (empty = no template) |

Binary template stored in `report_templates` table (not in object_variables).

Web Console: **Report Builder** — structured editor (parameters/columns, no manual JSON), SQL preview, CSV, **YARG Template** tab, PDF/XLSX/HTML export. Toggle **sql** | **tree-variables**.

### tree-variables (model `tree-variables-report-v1`)

| Variable | Description |
|----------|-------------|
| `reportType` | `tree-variables` |
| `devicePathPattern` | Device path prefix or glob (`*`, `?`) |
| `variableName` | Variable name on each object |
| `columns` | JSON array `{field, label}` (default path, value) |

Save: `PUT /api/v1/reports/by-path/tree-variables-definition?path=...`

## Export matrix (UI)

| Surface | CSV | PDF/XLSX/HTML |
|---------|-----|----------------|
| Report Builder | yes | yes (if YARG template) |
| Dashboard widget `report` | configurable (`showCsv`, …) | configurable + template |
| Operator manifest `screen.report` | yes | yes (if template) |
| Operator app (ReportBuilder) | yes | yes (if template) |

## Dashboard widget `type: "report"`

| Field | Description |
|-------|-------------|
| `reportPath` | REPORT path |
| `parametersJson` | Static run parameters |
| `contextParamsJson` | `{reportParam: sessionParamKey}` |
| `showCsv` / `showPdf` / `showXlsx` / `showHtml` | Export buttons |
| `showTruncatedWarning` | Banner when rows truncated |

## Path-based API (primary)

```http
GET  /api/v1/reports/by-path?path=root.platform.reports.ready-items
PUT  /api/v1/reports/by-path/definition?path=...
PUT  /api/v1/reports/by-path/tree-variables-definition?path=...
POST /api/v1/reports/by-path/run?path=...
GET  /api/v1/reports/by-path/export?path=...&format=csv|pdf|xlsx|html
POST /api/v1/reports/by-path/template?path=...&format=xlsx   (multipart file)
GET  /api/v1/reports/by-path/template?path=...
DELETE /api/v1/reports/by-path/template?path=...
```

### Run (by path)

```http
POST /api/v1/reports/by-path/run?path=root.platform.reports.ready-items
Content-Type: application/json

{ "parameters": { "status": "ready" } }
```

## YARG templates (Phase 13)

Server export via [YARG](https://github.com/cuba-platform/yarg) (Apache-2.0):

1. Create Excel/Word template with band **`Band1`** and fields `${Band1.COLUMN}` (SQL column names in **uppercase**, e.g. `${Band1.ITEM_CODE}`). Example — [YARG smoke sample](https://github.com/cuba-platform/yarg/tree/master/core/modules/core/test/sample).
2. Upload file in **YARG Template** tab in Report Builder (`POST .../template`).
3. Export: `GET .../export?format=pdf|xlsx|html` — data from same SQL run.

Without template only `format=csv` is available.

## Deploy

Via bundle (`reports[]` creates object in `root.platform.reports.{reportId}`):

```json
{
  "reports": [
    {
      "reportId": "ready-items",
      "title": "Ready items",
      "query": "SELECT item_code, status FROM demo_item WHERE status = ?",
      "parameters": ["status"],
      "columns": [
        { "field": "item_code", "label": "Code" },
        { "field": "status", "label": "Status" }
      ],
      "maxRows": 500
    }
  ]
}
```

Legacy deploy:

```http
POST /api/v1/applications/{appId}/reports/deploy
```

## Legacy app-scoped API

```http
GET  /api/v1/applications/{appId}/reports
POST /api/v1/applications/{appId}/reports/{reportId}/run
GET  /api/v1/applications/{appId}/reports/{reportId}/export?format=csv|pdf|xlsx|html
```

## Operator UI

- **operatorUi `reports[]`:** navigation by report path (like `dashboards[]`).
- **Dashboard widget `type: "report"`** — table by `reportPath` with `parametersJson` / session mapping and export toolbar.
- **Legacy manifest** `screen.report` — CSV + PDF/XLSX/HTML with YARG template.

## Permissions

| Endpoint | Role |
|----------|------|
| `GET .../reports/by-path`, export | `operator`, `admin` |
| `PUT .../definition`, template upload/delete | `admin` |
| `POST .../run` | `operator`, `admin` |
| `POST .../applications/.../deploy` (reports.md) | `admin` |

## Limitations

- Read-only SQL only (no `INSERT`/`UPDATE`/`DELETE`/DDL).
- Query runs in data source object schema (`dataSourcePath`).
- PDF/XLSX/HTML require uploaded YARG template.

## Example

[examples/demo-app/](../examples/demo-app/) — `POST /api/v1/platform/packages/import?packageId=demo` or legacy deploy on `demo`, reports in `root.platform.reports.*`.

## Related documents

- [APPLICATIONS.md](applications.md) — bundle deploy
- [DASHBOARDS.md](dashboards.md) — similar tree-first model
- [ROADMAP.md](roadmap.md) — Phase 12–13
