# ADR-0023: Haystack query runtime (filter subset)

## Status

Accepted (2026-07-02)

## Context

[ADR-0021](0021-haystack-semantic-overlay.md) defines Haystack as an optional overlay on the ISPF object tree. Wave G (BL-56…62 Done) delivered tag export, `GET /platform/haystack/search` (repeated `tags` params with AND semantics), and dashboard bind-by-tags.

Integrators and BMS engineers expect **Haystack filter strings** (`point and temp`, `equip and ahu`) rather than checkbox tag lists. SkySpark/FIN tools use similar filter syntax over tag sets.

Requirements (BL-101…103):

- Subset filter parser over in-memory tag index built from tree walk (same source as export/search).
- REST query endpoint with pagination and ACL on paths.
- Dashboard builder wizard: bind widgets from a filter string.

## Decision

### 1. Tree-first, overlay unchanged

- ISPF dot-paths remain authoritative for bindings, historian, and ACL.
- Query runtime evaluates **marker presence** on equip and point tag maps already produced by `HaystackExportService`.
- No replacement of dot-path navigation; no full Haxall/Zinc server.

### 2. v1 filter syntax (subset)

Supported:

| Form | Example | Semantics |
| ---- | ------- | --------- |
| Single marker | `temp` | Entity must have marker `temp` |
| Conjunction | `point and temp` | All markers present (AND) |
| Whitespace | `equip  and   ahu` | Normalized trim |

Markers are `[A-Za-z][A-Za-z0-9_-]*` tokens. The keyword `and` (case-insensitive) separates markers.

**Examples:**

```
point and temp          → temp points (sensor telemetry)
equip and ahu           → equipment tagged ahu
equip and point and temp
```

Out of scope for v1:

- `or`, `not`, parentheses
- Comparisons (`temp > 20`), refs (`@site.equip`), ids with special chars
- Server-side Zinc/HTTP Haystack protocol
- Cached global tag index (full tree scan per request; acceptable for demo/lab scale)

Implementation: `HaystackFilterParser` → `List<String>` required markers → reuse `HaystackExportService.tagsMatch`.

### 3. Query API

```
GET /api/v1/platform/haystack/query?filter=point+and+temp&rootPath=&entityKind=point&offset=0&limit=50
```

Response:

```json
{
  "formatVersion": 1,
  "filter": "point and temp",
  "rootPath": "root.platform",
  "entityKind": "point",
  "offset": 0,
  "limit": 50,
  "count": 1,
  "matches": [
    {
      "entityKind": "point",
      "path": "root.platform.devices.lab-userA-01",
      "objectPath": "root.platform.devices.lab-userA-01",
      "variableName": "sineWave",
      "tags": { "point": true, "temp": true, "sensor": true },
      "curVal": 0.42,
      "unit": "°C",
      "dis": "Sine wave"
    }
  ]
}
```

- **Pagination:** `offset` (default 0), `limit` (default 50, max 200).
- **ACL:** `HaystackQueryService` filters matches with `TenantScopeService` + `ObjectAccessService.canRead` (same as agent `search_by_haystack_tags`).
- **Legacy search:** `GET /haystack/search?tags=` retained for backward compatibility.

### 4. Dashboard auto-bind (BL-103)

`HaystackBindDialog` gains a **Filter query** mode: free-text filter input calling `/haystack/query`, reusing existing value-widget append logic.

Tag checkbox mode remains for quick AND multiselect.

### 5. Agent tools (optional follow-up)

Agent may add `query_haystack` delegating to `HaystackQueryService`; not required for BL-102 acceptance.

## Consequences

**Positive**

- Familiar Haystack filter strings for BMS integrators.
- Reuses export tag index; minimal new surface area.
- ACL enforced on query endpoint (stricter than legacy search).

**Negative**

- Full tree scan per query; large deployments may need cached index (future BL).
- v1 AND-only; complex filters deferred.

## References

- [ADR-0021](0021-haystack-semantic-overlay.md)
- BL-101, BL-102, BL-103 — [ROADMAP.md](../ROADMAP.md#часть-e--полный-реестр-bl-01139)
- `HaystackExportService`, `HaystackFilterParser`, `HaystackQueryService`
