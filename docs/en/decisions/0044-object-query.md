# ADR-0044: Object Query (OQ)

## Status

**Accepted** (2026-07-12)

## Context

ISPF needed **cross-object tabular reads** over the platform object tree (device inventories, SNMP ifTable rows, joined parent/child projections) without SQL and without a separate `ObjectType.QUERY` catalog. Legacy tree-scan queries stored spec fragments in QUERY object variables and exposed `/api/v1/queries`.

## Decision

### 1. Storage: function-only

Object queries are stored as **`FunctionDescriptor` with `sourceType=object-query`** and OQ JSON in `sourceBody`. Convention folder `root.platform.queries` remains a **FOLDER** of objects carrying `run` (or named) object-query functions.

Invoke via existing function API:

```http
POST /api/v1/objects/by-path/functions/invoke?path={objectPath}&name=run
```

### 2. OQ spec (v1)

```json
{
  "from": {
    "alias": "row",
    "sourcePathPattern": "root.platform.devices.*",
    "objectTypes": ["DEVICE"],
    "filter": "displayName != \"\"",
    "expand": { "variable": "ifTable", "rowKey": "ifIndex", "filter": "row.ifOperStatus == 2" }
  },
  "joins": [
    { "alias": "parent", "type": "left", "on": { "kind": "parent" } }
  ],
  "fields": [
    { "name": "path", "source": "path", "alias": "row" },
    { "name": "temp", "ref": "{row}/temperature/value" }
  ],
  "orderBy": [{ "field": "path", "dir": "asc" }],
  "limit": 1000,
  "offset": 0,
  "having": "temp > 0"
}
```

**Join kinds (v1):** `parent`, `ancestor`, `ref`, `pathPrefix`, `eq`, `sameObject`, `lookup`, `pathSubstring`.

**Record expand:** `from.expand` turns a multi-row variable (`DataRecord`) into one result row per record row; `{row}/field` refs address expand row fields.

### 3. Runtime

- [`ObjectQueryService`](../../../packages/ispf-server/src/main/java/com/ispf/server/query/ObjectQueryService.java) — scan, join, project, sort, paginate, scalar aggregates.
- [`ObjectQueryFunctionHandler`](../../../packages/ispf-server/src/main/java/com/ispf/server/function/ObjectQueryFunctionHandler.java) — `@Order(-2)`, returns `rows` (JSON string) + `rowCount`.
- [`ObjectQueryCatalog`](../../../packages/ispf-server/src/main/java/com/ispf/server/query/ObjectQueryCatalog.java) — folder bootstrap for `root.platform.queries`.

### 4. Platform bindings

| Binding | Purpose |
|---------|---------|
| `queryScalar(spec, aggregate[, field])` | Reactive KPI (`count`, `sum`, `avg`, `min`, `max`, `first`) |
| `queryRows(spec)` / `executeQuery(spec)` | Full table as JSON string |
| `countScan(pattern)` / `sumScan(pattern, field)` | Tree-scan sugar |
| `write(ref, value)` | Writeback to remote variable field |

`spec` may be inline JSON or `@/variable/value` ref.

### 5. Script steps

`FunctionScriptEngine` steps: `queryRows` / `scan_objects`, `for_each_row`, `apply_query_patch` (reuse `ObjectQueryService` / `PlatformRefExecutor.write`).

### 6. Legacy removal

`ObjectType.QUERY`, `query-v1`, `/api/v1/queries/*`, and tree-scan variable storage are **removed**. Query definitions live only as `CUSTOM` children under `root.platform.queries` with a `run` function (`sourceType=object-query`).

### 7. Post-v1 extensions (implemented)

- Invoke **patch writeback** via `patch` / `patches` on `run` input
- `groupBy` + reducer aggregates in-engine
- Historian column: `{"ref": "{row}/temperature", "historian": {"fn": "avg", "window": "15m"}}`
- Variables introspection: `{"source": "variables", "alias": "row"}` → sorted variable name list
- Join kinds `lookup` and `pathSubstring` in [`ObjectQueryJoinResolver`](../../../packages/ispf-server/src/main/java/com/ispf/server/query/oq/ObjectQueryJoinResolver.java)

Remaining: none for v1 UI — spec editor in Web Console (`ObjectQuerySpecField`, expression builder integration).

## Consequences

- Single invoke path for tables and KPIs; bindings stay reactive-scalar only.
- Agents and UI store specs on `run.sourceBody` and reference via `@/…/value`.
- Existing deployments must migrate QUERY nodes to CUSTOM + `run` before upgrade (Flyway `V80__retire_object_query_type.sql` retypes DB rows).
