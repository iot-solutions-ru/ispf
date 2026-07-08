> **Язык:** русская версия (вычитка). Канонический английский: [en/decisions/0023-haystack-query-runtime.md](../../en/decisions/0023-haystack-query-runtime.md).

# ADR-0023: Haystack query runtime (filter subset)

## Статус

Принято (2 июля 2026 г.)

## Контекст

[ADR-0021](0021-haystack-semantic-overlay.md) определяет Haystack как optional overlay на ISPF object tree. Wave G (BL-56…62 Done) доставил tag export, `GET /platform/haystack/search` (repeated `tags` params с AND semantics) и dashboard bind-by-tags.

Integrators и BMS engineers ожидают **Haystack filter strings** (`point and temp`, `equip and ahu`), а не checkbox tag lists. SkySpark/FIN tools используют похожий filter syntax над tag sets.

Требования (BL-101…103):

- Subset filter parser над in-memory tag index, построенным из tree walk (тот же source, что export/search).
- REST query endpoint с pagination и ACL на paths.
- Dashboard builder wizard: bind widgets из filter string.

## Решение

### 1. Tree-first, overlay unchanged

- ISPF dot-paths остаются authoritative для bindings, historian и ACL.
- Query runtime оценивает **marker presence** на equip и point tag maps, уже produced `HaystackExportService`.
- Без replacement dot-path navigation; без full Haxall/Zinc server.

### 2. v1 filter syntax (subset)

Supported:

| Form | Example | Semantics |
| ---- | ------- | --------- |
| Single marker | `temp` | Entity must have marker `temp` |
| Conjunction | `point and temp` | All markers present (AND) |
| Whitespace | `equip  and   ahu` | Normalized trim |

Markers — tokens `[A-Za-z][A-Za-z0-9_-]*`. Keyword `and` (case-insensitive) разделяет markers.

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
- Cached global tag index (full tree scan per request; acceptable для demo/lab scale)

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
- **ACL:** `HaystackQueryService` фильтрует matches через `TenantScopeService` + `ObjectAccessService.canRead` (как agent `search_by_haystack_tags`).
- **Legacy search:** `GET /haystack/search?tags=` retained для backward compatibility.

### 4. Dashboard auto-bind (BL-103)

`HaystackBindDialog` получает режим **Filter query**: free-text filter input вызывает `/haystack/query`, reusing existing value-widget append logic.

Tag checkbox mode остаётся для quick AND multiselect.

### 5. Agent tools (optional follow-up)

Agent может добавить `query_haystack`, delegating to `HaystackQueryService`; не required для BL-102 acceptance.

## Последствия

**Positive**

- Familiar Haystack filter strings для BMS integrators.
- Reuses export tag index; minimal new surface area.
- ACL enforced на query endpoint (строже, чем legacy search).

**Negative**

- Full tree scan per query; large deployments могут потребовать cached index (future BL).
- v1 AND-only; complex filters deferred.

## Связанные материалы

- [ADR-0021](0021-haystack-semantic-overlay.md)
- BL-101, BL-102, BL-103 — [roadmap.md](../roadmap.md#часть-e--полный-реестр-bl-01139)
- `HaystackExportService`, `HaystackFilterParser`, `HaystackQueryService`
