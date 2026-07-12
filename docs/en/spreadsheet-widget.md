> **Language:** Canonical English. Russian edition: [ru/spreadsheet-widget.md](../ru/spreadsheet-widget.md).

# Spreadsheet widget

`spreadsheet` is an HMI grid with A1 cell addressing (`A1`, `B2`, `C10`) and client-side formulas. Use it for shift calculators, summaries, simple on-screen calculations, and process tables without a separate Excel file.

Widget catalog: [widgets](widgets.md). Dashboard basics, `selectionKey`, and layout: [dashboards](dashboards.md).

## Why you might see `#NAME?`

`#NAME?` means the formula engine did not recognize a function name. In ISPF this can be:

- a typo in the function name: `=SUMM(A1:A10)` instead of `=SUM(A1:A10)`;
- after correction, standard functions such as `SUM`, `AVERAGE`, `MIN`, `MAX`, `IF`, and `ROUND` are provided by the **built-in ISPF engine** (`ispfSheetEval`) together with `ISPREF`, `ISPSUM`, and `ISPHIST`.

## Modes

| Mode | When to use | Behavior |
|------|-------------|----------|
| `free` | Operator builds the sheet | Any unlocked cell is editable; formula bar, undo/redo, copy/paste, **XLSX import/export**, CSV |
| `configured` | Developer defines a template | Only `input` cells are editable; `label`, `formula`, `binding`, and `readonly` are set in `sheetConfigJson` |

Default `sheetMode` is `free`.

## UI

| Element | Description |
|---------|-------------|
| Formula bar | Selected cell address on the left; raw definition on the right: text, number, or formula starting with `=` |
| Grid | Shows computed values; formulas recalculate immediately |
| Toolbar | In `free`: undo/redo, copy/paste, insert/delete rows and columns, **XLSX import**, **XLSX export**, CSV export |
| Sheet tabs | Multiple sheets in one workbook; tab switching; cross-sheet formulas (`=Sales!A1`) |
| Binding cells | Live ISPF variable data; not editable by the operator |

Keyboard shortcuts in `free`:

| Keys | Action |
|------|--------|
| `F2` / double-click | Edit cell |
| `Enter` | Start editing; after entry, save and move down |
| `Esc` | Cancel edit |
| `Tab` / `Shift+Tab` | Next / previous cell |
| Arrow keys | Move selection |
| `Ctrl+Z` / `Ctrl+Y` | Undo / redo |
| `Ctrl+C` / `Ctrl+V` | Copy / paste |

## Formulas

A formula always starts with `=`.

Examples:

```text
=A1+B1
=A2*1.2
=SUM(A2:A10)
=AVERAGE(B2:B20)
=IF(C2>80,"ALARM","OK")
=ROUND(A2/3,2)
```

Ranges use Excel syntax: `A1:A10`, `A1:C5`, **`D:D`** (entire column to grid end), **`Sheet!D:D`** (column on another sheet). English function names are supported, plus **Russian Excel locale aliases** that map to the same functions (for example localized `SUM` and `IF` names). Argument separator is `,` or `;` (as in Russian-locale Excel).

### XLSX import and export

In `free` mode the toolbar provides **Import XLSX** and **Export XLSX** (ExcelJS library, MIT).

| Stage | Behavior |
|-------|----------|
| Import | All workbook sheets become tabs; cells, formulas (including `Sheet!A1`), **merges**, **basic styles**; grid capped at 500×52 |
| Export | Full workbook to `.xlsx` with formulas, styles, and merges |
| Session | Values, formulas, and workbook metadata (sheets, styles, merges) stored in session/variable |
| Warnings | Unsupported functions show `#NAME?`; after import a dismissible list of unresolved functions is shown (banner does not auto-hide) |

String concatenation with `&` is supported: `="A"&"B"`.

#### Supported functions

| Category | Functions |
|----------|-----------|
| Aggregates | `SUM`, `AVERAGE`, `MIN`, `MAX`, `COUNT`, `COUNTA`, `COUNTBLANK`, `PRODUCT`, `MEDIAN`, `STDEV`, `STDEV.S`, `SUBTOTAL` |
| Conditional | `IF`, `IFERROR`, `IFS`, `SUMIF`, `COUNTIF`, `AVERAGEIF`, `SUMIFS`, `COUNTIFS`, `AVERAGEIFS`, `MAXIFS`, `MINIFS`, `SUMPRODUCT` |
| Lookup | `VLOOKUP`, `HLOOKUP`, `INDEX`, `MATCH`, `XLOOKUP` |
| Math | `ABS`, `MOD`, `POWER`, `SQRT`, `INT`, `ROUND`, `ROUNDUP`, `ROUNDDOWN`, `CEILING`, `FLOOR`, `TRUNC`, `MROUND`, `LOG`, `LN`, `LOG10`, `EXP`, `PI`, `SIGN`, `RAND`, `RANDBETWEEN` |
| Logic | `AND`, `OR`, `NOT`, `TRUE`, `FALSE`, `SWITCH`, `CHOOSE`, `IFNA` |
| Text | `LEN`, `LEFT`, `RIGHT`, `MID`, `TRIM`, `UPPER`, `LOWER`, `PROPER`, `CHAR`, `CODE`, `REPT`, `CONCAT`, `CONCATENATE`, `TEXT`, `TEXTJOIN`, `FIND`, `SEARCH`, `SUBSTITUTE`, `REPLACE`, `VALUE`, `EXACT` |
| Date/time | `TODAY`, `NOW`, `YEAR`, `MONTH`, `DAY`, `DATE`, `DAYS`, `WEEKDAY`, `HOUR`, `MINUTE`, `SECOND`, `TIME`, `EDATE`, `EOMONTH`, `DATEDIF`, `NETWORKDAYS`, `WORKDAY`, `DATEVALUE`, `TIMEVALUE`, `WEEKNUM`, `YEARFRAC` |
| Statistics | `VAR`, `VAR.S`, `PERCENTILE`, `QUARTILE`, `LARGE`, `SMALL`, `RANK` |
| Finance | `NPV`, `IRR`, `PMT`, `FV`, `PV`, `NPER`, `RATE` |
| Trigonometry | `SIN`, `COS`, `TAN`, `ASIN`, `ACOS`, `ATAN`, `ATAN2`, `RADIANS`, `DEGREES` |
| Addressing | `ROW`, `COLUMN`, `ROWS`, `COLUMNS` |
| Checks | `ISBLANK`, `ISNUMBER`, `ISTEXT`, `ISERROR`, `ISNA`, `ISERR`, `NA`, `ISLOGICAL`, `ISODD`, `ISEVEN` |
| Text (clean) | `CLEAN` |
| ISPF | `ISPREF`, `ISPSUM`, `ISPHIST` |

Cross-sheet references: `=Sheet2!A1`, `=SUM(Sales!A1:A10)`.

IoT/reporting examples:

```text
=SUMIFS(C2:C100, A2:A100, "sensor-01", B2:B100, ">80")
=XLOOKUP(E2, A2:A50, C2:C50, "—")
=IFS(A2>90,"ALARM", A2>70,"WARN", TRUE,"OK")
=TEXTJOIN(", ", TRUE, A2, B2, C2)
=NETWORKDAYS(DATE(2026,1,1), TODAY())
=MEDIAN(H2:H100)
=NPV(0.08,C2:C24)
=PMT(0.05/12,36,-10000)
=FIND("@",A2)
=DATEVALUE("2026-06-28")
=(E3-B3)/B3
```

Russian locale aliases cover the same set (localized names for `SUM`, `IF`, `IFS`, `VLOOKUP`, `OFFSET`, `SUMIFS`, `FIND`, `SUBSTITUTE`, `DATEVALUE`, `NPV`, `PMT`, `IRR`, `ROW`, `COLUMN`, and others).

Limitations: `SUBTOTAL` does not respect hidden rows; wildcards in `XLOOKUP` are not applied; `YEARFRAC` uses simplified bases 0/1; `SEARCH` wildcards support basic `?` and `*`.

## ISPF functions

### `ISPREF(path, variableName, [field])`

Returns the current value of an object variable.

```text
=ISPREF("root.platform.devices.demo-sensor-01","temperature")
=ISPREF("root.platform.devices.demo-sensor-01","status","online")
```

Default `field` is `value`. Supported fields: `value`, `raw`, `int`, `string`, `online`, `unit`.

Variables referenced in `ISPREF` / `ISPHIST` / `ISPSUM` are **fetched automatically** from the formula (a binding cell is optional but useful for displaying live values in the grid). When variables change, dependent formulas recalculate:

| Channel | Behavior |
|---------|----------|
| WebSocket | `VARIABLE_UPDATED` / `EVENT_FIRED` on watched path → refetch bindings → `refreshComputed()` |
| Polling | Widget `refreshIntervalMs` when WS is unavailable |
| Binding cells | Update both displayed value and formula cache |

For `ISPHIST`, historian is queried on the same interval (or on invalidation events).

### `ISPSUM(tableVariable, column)`

Sums a numeric column of a RECORD_LIST variable on the dashboard object (`objectPath` of the widget).

```text
=ISPSUM("ordersTable","int")
=ISPSUM("ordersTable","value")
```

### `ISPHIST(path, variableName, [minutes])`

Returns the latest historian sample for a variable in a lookback window. If historian is unavailable, falls back to the current value from the binding cache.

```text
=ISPHIST("root.platform.devices.demo-sensor-01","temperature",5)
```

## Widget configuration

Widget fields:

| Field | Description |
|-------|-------------|
| `sheetMode` | `free` or `configured` |
| `sheetConfigJson` | Grid configuration JSON |
| `persistMode` | `session` or `variable` |
| `valuesVariable` | RECORD_LIST variable for stored values when `persistMode: variable` |
| `sessionKey` | Session key; default `sheet:{widgetId}` |
| `editable` | `false` — view only |
| `objectPath` / `selectionKey` | Object context for binding and variable persist |

Minimal `free`:

```json
{
  "type": "spreadsheet",
  "title": "Calculator",
  "sheetMode": "free",
  "sheetConfigJson": "{\"rows\":20,\"cols\":8,\"cells\":{}}"
}
```

`configured` with formulas and live binding:

```json
{
  "type": "spreadsheet",
  "title": "Summary",
  "sheetMode": "configured",
  "sheetConfigJson": "{\"rows\":6,\"cols\":4,\"frozenRows\":1,\"cells\":{\"A1\":{\"kind\":\"label\",\"text\":\"Parameter\"},\"B1\":{\"kind\":\"label\",\"text\":\"Value\"},\"A2\":{\"kind\":\"label\",\"text\":\"Temperature\"},\"B2\":{\"kind\":\"binding\",\"ref\":\"root.platform.devices.demo-sensor-01/temperature/value\"},\"C2\":{\"kind\":\"formula\",\"expr\":\"=B2*1.8+32\"}}}"
}
```

## `sheetConfigJson`

| Field | Type | Description |
|-------|------|-------------|
| `rows` | number | Row count |
| `cols` | number | Column count |
| `frozenRows` / `frozenCols` | number | Frozen rows / columns |
| `colLabels` | string[] | Column labels instead of A, B, C |
| `cells` | object | Per-cell settings by A1 address |
| `columnFilters` | array | Column filters in `configured` |
| `dataRegion` | object | Fill a block from RECORD_LIST |
| `conditionalStyles` | array | Conditional highlighting |

Cell kinds:

| `kind` | Fields | Purpose |
|--------|--------|---------|
| `label` | `text`, `style` | Static label |
| `input` | `default`, `validation`, `format`, `style` | Operator input |
| `formula` | `expr`, `format`, `style` | Formula |
| `readonly` | `default`, `format`, `style` | Non-editable value |
| `binding` | `ref` (preferred) or `objectPath` + `variableName` + `valueField`, `historyMinutes?` | Live snapshot or historian (see below) |

### Binding + historian (`historyMinutes`)

For `kind: "binding"` cells you can set `historyMinutes` (integer > 0): the widget queries historian for that window and shows the **latest** numeric sample (falls back to live snapshot if historian is empty). The same window is used in `ISPHIST(path, var, minutes)`.

```json
"B2": {
  "kind": "binding",
  "objectPath": "root.platform.devices.demo-sensor-01",
  "variableName": "temperature",
  "valueField": "value",
  "historyMinutes": 15
}
```

Limitation: latest sample in the window only (not average/min/max); maximum window is 10080 minutes (7 days).

Example `dataRegion`:

```json
{
  "rows": 20,
  "cols": 5,
  "cells": {},
  "dataRegion": {
    "variableName": "orders",
    "startRow": 1,
    "startCol": 0,
    "columnFields": ["name", "qty", "status"]
  }
}
```

`startRow` and `startCol` are 0-based.

## Persistence

| `persistMode` | Stored in | When to use |
|---------------|-----------|-------------|
| `session` | Dashboard session | Draft calculations for one operator |
| `variable` | Object RECORD_LIST variable (`valuesVariable`) | Survive F5 or share the sheet across operators |

Reference object + widget setup: [examples/spreadsheet-demo](readme.md).

In `free` mode both values and formulas are saved:

```json
[
  { "cell": "A2", "value": "10" },
  { "cell": "C2", "value": "=SUM(A2:A10)" }
]
```

## Practical tips

- For an operator calculator use `free` and `persistMode: variable`.
- For a regulated form prefer `configured`: labels as `label`, editable fields as `input`, calculations as `formula`.
- For live data use `binding` cells; reference them with normal cell refs (`=B2*1.2`) or ISPF functions.
- If you see `#NAME?`, check the function name and refresh the UI build.
